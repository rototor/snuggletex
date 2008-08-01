/* $Id$
 *
 * Copyright 2008 University of Edinburgh.
 * All Rights Reserved
 */
package uk.ac.ed.ph.snuggletex.conversion;

import uk.ac.ed.ph.aardvark.commons.util.ArrayListStack;
import uk.ac.ed.ph.snuggletex.ErrorCode;
import uk.ac.ed.ph.snuggletex.InputError;
import uk.ac.ed.ph.snuggletex.SnuggleInput;
import uk.ac.ed.ph.snuggletex.SnuggleLogicException;
import uk.ac.ed.ph.snuggletex.conversion.WorkingDocument.SourceContext;
import uk.ac.ed.ph.snuggletex.definitions.BuiltinCommand;
import uk.ac.ed.ph.snuggletex.definitions.BuiltinEnvironment;
import uk.ac.ed.ph.snuggletex.definitions.Command;
import uk.ac.ed.ph.snuggletex.definitions.CommandOrEnvironment;
import uk.ac.ed.ph.snuggletex.definitions.GlobalBuiltins;
import uk.ac.ed.ph.snuggletex.definitions.Globals;
import uk.ac.ed.ph.snuggletex.definitions.LaTeXMode;
import uk.ac.ed.ph.snuggletex.definitions.TextFlowContext;
import uk.ac.ed.ph.snuggletex.definitions.UserDefinedCommand;
import uk.ac.ed.ph.snuggletex.definitions.UserDefinedEnvironment;
import uk.ac.ed.ph.snuggletex.semantics.MathIdentifierInterpretation;
import uk.ac.ed.ph.snuggletex.semantics.MathInterpretation;
import uk.ac.ed.ph.snuggletex.semantics.MathNumberInterpretation;
import uk.ac.ed.ph.snuggletex.tokens.ArgumentContainerToken;
import uk.ac.ed.ph.snuggletex.tokens.BraceContainerToken;
import uk.ac.ed.ph.snuggletex.tokens.CommandToken;
import uk.ac.ed.ph.snuggletex.tokens.EnvironmentToken;
import uk.ac.ed.ph.snuggletex.tokens.ErrorToken;
import uk.ac.ed.ph.snuggletex.tokens.FlowToken;
import uk.ac.ed.ph.snuggletex.tokens.SimpleToken;
import uk.ac.ed.ph.snuggletex.tokens.Token;
import uk.ac.ed.ph.snuggletex.tokens.TokenType;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This class reads in SnuggleTeX input and builds a tree of literal parsed {@link Token}s.
 * <p>
 * This is probably the most complex class in SnuggleTeX!
 * 
 * <h2>Developer Notes</h2>
 * 
 * TODO: Add run-time configuration options
 * TODO: We are keeping command and env names separate - LaTeX doesn't do this!
 * TODO: \cal and friends in normal mode
 * TODO: \mathcal (Note: this might not work very well due to font issues...)
 * TODO: matrices in math mode
 * TODO: \makebox and \framebox
 * 
 * @author  David McKain
 * @version $Revision$
 */
public final class LaTeXTokeniser {
    
    /** 
     * Set of reserved commands. These cannot be redefined and are not all listed in
     * {@link BuiltinCommand}.
     */
    public static final Set<String> reservedCommands = new HashSet<String>(Arrays.asList(new String[] {
        "begin",
        "end",
        "(",
        ")",
        "[",
        "]",
        "newcommand",
        "renewcommand",
        "newenvironment",
        "renewenvironment"
    }));
    
    /** 
     * Name of internal command that gets temporarily appended after the begin clauses of a
     * user-defined environment has been substituted in order to perform house-keeping duties.
     * No trace of this
     * command will exist once tokenisation has finished.
     * <p>
     * I've chosen a non-ASCII name for this command so as to make it impossible to be used in
     * "real" inputs.
     * 
     * See {@link #handleUserDefinedEnvironmentControl()}
     */
    private static final String UDE_POST_BEGIN = "\u00a3";
    
    /**
     * Provides access to the current {@link SessionContext}.
     */
    private final SessionContext sessionContext;

    //-----------------------------------------
    // Tokenisation state
    
    /** 
     * Current "working document", taking into account command/environment substitutions. This
     * starts off identical to the content of the {@link SnuggleInput} being read but will change
     * during tokenisation.
     */
    private WorkingDocument workingDocument;
    
    /** 
     * Current position within {@link #workingDocument}. This starts at 0 and generally increases
     * monotonically until it hits the length of the {@link #workingDocument}, though we may
     * sometimes move backwards when performing substitutions.
     */
    private int position;
    
    /**
     * Index of the start of the current token being parsed. This is set in {@link #readNextToken()}.
     */
    private int startTokenIndex;

    /** Current parsing "mode" */
    private ModeState currentModeState;
    
    /** Stack of parsing modes, with the last entry being equal to {@link #currentModeState} */
    private final ArrayListStack<ModeState> modeStack;
    
    /** Stack of open environments */
    private final ArrayListStack<String> openEnvironmentStack;
    
    /** 
     * Keeps track of which user-defined environments are currently in the process of opening.
     * We do this to prevent recursive environments from exhausting the stack.
     */
    private final Set<String> userEnvironmentsOpeningSet;
    
    /**
     * Enumeration of the various "tokenisation modes" we will be running in
     */
    public static enum TokenisationMode {
        TOP_LEVEL,
        BRACE,
        MATH,
        COMMAND_ARGUMENT,
        BUILTIN_ENVIRONMENT_CONTENT,
        USER_DEFINED_ENVIRONMENT_BEGIN,
        ;
    }
    
    /**
     * Trivial "struc"t encapsulating information about the state of a tokenisation mode.
     */
    public static class ModeState {
        
        /** Mode we're tokenising in */
        public final TokenisationMode tokenisationMode;
        
        /** Current LaTeX mode we are parsing in */
        public LaTeXMode latexMode;
        
        /** Position within {@link WorkingDocument} where this mode started */
        public final int startPosition;
        
        /** Terminator we are searching for */
        public final String terminator;
        
        /** Tokens accumulated in this mode */
        public final List<FlowToken> tokens;
        
        /** 
         * If terminator is not null, then this indicates whether the terminator was found when
         * parsing in this mode ended.
         */
        public boolean foundTerminator;
        
        public ModeState(final TokenisationMode tokenisationMode, final LaTeXMode latexMode,
                final int startPosition, final String terminator) {
            this.tokenisationMode = tokenisationMode;
            this.latexMode = latexMode;
            this.startPosition = startPosition;
            this.terminator = terminator;
            this.tokens = new ArrayList<FlowToken>();
            this.foundTerminator = false;
        }
        
        /**
         * Gets the end index of the Slice corresponding to the last Token recorded, returning
         * the start position if nothing has been recorded. This is useful for getting at the
         * "useful" content of a parse as it won't include the terminator.
         */
        public int computeLastTokenEndIndex() {
            if (tokens.isEmpty()) {
                return startPosition;
            }
            return tokens.get(tokens.size()-1).getSlice().endIndex;
        }
    }
    
    //-----------------------------------------
    
    public LaTeXTokeniser(final SessionContext sessionContext) {
        this.sessionContext = sessionContext;
        this.modeStack = new ArrayListStack<ModeState>();
        this.openEnvironmentStack = new ArrayListStack<String>();
        this.userEnvironmentsOpeningSet = new HashSet<String>();
    }
    
    /** Resets the parsing state of this tokeniser */
    private void reset() {
        position = 0;
        startTokenIndex = -1;
        modeStack.clear();
        currentModeState = null;
        openEnvironmentStack.clear();
        userEnvironmentsOpeningSet.clear();
    }
    
    /**
     * Tokenises the input specified by the given {@link SnuggleInputReader}, returning an
     * {@link ArgumentContainerToken} containing the roots of the parsed token tree.
     */
    public ArgumentContainerToken tokenise(final SnuggleInputReader reader)
            throws SnuggleParseException, IOException {
        /* Create WorkingDocument */
        this.workingDocument = reader.createWorkingDocument();
        
        /* Reset state and parse document in "top level" mode */
        reset();
        ModeState topLevelResult = tokeniseInNewState(TokenisationMode.TOP_LEVEL, null, LaTeXMode.PARAGRAPH);
        
        /* Check that all environments have been closed, recording a client error for each that is not */
        while (!openEnvironmentStack.isEmpty()) {
            topLevelResult.tokens.add(createError(ErrorCode.TTEE04, position, position,
                    openEnvironmentStack.pop()));
        }

        /* That's it! Simply return the tokens that have been accrued */
        return new ArgumentContainerToken(workingDocument.freezeSlice(0, workingDocument.length()),
                LaTeXMode.PARAGRAPH, topLevelResult.tokens);
    }
    
    /**
     * Tokenises the {@link #workingDocument} from the current {@link #position} in the given
     * {@link LaTeXMode} using the newly provided {@link TokenisationMode} and terminator.
     * <p>
     * When this returns, {@link #position} will either be at the end of input or after the
     * provided terminator.
     * 
     * @param tokenisationMode
     * @param terminator
     * @throws SnuggleParseException
     */
    private ModeState tokeniseInNewState(final TokenisationMode tokenisationMode,
            final String terminator, final LaTeXMode latexMode) throws SnuggleParseException {
        /* Create new parsing state */
        currentModeState = new ModeState(tokenisationMode, latexMode, position, terminator);
        modeStack.push(currentModeState);
        
        /* Parse in current state until exhaustion */
        FlowToken token;
        while ((token = readNextToken())!=null) {
            currentModeState.tokens.add(token);
        }
        
        /* Check that we got the required terminator */
        if (terminator!=null && !currentModeState.foundTerminator) {
            /* Error: Input ended before required terminator */
            currentModeState.tokens.add(createError(ErrorCode.TTEG00, position, position, terminator));
        }
        
        /* Revert to previous parsing state and return results of this parse */
        ModeState result = currentModeState;
        modeStack.pop(); /* Removes current entry from stack */
        currentModeState = modeStack.isEmpty() ? null : modeStack.peek(); /* Set current to previous state */
        return result;
    }
    
    /**
     * Reads the next token before the current terminator, returning null on end of input or
     * if the terminator is discovered.
     * <p>
     * The value of {@link #position} is advanced to just after the found token, or if no
     * token is found then it will be the end of input or after the terminator, whichever
     * is appropriate.
     * <p>
     * The caller should be prepared to handle the case where input ended before a required
     * terminator was discovered. In this case, a null will have been returned and
     * {@link ModeState#foundTerminator} will be false.
     * 
     * @throws SnuggleParseException
     * 
     * @return next token read in, or null if the input ended or we hit the terminator for the
     *   current {@link ModeState}. (You can test whether we hit a terminator by checking
     *   {@link ModeState#foundTerminator}.)
     */
    private FlowToken readNextToken() throws SnuggleParseException {
//        System.out.println("rNT: position=" + position
//                + ", length=" + workingDocument.length()
//                + ", tokMode=" + currentModeState.tokenisationMode
//                + ", latexMode=" + currentModeState.latexMode
//                + ", terminator=" + currentModeState.terminator
//                + ", envsOpen=" + openEnvironmentStack
//                + ", remainder='" + workingDocument.extract(position, Math.min(position+20, workingDocument.length())) + "'");
        
        /* In MATH Mode, we skip over any leading whitespace */
        if (currentModeState.latexMode==LaTeXMode.MATH) {
            skipOverWhitespace();
        }
        
        /* See if we are at our required terminator or at the end of the document */
        if (currentModeState.terminator!=null) {
            if (position>workingDocument.length() - currentModeState.terminator.length()) {
                /* Terminator wasn't found, so wind to end of document and return null */
                position = workingDocument.length();
                currentModeState.foundTerminator = false;
                return null;
            }
            if (workingDocument.matchesAt(position, currentModeState.terminator)) {
                /* Terminator found, so mark success and return null */
                position += currentModeState.terminator.length();
                currentModeState.foundTerminator = true;
                return null;
            }
        }
        else {
            if (position==workingDocument.length()) {
                /* Natural end of document */
                return null;
            }
        }
        
        /* Record position of the start of this token so that 'position' can be messed
         * about incrementally in the parsing methods below.
         */
        startTokenIndex = position;
        
        /* Now branch off according to the LaTeX mode we've currently in */
        FlowToken result;
        switch (currentModeState.latexMode) {
            case PARAGRAPH:
            case LR:
                result = readNextTokenTextMode();
                break;
                
            case MATH:
                result = readNextTokenMathMode();
                break;
                
            default:
                throw new SnuggleLogicException("Unhandled switch case " + currentModeState.latexMode);
        }
        
        /* Advance current position past this token, if we actually got something */
        if (result!=null) {
            position = result.getSlice().endIndex;
        }
        
        /* Return resulting token */
        return result;
    }
    
    /**
     * Makes a substitution within the working document, replacing the position of the
     * {@link WorkingDocument} from startIndex to endIndex with the given replacement
     * {@link CharSequence}.
     * <p>
     * The value of {@link #position} is updated to point to the start of the replacement
     * afterwards.
     * <p>
     * The given {@link SourceContext} provides optional contextual information about where
     * the substitution came from, if required.
     */
    private void makeSubstitutionAndRewind(final int startIndex, final int endIndex,
            final CharSequence replacement, final SourceContext context) {
        workingDocument.substitute(startIndex, endIndex, replacement, context);
        position = startIndex;
    }
    
    /* TOKENISATION METHODS START BELOW
     * ================================
     *
     * These will update the value of 'position' incrementally, so be careful!
     */
    
    //-----------------------------------------
    // Tokenisation in MATH Mode
    
    private FlowToken readNextTokenMathMode() throws SnuggleParseException {
        /* Look at first non-whitespace character */
        int c = workingDocument.charAt(position);
        switch(c) {
            case -1:
                throw new SnuggleLogicException("EOF should have been detected by caller");
                
            case '\\':
                /* Macro or special characters. Read in macro name, look it up and then
                 * read in arguments and report back */
                return readSlashToken();
                
            case '{':
                /* Start of a region in braces */
                return readBraceRegion();
                
            case '%':
                /* Start of a comment. Ignore until end of line */
                return readComment();
                
            case '&':
                /* 'Tab' character */
                return new SimpleToken(workingDocument.freezeSlice(position, position+1),
                        TokenType.TAB_CHARACTER, currentModeState.latexMode, null);
                
            case '#':
                /* Error: This is only allowed inside command/environment definitions */
                return createError(ErrorCode.TTEG04, position, position+1);
                
            default:
                /* Mathematical symbol, operator, number etc... */
                return readNextMathNumberOrSymbol();
        }
    }
    
    private FlowToken readNextMathNumberOrSymbol() {
        /* Let's see if we can reasonably parse a number */
        SimpleToken numberToken = tryReadMathNumber();
        if (numberToken!=null) {
            return numberToken;
        }
        
        /* If still here, then it wasn't a number. So we'll look again at the first non-whitespace
         * character */
        char c = (char) workingDocument.charAt(position);
        FrozenSlice thisCharSlice = workingDocument.freezeSlice(position, position+1);
        
        /* Is this a special math character? */
        MathInterpretation interpretation = Globals.getMathCharacter(c);
        if (interpretation!=null) {
            return new SimpleToken(thisCharSlice,
                    TokenType.SINGLE_CHARACTER_MATH_SPECIAL,
                    LaTeXMode.MATH, interpretation, null);
        }
        /* If still here, then we'll treat this as an identifier (e.g. 'x', 'y' etc.) */
        return new SimpleToken(thisCharSlice,
                TokenType.SINGLE_CHARACTER_MATH_IDENTIFIER,
                LaTeXMode.MATH,
                new MathIdentifierInterpretation(String.valueOf(c)), null);
    }
    
    /**
     * Attempts to read in a number at the current position, returning null if the input
     * doesn't look like a number.
     * 
     * @return SimpleToken representing the number, or null if input wasn't a number.
     */
    private SimpleToken tryReadMathNumber() {
        /* See if we can reasonably parse a number, returning null if we couldn't
         * or an appropriate token if we could.
         * 
         * TODO: Localisation! This is assuming the number is using '.' as decimal separator.
         * How does LaTeX do this?
         */
        int index = position; /* Current number search index */
        int c;
        boolean foundDigitsBeforeDecimalPoint = false;
        boolean foundDigitsAfterDecimalPoint  = false;
        boolean foundDecimalPoint = false;
        
        /* Look for leading negative sign if we are at the start of the slice */
        if (workingDocument.charAt(index)=='-') {
            index++;
        }
        /* Read zero or more digits */
        while(true) {
            c = workingDocument.charAt(index);
            if (c>='0' && c<='9') {
                foundDigitsBeforeDecimalPoint = true;
                index++;
            }
            else {
                break;
            }
        }
        /* Maybe read decimal point */
        if (workingDocument.charAt(index)=='.') {
            /* Found leading decimal point, so only allow digits afterwards */
            foundDecimalPoint = true;
            index++;
        }
        /* Bail out if we didn't find a number before and didn't find a decimal point */
        if (!foundDigitsBeforeDecimalPoint && !foundDecimalPoint) {
            return null;
        }
        /* Read zero or more digits */
        while(true) {
            c = workingDocument.charAt(index);
            if (c>='0' && c<='9') {
                foundDigitsAfterDecimalPoint = true;
                index++;
            }
            else {
                break;
            }
        }
        /* Make sure we read in some number! */
        if (!foundDigitsBeforeDecimalPoint && !foundDigitsAfterDecimalPoint) {
            return null;
        }
        FrozenSlice numberSlice = workingDocument.freezeSlice(position, index);
        return new SimpleToken(numberSlice, TokenType.MATH_NUMBER, LaTeXMode.MATH,
                new MathNumberInterpretation(numberSlice.extract()),
                null);
    }
    
    //-----------------------------------------
    // Tokenisation in PARAGRAPH or LR mode
    
    private FlowToken readNextTokenTextMode() throws SnuggleParseException {
        /* Look at first character to decide what type of token we're going to read.
         * 
         * NB: If adding any trigger characters to the switch below, then you'll need to add
         * them to {@link #readNextSimpleTextParaMode()} as well to ensure that they terminate
         * regions of plain text as well.
         */
        int c = workingDocument.charAt(position);
        switch(c) {
            case -1:
                throw new SnuggleLogicException("EOF should have been detected by caller");
                
            case '\\':
                /* This is \command, \verb or an environment control */
                return readSlashToken();
                
            case '$':
                /* Math mode $ or $$ */
                return readDollarMath();
                
            case '{':
                /* Start of a region in braces */
                return readBraceRegion();
                
            case '%':
                /* Start of a comment. Ignore until end of line */
                return readComment();
                
            case '&':
                /* 'Tab' character */
                return new SimpleToken(workingDocument.freezeSlice(position, position+1),
                        TokenType.TAB_CHARACTER, currentModeState.latexMode, null);
                
            case '_':
            case '^':
                /* Error: These are only allowed in MATH mode */
                return createError(ErrorCode.TTEM03, position, position+1);
                
            case '#':
                /* Error: This is only allowed inside command/environment definitions */
                return createError(ErrorCode.TTEG04, position, position+1);

            default:
                /* Plain text or some paragraph nonsense */
                return readNextSimpleTextParaMode();
        }
    }
    
    /**
     * Reads either the next text lump or "new paragraph" tokens, until the start of
     * something else.
     */
    private SimpleToken readNextSimpleTextParaMode() {
        /* Need to keep an eye out for 2 or more newlines, which signifies a new paragraph */
        SimpleToken result = null;
        int newLineCount = 0;
        int whitespaceStartIndex;
        int index, c;
        
        /* See if we start with blanks containing at least 2 newlines, consuming as many
         * newlines as we possibly can. */
        for (index=position; index<workingDocument.length(); index++) {
            c = workingDocument.charAt(index);
            if (c=='\n') {
                newLineCount++;
            }
            else if (!Character.isWhitespace(c)){
                break;
            }
        }
        if (newLineCount>=2) {
            /* We started with a paragraph break so return token. */
            return new SimpleToken(workingDocument.freezeSlice(position, index),
                    TokenType.NEW_PARAGRAPH, currentModeState.latexMode, TextFlowContext.ALLOW_INLINE);
        }
        /* If still here then it's normal text. Read until the start of something more interesting,
         * such as 2 newlines or a 'start' character */
        newLineCount = 0;
        whitespaceStartIndex = -1;
        for (index=position; index<workingDocument.length(); index++) {
            c = workingDocument.charAt(index);
            if (c=='\\' || c=='$' || c=='{' || c=='%' || c=='&' || c=='#' || c=='^' || c=='_') {
                break;
            }
            else if (currentModeState.terminator!=null && workingDocument.matchesAt(index, currentModeState.terminator)) {
                break;
            }
            else if (Character.isWhitespace(c)) {
                if (whitespaceStartIndex==-1) {
                    whitespaceStartIndex = index;
                }
                if (c=='\n') {
                    newLineCount++;
                    if (newLineCount==2) {
                        /* We've had 2 newlines amongst the last whitespace */
                        break;
                    }
                }
            }
            else {
                /* Normal text so turn off whitespace gathering */
                newLineCount=0;
                whitespaceStartIndex = -1;
            }
        }
        if (newLineCount==2) {
            /* Found a newline, return everything until the whitespace started. */
            result = new SimpleToken(workingDocument.freezeSlice(position, whitespaceStartIndex),
                    TokenType.TEXT_MODE_TEXT, currentModeState.latexMode, TextFlowContext.ALLOW_INLINE);
        }
        else {
            /* Just text */
            result = new SimpleToken(workingDocument.freezeSlice(position, index),
                    TokenType.TEXT_MODE_TEXT, currentModeState.latexMode, TextFlowContext.ALLOW_INLINE);
        }
        return result;
    }
    
    /**
     * Reads the rest of the current LaTeX comment line, with '%' assumed to be the first
     * character.
     * <p>
     * Bizarrely, if the next line contains non-whitespace characters then LaTeX comments
     * also absorb the newline at the end of the original line plus any leading whitespace
     * on the next line(!)
     */
    private SimpleToken readComment() {
        /* Read to end of current line */
        int index = position + 1;
        while (index<workingDocument.length() && workingDocument.charAt(index)!='\n') {
            index++;
        }
        if (workingDocument.charAt(index)=='\n') {
            /* See if the next line contains non-whitespace */
            int searchIndex = index + 1;
            int c;
            while (searchIndex<workingDocument.length() && (c=workingDocument.charAt(searchIndex))!='\n') {
                if (!Character.isWhitespace(c)) {
                    /* Found non-whitespace so terminate comment here and stop */
                    index = searchIndex;
                    break;
                }
                searchIndex++;
            }
        }
        return new SimpleToken(workingDocument.freezeSlice(position, index),
                TokenType.COMMENT, currentModeState.latexMode, TextFlowContext.IGNORE);
    }
    
    /**
     * Reads in a Math environment opened with <tt>$</tt> or <tt>$$</tt>.
     */
    private FlowToken readDollarMath() throws SnuggleParseException {
    	/* Record current LaTeX mode */
    	LaTeXMode startLatexMode = currentModeState.latexMode;
    	
        /* See if we are doing '$' or '$$' */
        boolean isDisplayMath = workingDocument.matchesAt(position, "$$");
        String delimiter = isDisplayMath ? "$$" : "$";
        
        /* Advance past the delimiter */
        position += delimiter.length();
        
        /* Now we parse this as an environment until we find the delimiter again. This works OK
         * with nested delimiters since they will always be the argument of a command, which
         * are tokenised separately so we don't have to worry.
         */
        int startContentIndex = position; /* And record this position as we're going to move on */
        ModeState contentResult = tokeniseInNewState(TokenisationMode.BUILTIN_ENVIRONMENT_CONTENT, delimiter, LaTeXMode.MATH);
        
        /* position now points just after the delimiter. */
        int endContentIndex = contentResult.foundTerminator ? position - delimiter.length() : position;
        
        /* Better also check that if the delimiter is '$' then we haven't ended up at '$$' */
        if (delimiter.equals("$") && workingDocument.charAt(position)=='$') {
        	/* Error: $ ended by $$ */
            return createError(ErrorCode.TTEM01, position, position+1);
        }
        
        /* Right, that's it! */
        FrozenSlice contentSlice = workingDocument.freezeSlice(startContentIndex, endContentIndex);
        ArgumentContainerToken contentToken = new ArgumentContainerToken(contentSlice, LaTeXMode.MATH, contentResult.tokens);
        FrozenSlice environmentSlice = workingDocument.freezeSlice(startTokenIndex, position);
        BuiltinEnvironment environment = isDisplayMath ? GlobalBuiltins.DISPLAYMATH : GlobalBuiltins.MATH;
        return new EnvironmentToken(environmentSlice, startLatexMode, environment, contentToken);
    }
    
    /**
     * Reads the content of a region explicitly delimited inside <tt>{....}</tt>. This is the
     * simplest type of "mode change" in the tokenisation process so is a useful template in
     * understanding more complicated changes.
     */
    private BraceContainerToken readBraceRegion() throws SnuggleParseException {
        int openBraceIndex = position; /* Record position of '{' */
        LaTeXMode openLaTeXMode = currentModeState.latexMode; /* Record initial LaTeX mode */
        position++; /* Advance over the '{' */
        
        /* Go out and tokenise from this point onwards until the end of the '}' */
        ModeState result = tokeniseInNewState(TokenisationMode.BRACE, "}", currentModeState.latexMode);
        
        int endInnerIndex = result.foundTerminator ? position-1 : position;
        FrozenSlice braceOuterSlice = workingDocument.freezeSlice(openBraceIndex, position); /* Includes {...} */
        FrozenSlice braceInnerSlice = workingDocument.freezeSlice(openBraceIndex+1, endInnerIndex); /* Without {...} */
        ArgumentContainerToken braceContents = new ArgumentContainerToken(braceInnerSlice, openLaTeXMode, result.tokens);
        return new BraceContainerToken(braceOuterSlice, openLaTeXMode, braceContents);
    }
    
    /** 
     * Reads in a token starting with a <tt>\\</tt>
     */
    private FlowToken readSlashToken() throws SnuggleParseException {
        FlowToken result;
        int afterSlashIndex = position+1;
        int c = workingDocument.charAt(afterSlashIndex);
        if (c==-1) {
            /* Nothing following \\ */
            result = createError(ErrorCode.TTEG01, position, afterSlashIndex, currentModeState.latexMode);
        }
        else if (c=='(' || c=='[') {
            /* It's the start of a math environment specified using \( or \[ */
            if (currentModeState.latexMode==LaTeXMode.MATH) {
                /* Error: Already in Math mode - not allowed \( or \[ */
                result = createError(ErrorCode.TTEM00, position, afterSlashIndex);
            }
            else {
                int startCommandIndex = position;
                /* Advance over the delimiter and parse environment content */
                position += 2;
                int startContentIndex = position;
                String closer = (c=='(') ? "\\)" : "\\]";
                ModeState contentResult = tokeniseInNewState(TokenisationMode.BUILTIN_ENVIRONMENT_CONTENT, closer, LaTeXMode.MATH);
                if (!contentResult.foundTerminator) {
                    /* Error: We didn't find the required terminator so we'll register an error. There
                     * are probably lots of other errors caused by the missing terminator so we'll
                     * add this error *first* in the list for the environment.
                     */
                    contentResult.tokens.add(0, createError(ErrorCode.TTEM02, startCommandIndex, position,
                            "\\" + Character.valueOf((char) c), closer));
                }
                int endContentIndex = contentResult.computeLastTokenEndIndex();
                FrozenSlice contentSlice = workingDocument.freezeSlice(startContentIndex, endContentIndex);
                FrozenSlice mathSlice = workingDocument.freezeSlice(startCommandIndex, position);
                ArgumentContainerToken contentToken = new ArgumentContainerToken(contentSlice, LaTeXMode.MATH, contentResult.tokens);
                BuiltinEnvironment environment = (c=='(') ? GlobalBuiltins.MATH : GlobalBuiltins.DISPLAYMATH;
                result = new EnvironmentToken(mathSlice, currentModeState.latexMode, environment, contentToken);
            }
        }
        else if (c==')' || c==']') {
            /* Close of a math environment specified using \) or \]. This should have
             * been discovered at the time the environment was opened (above), so this is
             * always an error if we get here.
             */
            result = createError(ErrorCode.TTEG03, position, position+2,
                    workingDocument.freezeSlice(position, position+2).extract());
        }
        else {
            /* It's not math, so must be \verb, \command or environment control */
            result = readCommandOrEnvironmentOrVerb();
        }
        return result;
    }
    
    //-----------------------------------------
    // Commands and Environments (this is by far the most complicated part of tokenisation!)
    
    /**
     * Reads in a command, <tt>\\verb</tt> or environment control token.
     */
    private FlowToken readCommandOrEnvironmentOrVerb() throws SnuggleParseException {
        /* We are handling either:
         * 
         * 1. \command[opt]{arg}{...}
         * 2. \verb...
         * 3. \begin{env}[opt]{arg}{...}...\end{env}
         * 
         * Get the first character after '\' - which the caller has already checked existence of -
         * and turn this into a command name. The name 'begin' indicates that this is the
         * start of an environment.
         */
        int startCommandNameIndex = position+1;
        String commandName = readCommandOrEnvironmentName(startCommandNameIndex);
        if (commandName==null) {
            /* The calling method should have picked this up */
            throw new SnuggleLogicException("Expected caller to have picked the commandName==null case up");
        }
        
        /* Advance over the command name */
        position += 1 + commandName.length();
        
        /* Now see if we're doing a command or an environment */
        FlowToken result = null;
        if (commandName.equals("begin")) {
            /* It's the start of a built-in or user-defined environment. */
            result = finishBeginEnvironment();
        }
        else if (commandName.equals("end")) {
            /* It's the end of a built-in or user-defined environment */
            result = finishEndEnvironment();
        }
        else if (commandName.equals(UDE_POST_BEGIN)) {
            /* Internal only! */
            result = handleUserDefinedEnvironmentControl();
        }
        else if (commandName.equals(GlobalBuiltins.VERB.getTeXName())) {
            /* It's \\verb... which needs special parsing */
            result = finishVerbToken();
        }
        else {
            /* It's a built-in or user-defined command. */
            result = finishCommand(commandName);
        }
        return result;
    }
    
    /**
     * Helper to read in the command or environment name starting at the given index, obeying the
     * esoteric rules of naming.
     * <p>
     * Note that this might return '(', ')', '[' and ']' which are considered special by the
     * parsing process so may not be re-defined.
     * <p>
     * This DOES NOT change position or any other component of the current parsing state.
     * 
     * @param startCommandNameIndex index to start reading from.
     * @return command name String, which will contain at least one character, or null if there
     *   were no further characters in the Slice.
     */
    private String readCommandOrEnvironmentName(final int startCommandNameIndex) {
        int index = startCommandNameIndex;
        int c = workingDocument.charAt(index);
        String commandName;
        if (c==-1) {
            /* Nothing to read! */
            commandName = null;
        }
        else if (!((c>='a' && c<='z') || (c>='A' && c<='Z'))) {
            /* Funny symbols are always exactly one character, which may be whitespace and include
             * reserved characters (,),[ or ].
             */
            commandName = Character.toString((char) c);
        }
        else {
            /* 1 or more alphanumeric characters, followed by an optional star */
            index++;
            while (true) {
                c = workingDocument.charAt(index);
                if (c>='a' && c<='z' || c>='A' && c<='Z') {
                    index++;
                    continue;
                }
                else if (c=='*') {
                    index++;
                    break;
                }
                else {
                    break;
                }
            }
            commandName = workingDocument.extract(startCommandNameIndex, index).toString();
        }
        return commandName;
    }
    
    /**
     * This is called once it has become clear that the next token is <tt>\verb</tt>.
     * <p>
     * As with LaTeX, this next character is used to delimit the verbatim region, which must
     * end on the same line. No whitespace can occur after \verb.
     * <p>
     * PRE-CONDITION: position will point to the character immediately after <tt>\verb</tt>.
     * 
     * @throws SnuggleParseException 
     */
    private FlowToken finishVerbToken() throws SnuggleParseException {
        /* Get the character immediately after the \verb command - whitespace not allowed */
        int startDelimitIndex = position;
        int delimitChar = workingDocument.charAt(startDelimitIndex);
        if (delimitChar==-1) {
            return createError(ErrorCode.TTEV00, startTokenIndex, startDelimitIndex);
        }
        else if (Character.isWhitespace(delimitChar)) {
            return createError(ErrorCode.TTEV00, startTokenIndex, startDelimitIndex+1);
        }
        /* We now find the end delimiter, which must occur on the same line */
        int afterStartDelimitIndex = startDelimitIndex + 1;
        int lineEndIndex = workingDocument.indexOf(afterStartDelimitIndex, '\n');
        if (lineEndIndex==-1) {
            lineEndIndex = workingDocument.length() - 1;
        }
        int endDelimitIndex = workingDocument.indexOf(afterStartDelimitIndex, (char) delimitChar);
        if (endDelimitIndex==-1) {
            return createError(ErrorCode.TTEV02, startTokenIndex, lineEndIndex+1,
                    Character.toString((char) delimitChar));
        }
        if (lineEndIndex < endDelimitIndex) {
            return createError(ErrorCode.TTEV01, startTokenIndex, lineEndIndex+1);
        }

        /* That's it - convert raw text to a command */
        FrozenSlice verbatimSlice = workingDocument.freezeSlice(startTokenIndex, endDelimitIndex+1);
        FrozenSlice verbatimContentSlice = workingDocument.freezeSlice(afterStartDelimitIndex, endDelimitIndex);
        SimpleToken verbatimContentToken = new SimpleToken(verbatimContentSlice,
                TokenType.VERBATIM_MODE_TEXT, LaTeXMode.VERBATIM, TextFlowContext.ALLOW_INLINE);
        return new CommandToken(verbatimSlice, currentModeState.latexMode, GlobalBuiltins.VERB,
                null,
                new ArgumentContainerToken[] {
                    ArgumentContainerToken.createFromSingleToken(LaTeXMode.VERBATIM, verbatimContentToken)
                });
    }
    
    //-----------------------------------------
    // Commands
    
    /**
     * Finishes reading a Command, which will either be a {@link BuiltinCommand} or a
     * {@link UserDefinedCommand}.
     * <p>
     * PRE-CONDITION: position will point to the character immediately after <tt>\commandName</tt>.
     * 
     * @param commandName name of the command being read in
     */
    private FlowToken finishCommand(final String commandName) throws SnuggleParseException {
        /* First resolve the command as either a user-defined or built-in. We search
         * for user-defined commands first, and then built-ins.
         */
        FlowToken result = null;
        UserDefinedCommand userCommand = sessionContext.getUserCommandMap().get(commandName);
        if (userCommand!=null) {
            result = finishUserDefinedCommand(userCommand);
        }
        else {
            BuiltinCommand builtinCommand = sessionContext.getCommandByTeXName(commandName);
            if (builtinCommand!=null) {
                result = finishBuiltinCommand(builtinCommand);
            }
            else {
                /* Undefined command */
                result = createError(ErrorCode.TTEC00, startTokenIndex, position, commandName);
            }
        }
        return result;
    }

    /** 
     * Finishes reading in a {@link BuiltinCommand}, catering for the different types of
     * those commands.
     * <p>
     * PRE-CONDITION: position will point to the character immediately after <tt>\commandName</tt>.
     *  
     * @throws SnuggleParseException
     */
    private FlowToken finishBuiltinCommand(final BuiltinCommand command)
            throws SnuggleParseException {
        /* Make sure we can use this command in the current mode */
        if (!command.getAllowedModes().contains(currentModeState.latexMode)) {
            /* Not allowed to use this command in this mode */
            return createError(ErrorCode.TTEC01, startTokenIndex, position,
                    command.getTeXName(), currentModeState.latexMode);
        }
        
        /* Command and environment definitions need to be handled specifically as their structure is quite
         * specific
         */
        if (command==GlobalBuiltins.NEWCOMMAND || command==GlobalBuiltins.RENEWCOMMAND) {
            return finishCommandDefinition(command);
        }
        if (command==GlobalBuiltins.NEWENVIRONMENT || command==GlobalBuiltins.RENEWENVIRONMENT) {
            return finishReadingEnvironmentDefinition(command);
        }

        /* All other commands are handled according to their type */
        switch (command.getType()) {
            case SIMPLE:
                /* Not expecting any more to read so bail out now */
                return finishSimpleCommand(command);
                
            case COMBINER:
                /* Read in next token and combine up */
                return finishCombiningCommand(command);
                
            case COMPLEX:
                /* Read arguments */
                return finishComplexCommand(command);
                
            default:
                throw new SnuggleLogicException("Unexpected switch case " + command.getType());
        }
    }
    
    /**
     * Finishes the reading of a simple command. These include "funny" (i.e. single character
     * non-alphanumeric) commands which leave trailing whitespace intact so we need to be
     * a little bit careful here.
     * <p>
     * PRE-CONDITION: position will point to the character immediately after <tt>\commandName</tt>.
     */
    private FlowToken finishSimpleCommand(final BuiltinCommand command) {
        /* Work out the next significant index after the command:
         * single non-alpha (=funny) commands do not eat up trailing whitespace;
         * all other commands do.
         */
        boolean isFunnyCommand = false;
        String commandName = command.getTeXName();
        if (commandName.length()==1) {
            char c = commandName.charAt(0);
            isFunnyCommand = !((c>='a' && c<='z') || (c>='A' && c<='Z'));
        }
        if (!isFunnyCommand) {
            skipOverWhitespace();
        }
        return new CommandToken(workingDocument.freezeSlice(startTokenIndex, position),
                currentModeState.latexMode, command);
    }

    /** 
     * Deals with pulling in the next token after something like <tt>\not</tt>
     * <p>
     * PRE-CONDITION: position will point to the character immediately after <tt>\commandName</tt>.
     *  
     * @throws SnuggleParseException
     */
    private FlowToken finishCombiningCommand(final BuiltinCommand command) throws SnuggleParseException {
        /* We always skip trailing whitespace for these types of commands */
        skipOverWhitespace();
        int afterWhitespaceIndex = position;
        
        /* Read in the next token */
        FlowToken nextToken = readNextToken();
        if (nextToken==null) {
            /* Could not find target for this combiner */
            return createError(ErrorCode.TTEC03, startTokenIndex, afterWhitespaceIndex,
                    command.getTeXName());
        }
        /* Make sure this next token is allowed to be combined with this one */
        if (!(command.getAllowedCombinerIntepretationTypes().contains(nextToken.getInterpretationType()))) {
            /* Inappropriate combiner target */
            return createError(ErrorCode.TTEC04, startTokenIndex, nextToken.getSlice().endIndex,
                    command.getTeXName());
        }
        /* Create combined token spanning the two "raw" tokens */
        return new CommandToken(workingDocument.freezeSlice(startTokenIndex, nextToken.getSlice().endIndex),
                currentModeState.latexMode, command, nextToken);
    }
    
    /**
     * Finishes the process or reading in a "complex" command, by searching for any required
     * and optional arguments.
     * <p>
     * PRE-CONDITION: position will point to the character immediately after <tt>\commandName</tt>.
     */
    private FlowToken finishComplexCommand(final BuiltinCommand command) throws SnuggleParseException {
        /* Read in and tokenise arguments, passing a "struct" to store the results in.
         * I've done it this way as this process is used in a number of different places but
         * we still need to be able to return an error if required.
         * 
         * We preserve trailing whitespace after these types of commands.
         */
        BuiltinCommandArgumentSearchResult argumentSearchResult = new BuiltinCommandArgumentSearchResult();
        ErrorToken errorToken = advanceOverBuiltinCommandOrEnvironmentArguments(command, argumentSearchResult);
        if (errorToken!=null) {
            return errorToken;
        }

        /* That's it! */
        FrozenSlice commandSlice = workingDocument.freezeSlice(startTokenIndex, position);
        return new CommandToken(commandSlice, currentModeState.latexMode, command,
                argumentSearchResult.optionalArgument,
                argumentSearchResult.requiredArguments);
    }
    
    /**
     * Trivial "struct" Object to hold the results of searching for command and/or environment
     * arguments.
     * 
     * @see LaTeXTokeniser#advanceOverBuiltinCommandOrEnvironmentArguments(CommandOrEnvironment, BuiltinCommandArgumentSearchResult)
     */
    static class BuiltinCommandArgumentSearchResult {
        
        /** Tokenised version of optional argument, null if not supported or not requested. */
        public ArgumentContainerToken optionalArgument;
        
        /** 
         * Tokenised versions of required arguments, will include null entries for arguments
         * where tokenisation was not asked for and did not occur.
         */
        public ArgumentContainerToken[] requiredArguments;
    }
    
    /**
     * This helper reads in the optional and required arguments for a Command or Environment,
     * starting at the current position.
     * <p>
     * Trailing whitespace is always preserved after the command/environment.
     * <p>
     * PRE-CONDITION: position will point to the character immediately after <tt>\commandName</tt>.
     * POST-CONDITION: position will point to immediately after the last argument.
     * 
     * @param commandOrEnvironment
     * @param result blank result Object that will be filled in by this method.
     * 
     * @return ErrorToken if parsing failed, null otherwise.
     * @throws SnuggleParseException
     */
    private ErrorToken advanceOverBuiltinCommandOrEnvironmentArguments(final CommandOrEnvironment commandOrEnvironment,
            final BuiltinCommandArgumentSearchResult result) throws SnuggleParseException {
        /* First of all see if we're expecting arguments and bail if not */
        if (commandOrEnvironment.getArgumentCount()==0 && !commandOrEnvironment.isAllowingOptionalArgument()) {
            result.optionalArgument = null;
            result.requiredArguments = ArgumentContainerToken.EMPTY_ARRAY;
            return null;
        }
        
        /* If still here, we're expecting arguments so skip any whitespace before first argument */
        skipOverWhitespace();
        
        /* Consider optional argument, if allowed */
        ArgumentContainerToken optionalArgument = null;
        FrozenSlice optionalArgumentSlice = null;
        int c;
        int argumentIndex = 0;
        LaTeXMode argumentMode;
        ModeState argumentResult;
        if (commandOrEnvironment.isAllowingOptionalArgument()) {
            /* Decide what mode we'll be parsing this argument in, defaulting to current mode if
             * nothing is specified */
            argumentMode = commandOrEnvironment.getArgumentMode(argumentIndex++);
            if (argumentMode==null) {
                argumentMode = currentModeState.latexMode;
            }
            /* Now handle optional argument, if provided */
            c = workingDocument.charAt(position);
            if (c=='[') {
                int openBracketIndex = position; /* And record this position as we're going to move on */
                position++; /* Advance to just after the '[' ... */
                
                /* Go out and tokenise from this point onwards until the end of the ']' */
                argumentResult = tokeniseInNewState(TokenisationMode.COMMAND_ARGUMENT, "]", argumentMode);
                optionalArgumentSlice = workingDocument.freezeSlice(openBracketIndex, position);
                optionalArgument = new ArgumentContainerToken(optionalArgumentSlice, argumentMode, argumentResult.tokens);
            }
        }
        
        /* Look for required arguments.
         * 
         * These must all be specified using {....}
         * 
         * HOWEVER: If the command takes no 1 argument and no optional argument has been provided,
         * then LaTeX allows the next single token to be taken as the argument if it is not a brace.
         * 
         * E.g. \sqrt x is interpreted the same way as \sqrt{x}
         * 
         * We will allow that behaviour here.
         * 
         * TODO: Maybe make this optional?
         * 
         * Note that \sqrt xy is interpreted as \sqrt{x}y !!!
         */
        int argCount = commandOrEnvironment.getArgumentCount();
        ArgumentContainerToken[] requiredArguments = new ArgumentContainerToken[argCount];
        FrozenSlice[] requiredArgumentSlices = new FrozenSlice[argCount];
        for (int i=0; i<argCount; i++) {
            /* Skip any whitespace before this argument */
            skipOverWhitespace();
            
            /* Decide which parsing mode to use for this argument, using current if none specified */
            argumentMode = commandOrEnvironment.getArgumentMode(argumentIndex++);
            if (argumentMode==null) {
                argumentMode = currentModeState.latexMode;
            }
            
            /* Now look for this required argument */
            c = workingDocument.charAt(position);
            if (c=='{') {
                int openBracketIndex = position; /* and record position before we move on */
                position++; /* Skip over open brace... */
                
                /* Go out and tokenise from this point onwards until the end of the '}' */
                argumentResult = tokeniseInNewState(TokenisationMode.COMMAND_ARGUMENT, "}", argumentMode);
                requiredArgumentSlices[i] = workingDocument.freezeSlice(openBracketIndex, position);
                requiredArguments[i] = new ArgumentContainerToken(requiredArgumentSlices[i], argumentMode, argumentResult.tokens);
            }
            else if (c!=-1 && i==0 && argCount==1 && optionalArgument==null && commandOrEnvironment instanceof Command) {
                /* Special case listed above: command with 1 argument as a single token with no braces.
                 * Temporarily change LaTeX mode to that of the argument, pull in the next
                 * token and revert the mode to what we had initially.
                 */
                LaTeXMode currentLaTeXMode = currentModeState.latexMode;
                currentModeState.latexMode = argumentMode;
                FlowToken nextToken = readNextToken();
                currentModeState.latexMode = currentLaTeXMode;
                if (nextToken!=null) {
                    requiredArguments[i] = ArgumentContainerToken.createFromSingleToken(argumentMode, nextToken);
                    requiredArgumentSlices[i] = requiredArguments[i].getSlice();
                }
                else {
                    /* Error: Missing first (and only) argument. */
                    return createError(ErrorCode.TTEC02, startTokenIndex, position,
                            commandOrEnvironment.getTeXName(), Integer.valueOf(1));
                }
            }
            else {
                /* Error: Missing '#n' argument (where n=i+1).
                 * (There is one variant of this for commands and one for environments)
                 */
                return createError(
                        (commandOrEnvironment instanceof Command) ? ErrorCode.TTEC02 : ErrorCode.TTEE06,
                        startTokenIndex, position,
                        commandOrEnvironment.getTeXName(), Integer.valueOf(i+1));
            }
        }
        
        /* Fill in result and return null indicating success */
        result.optionalArgument = optionalArgument;
        result.requiredArguments = requiredArguments;
        return null;
    }
    
    /**
     * Finishes the process of reading in and evaluating a {@link UserDefinedCommand}.
     * <p>
     * PRE-CONDITION: position will point to the character immediately after <tt>\commandName</tt>.
     */
    private FlowToken finishUserDefinedCommand(final UserDefinedCommand command)
            throws SnuggleParseException {
        /* Read in argument as raw Slices, since we're going to perform parameter
         * interpolation on them.
         * 
         * We also KEEP the final trailing whitespace after the last part of the
         * command so that when it is substituted, there is whitespace left for further parsing.
         */
        UserDefinedCommandArgumentSearchResult argumentSearchResult = new UserDefinedCommandArgumentSearchResult();
        ErrorToken errorToken = advanceOverUserDefinedCommandOrEnvironmentArguments(command, argumentSearchResult);
        if (errorToken!=null) {
            return errorToken;
        }
        
        /* Right... what we now do is create a temporary document with the place-holders in the
         * command definition replaced with the arguments. This is then tokenised along with
         * enough of the rest of the existing document to ensure tokens are correctly balanced
         * or finished, and the resulting token becomes the final result. Phew!
         */
        String replacement = command.getDefinitionSlice().extract().toString();
        
        /* Now make substitutions using the usual convention that #n indicates the 'n'th
         * argument, where the optional argument is assumed to be first (if present) followed
         * by other args.
         */
        int argumentNumber = 1;
        if (command.isAllowingOptionalArgument()) {
            replacement = replacement.replace("#1",
                    argumentSearchResult.optionalArgument!=null ? argumentSearchResult.optionalArgument : "");
            argumentNumber++;
        }
        for (int i=0; i<argumentSearchResult.requiredArguments.length; i++) {
            replacement = replacement.replace("#" + (argumentNumber++),
                    argumentSearchResult.requiredArguments[i]);
        }

        /* Now we rewind to the start of the command and replace it with our substitution, and
         * then continue parsing as normal.
         */
        int afterCommandIndex = position;
        makeSubstitutionAndRewind(startTokenIndex, afterCommandIndex, replacement, null /* No special context info */);
        return readNextToken();
    }
    
    /**
     * Trivial "struct" Object to hold the results of searching for user-defined
     * command and/or environment arguments, which are initially treated as unparsed
     * but balanced {@link WorkingDocument}s.
     */
    static class UserDefinedCommandArgumentSearchResult {
        
        /** Optional argument, null if not supported. */
        public CharSequence optionalArgument;
        
        /** Required arguments */
        public CharSequence[] requiredArguments;
    }
    
    /**
     * This helper reads in the optional and required arguments for a Command or Environment,
     * starting at the current position.
     * <p>
     * The value of {@link #position} will be updated by this.
     * <p>
     * Trailing whitespace is always preserved after the command/environment.
     * <p>
     * PRE-CONDITION: position will point to the character immediately after <tt>\commandName</tt>.
     * POST-CONDITION: position will point to immediately after the last argument.
     * 
     * @param commandOrEnvironment
     * @param result blank result Object that will be filled in by this method.
     * 
     * @return ErrorToken if parsing failed, null otherwise.
     * @throws SnuggleParseException
     */
    private ErrorToken advanceOverUserDefinedCommandOrEnvironmentArguments(final CommandOrEnvironment commandOrEnvironment,
            final UserDefinedCommandArgumentSearchResult result) throws SnuggleParseException {
        /* First of all see if we're expecting arguments and bail if not */
        if (commandOrEnvironment.getArgumentCount()==0 && !commandOrEnvironment.isAllowingOptionalArgument()) {
            result.optionalArgument = null;
            result.requiredArguments = new CharSequence[0];
            return null;
        }
        
        /* Skip any whitespace before arguments */
        skipOverWhitespace();
        
        /* Consider optional argument, if allowed */
        int c;
        CharSequence optionalArgument = null;
        if (commandOrEnvironment.isAllowingOptionalArgument()) {
            /* Now handle optional argument, if provided */
            c = workingDocument.charAt(position);
            if (c=='[') {
                int openBracketIndex = position;
                int afterCloseBracketIndex = findEndSquareBrackets(openBracketIndex);
                if (afterCloseBracketIndex==-1) {
                    /* Error: no matching ']' */
                    return createError(ErrorCode.TTEG00, startTokenIndex, workingDocument.length(), ']');
                }
                optionalArgument = workingDocument.extract(openBracketIndex+1, afterCloseBracketIndex-1);
                position = afterCloseBracketIndex;
            }
        }
        
        /* Look for required arguments in the same way that we do it for built-in commands and
         * environments.
         */
        int argCount = commandOrEnvironment.getArgumentCount();
        CharSequence[] requiredArguments = new CharSequence[argCount];
        for (int i=0; i<argCount; i++) {
            /* Skip any whitespace before this argument */
            skipOverWhitespace();
            
            /* Now look for argument */
            c = workingDocument.charAt(position);
            if (c=='{') {
                int openBraceIndex = position;
                int afterCloseBraceIndex = findEndCurlyBrackets(openBraceIndex);
                if (afterCloseBraceIndex==-1) {
                    /* Error: no matching '}' */
                    return createError(ErrorCode.TTEG00, startTokenIndex, workingDocument.length(), '}');
                }
                requiredArguments[i] = workingDocument.extract(openBraceIndex+1, afterCloseBraceIndex-1);
                
                /* Move past close brace */
                position = afterCloseBraceIndex;
            }
            else if (c!=-1 && i==0 && argCount==1 && result.optionalArgument==null) {
                /* Special case listed above: 1 argument as a single token with no braces.
                 * 
                 * NOTE: This one is slightly complicated here in that we need to read the next token
                 * in now, which has an unwelcome side-effect of freezing the working document up
                 * to the end of this token. Since all we want is the content of this token, we
                 * "unfreeze" the working document back to the start of the current command.
                 */
                FlowToken nextToken = readNextToken();
                if (nextToken==null) {
                    /* Error: Missing first (and only) argument. */
                    return createError(ErrorCode.TTEC02, startTokenIndex, position,
                            commandOrEnvironment.getTeXName(), Integer.valueOf(1));
                }
                FrozenSlice nextSlice = nextToken.getSlice();
                requiredArguments[i] = nextSlice.extract();
                workingDocument.unfreeze(startTokenIndex); /* (nextSlice is now invalid!) */
            }
            else {
                /* Error: Missing '#n' argument (where n=i+1) */
                return createError(commandOrEnvironment instanceof Command ? ErrorCode.TTEC02 : ErrorCode.TTEE06,
                        startTokenIndex, position,
                        commandOrEnvironment.getTeXName(), Integer.valueOf(i+1));
            }
        }
        /* Record result and return null indicating success */
        result.optionalArgument = optionalArgument;
        result.requiredArguments = requiredArguments;
        return null;
    }
    
    //-----------------------------------------
    // Environments
    
    /**
     * Handles <tt>\\begin...</tt>
     * <p>
     * PRE-CONDITION: position will point to the character immediately after <tt>\\begin</tt>.
     */
    private FlowToken finishBeginEnvironment() throws SnuggleParseException {
        /* Read {envName} */
        String environmentName = advanceOverBracesAndEnvironmentName();
        if (environmentName==null) {
            /* Expected to find {envName} */
            return createError(ErrorCode.TTEE01, startTokenIndex, position);
        }
        
        /* If this is a 'verbatim' environment then we'll handle things explicitly and return
         * now since it doesn't behave like other environments.
         */
        if (environmentName.equals(GlobalBuiltins.VERBATIM.getTeXName())) {
            return finishVerbatimEnvironment();
        }
        
        /* Look up environment, taking user-defined on in preference to built-in */
        UserDefinedEnvironment userEnvironment = sessionContext.getUserEnvironmentMap().get(environmentName);
        FlowToken result = null;
        if (userEnvironment!=null) {
            result = finishBeginUserDefinedEnvironment(userEnvironment);
        }
        else {
            BuiltinEnvironment builtinEnvironment = sessionContext.getEnvironmentByTeXName(environmentName);
            if (builtinEnvironment!=null) {
                result = finishBeginBuiltinEnvironment(builtinEnvironment);
                
            }
            else {
                /* Undefined environment name */
                result = createError(ErrorCode.TTEE02, startTokenIndex, position, environmentName);
            }
        }
        return result;
    }
    
    /**
     * Handles <tt>\\end...</tt>
     * <p>
     * PRE-CONDITION: position will point to the character immediately after <tt>\\end</tt>.
     */
    private FlowToken finishEndEnvironment() throws SnuggleParseException {
        /* Read {envName} */
        String environmentName = advanceOverBracesAndEnvironmentName();
        if (environmentName==null) {
            /* Expected to find {envName} */
            return createError(ErrorCode.TTEE01, startTokenIndex, position);
        }
        
        /* First, we make sure this balances with what is open */
        String lastOpenName = openEnvironmentStack.isEmpty() ? null : openEnvironmentStack.peek();
        if (lastOpenName==null) {
            /* No environments are open */
            return createError(ErrorCode.TTEE05, startTokenIndex, position);
        }
        else if (!environmentName.equals(lastOpenName)) {
            /* Got end of envName, rather than one in the stack */
            return createError(ErrorCode.TTEE00, startTokenIndex, position,
                    environmentName, lastOpenName);

        }
        else {
            openEnvironmentStack.pop();
        }
        
        /* Look up environment, taking user-defined on in preference to built-in */
        UserDefinedEnvironment userEnvironment = sessionContext.getUserEnvironmentMap().get(environmentName);
        FlowToken result = null;
        if (userEnvironment!=null) {
            result = finishEndUserDefinedEnvironment(userEnvironment);
        }
        else {
            BuiltinEnvironment builtinEnvironment = sessionContext.getEnvironmentByTeXName(environmentName);
            if (builtinEnvironment!=null) {
                /* The end of a build-in environment is special as the \\begin{env} will have
                 * started a new tokenisation level. We simply return null to indicate there's
                 * no more at this level. 
                 */
            }
            else {
                /* Undefined environment name */
                result = createError(ErrorCode.TTEE02, startTokenIndex, position, environmentName);
            }
        }
        return result;
    }
    
    /**
     * Reads text of the form '{envName}' from the current position onwards, skipping any leading
     * whitespace and advancing the current position to just after the final '}'.
     * <p>
     * Returns null if this information was not found.
     * <p>
     * PRE-CONDITION: position will point to the character immediately after <tt>\\begin</tt> or <tt>\\end</tt>
     * POST-CONDITION: position will point to just after the <tt>{envName}</tt>, if found
     */
    private String advanceOverBracesAndEnvironmentName() {
        /* Skip leading whitespace */
        skipOverWhitespace();
        
        /* Read in name of environment and move beyond it */
        if (workingDocument.charAt(position)!='{') {
            return null;
        }
        String environmentName = readCommandOrEnvironmentName(++position);
        position += environmentName.length(); /* Move after the name, to hopefully '}' */
        if (workingDocument.charAt(position)!='}') {
            /* Expected to find {envName} */
            return null;
        }
        position++; /* Move after the '}' */
        return environmentName;
    }
    
    //------------------------------------------------------
    // Built-in Environments
    
    /**
     * Finishes off reading a built-in environment.
     * <p>
     * PRE-CONDITION: position will point to the character immediately after <tt>\\begin{envName}</tt>.
     * 
     * @param environment environment being read in
     * @throws SnuggleParseException
     */
    private FlowToken finishBeginBuiltinEnvironment(final BuiltinEnvironment environment) throws SnuggleParseException {
        /* Record that this environment has opened */
        openEnvironmentStack.push(environment.getTeXName());
        
        /* Check whether we can use this environment in this mode.
         * 
         * We won't bail immediately on error here as it is prudent to pull in the content
         * anyway to ensure things match up */
        LaTeXMode startLatexMode = currentModeState.latexMode;
        ErrorToken errorToken = null;
        if (!environment.getAllowedModes().contains(currentModeState.latexMode)) {
            /* Error: this environment can't be used in the current mode */
            errorToken = createError(ErrorCode.TTEE03, startTokenIndex, position,
                    environment.getTeXName(), startLatexMode);
        }
        
        /* Read in arguments, the same way that we do with commands. */
        BuiltinCommandArgumentSearchResult argumentSearchResult = new BuiltinCommandArgumentSearchResult();
        errorToken = advanceOverBuiltinCommandOrEnvironmentArguments(environment, argumentSearchResult);
        
        /* Gobble up any whitespace before the start of the content */
        skipOverWhitespace();
        
        /* Now we parse the environment content. We don't set a terminator here - the tokenisation
         * logic knows to search for \\end and make sure things are balanced up with whatever
         * is open. When an \\end is encountered, it returns a null token and leaves the
         * position just after the \\end.
         */
        int startContentIndex = position; /* And record this position as we're going to move on */
        LaTeXMode contentMode = environment.getContentMode();
        if (contentMode==null) {
            contentMode = currentModeState.latexMode;
        }
        ModeState contentResult = tokeniseInNewState(TokenisationMode.BUILTIN_ENVIRONMENT_CONTENT, null, contentMode);
        int endContentIndex = contentResult.computeLastTokenEndIndex();
        FrozenSlice contentSlice = workingDocument.freezeSlice(startContentIndex, endContentIndex);
        ArgumentContainerToken contentToken = new ArgumentContainerToken(contentSlice, contentMode, contentResult.tokens);
        
        /* position now points just after the \\end{envName} */
        
        /* Bail now if we encountered any errors */
        if (errorToken!=null) {
            /* We'll recreate the ErrorToken so that it ends at the current position so that
             * parsing will continue from after \\end{envName}.
             */
            sessionContext.getErrors().remove(errorToken.getError()); /* Remove entry added above */
            return createError(errorToken.getError().getErrorCode(),
                    startTokenIndex, position, errorToken.getError().getArguments());
        }
        
        /* Success! */
        FrozenSlice environmentSlice = workingDocument.freezeSlice(startTokenIndex, position);
        return new EnvironmentToken(environmentSlice, startLatexMode, environment,
                argumentSearchResult.optionalArgument,
                argumentSearchResult.requiredArguments,
                contentToken);
    }
    
    //------------------------------------------------------
    // User-defined Environments

    /**
     * Finishes the handling of the start of a user-defined environment. This replaces the
     * <tt>\\begin{envName}</tt> with the appropriate substitution and reparses.
     * <p>
     * PRE-CONDITION: position will point to the character immediately after <tt>\\begin{envName}</tt>.
     */
    private FlowToken finishBeginUserDefinedEnvironment(final UserDefinedEnvironment environment) throws SnuggleParseException {
        /* Read in arguments in unparsed form. */
        UserDefinedCommandArgumentSearchResult argumentSearchResult = new UserDefinedCommandArgumentSearchResult();
        ErrorToken errorToken = advanceOverUserDefinedCommandOrEnvironmentArguments(environment, argumentSearchResult);
        if (errorToken!=null) {
            return errorToken;
        }
        
        /* Check that this environment is not already in the process of opening */
        String environmentName = environment.getTeXName();
        if (userEnvironmentsOpeningSet.contains(environmentName)) {
            /* Error: detected recursion */
            return createError(ErrorCode.TTEUE4, startTokenIndex, position,
                    environment.getTeXName());
        }
        
        /* Record that this environment is opening. We'll remove this once it has safely finished */
        userEnvironmentsOpeningSet.add(environmentName);
        
        /* Now, as per LaTeX 2e, we make substitutions in the *begin* definition. */
        FrozenSlice beginSlice = environment.getBeginDefinitionSlice();
        String resolvedBegin = beginSlice.extract().toString();
        int argumentNumber = 1;
        if (environment.isAllowingOptionalArgument()) {
                resolvedBegin = resolvedBegin.replace("#1",
                      argumentSearchResult.optionalArgument!=null ? argumentSearchResult.optionalArgument : "");
              argumentNumber++;
        }
        for (int i=0; i<argumentSearchResult.requiredArguments.length; i++) {
              resolvedBegin = resolvedBegin.replace("#" + (argumentNumber++),
                      argumentSearchResult.requiredArguments[i]);
        }
        
        /* We add an extra command after the replacement to do housekeeping once the environment
         * has finished opening up.
         */
        resolvedBegin += "\\" + UDE_POST_BEGIN + "{" + environment.getTeXName() + "}";

        /* Substitute our \begin{...} clause with the replacement */
        int endBeginIndex = position;
        makeSubstitutionAndRewind(startTokenIndex, endBeginIndex, resolvedBegin,
                null /* No special context information required */);
        
        /* Then just return the next token */
        return readNextToken();
    }
    
    /**
     * Finishes the handling of the end of a user-defined environment. This replaces the
     * <tt>\\end{envName}</tt> with the appropriate substitution and reparses.
     * <p>
     * PRE-CONDITION: position will point to the character immediately after <tt>\\begin{envName}</tt>.
     */
    private FlowToken finishEndUserDefinedEnvironment(final UserDefinedEnvironment environment)
            throws SnuggleParseException {
        /* Substitute the whole \end{...} clause with the definition */
        int endEndIndex = position;
        makeSubstitutionAndRewind(startTokenIndex, endEndIndex,
                environment.getEndDefinitionSlice().extract(),
                null /* No special context information */);
        return readNextToken();
    }
    
    /**
     * Handles the special internal "begin user-defined environment finished" token that is
     * substituted into the input when processing the start of a user-defined environment.
     * This prompts some house-keeping, which is performed here.
     * <p>
     * PRE-CONDITION: position will point to the character immediately after <tt>\\end</tt>.
     */
    private FlowToken handleUserDefinedEnvironmentControl() throws SnuggleParseException {
        /* Read {envName} */
        String environmentName = advanceOverBracesAndEnvironmentName();
        if (environmentName==null) {
            /* Expected to find {envName} */
            throw new SnuggleLogicException("Expected to find {envName}");
        }
        
        /* Look up environment */
        UserDefinedEnvironment userEnvironment = sessionContext.getUserEnvironmentMap().get(environmentName);
        if (userEnvironment==null) {
            throw new SnuggleLogicException("Environment is not user-defined");
        }
        
        /* Remove this from the list of things that are opening */
        userEnvironmentsOpeningSet.remove(environmentName);
        
        /* Now we can register this environment as begin open */
        openEnvironmentStack.push(environmentName);
        
        /* Next, we obliterate this temporary token from the input and re-parse */
        makeSubstitutionAndRewind(startTokenIndex, position, "", null);
        return readNextToken();
    }
    
    /**
     * This does the job of reading in the content of a <tt>verbatim</tt> environment. The content
     * mode is different here - we treat everything after <tt>\begin{verbatim}</tt> until before
     * the next <tt>\end{verbatim}</tt> as the environment content.
     * <p>
     * PRE-CONDITION: position will point to the character immediately after <tt>\\begin{verbatim}</tt>.
     */
    private FlowToken finishVerbatimEnvironment() throws SnuggleParseException {
        /* The content model can be dealt with by a regular expression here as it is nicely flat. */
        CharSequence inputUntilEnd = workingDocument.extract(position, workingDocument.length());
        Pattern contentPattern = Pattern.compile("^(.+?)\\\\end\\s*\\{verbatim\\}\\s*");
        Matcher contentMatcher = contentPattern.matcher(inputUntilEnd);
        if (!contentMatcher.find()) {
            /* Could not find end of verbatim environment */
            return createError(ErrorCode.TTEV03, startTokenIndex, workingDocument.length());
        }
        /* Need to work out how much content there is any also the first non-whitespace after
         * \end{verbatim} so that the next token can be found.
         * (Also remember that the indexes have to be taken relative to the document, not
         * the String we're matching on)
         */
        int contentEndIndex = position + contentMatcher.group(1).length();
        int nextReadIndex = position + contentMatcher.group().length();
        FrozenSlice contentSlice = workingDocument.freezeSlice(position, contentEndIndex);
        
        FrozenSlice envSlice = workingDocument.freezeSlice(startTokenIndex, nextReadIndex);
        SimpleToken contentToken = new SimpleToken(contentSlice,
                TokenType.VERBATIM_MODE_TEXT, LaTeXMode.VERBATIM, TextFlowContext.START_NEW_XHTML_BLOCK);
        return new EnvironmentToken(envSlice, currentModeState.latexMode, GlobalBuiltins.VERBATIM,
                ArgumentContainerToken.createFromSingleToken(currentModeState.latexMode, contentToken));
    }
    
    //-----------------------------------------
    // Command and Environment Definition

    /**
     * Finishes reading the definition of a user-defined command specified using <tt>\\newcommand</tt>
     * or similar.
     * <p>
     * PRE-CONDITION: position will point to the character immediately after <tt>\\newcommand</tt>.
     */
    private FlowToken finishCommandDefinition(final BuiltinCommand definitionCommand)
            throws SnuggleParseException {
        /* Skip whitespace after \\newcommand or whatever. */
        skipOverWhitespace();
        
        /* First thing to read is the name of the command, which is written as either
         * 
         * {\name} or \name
         */
        boolean nameIsInBraces = false;
        int c = workingDocument.charAt(position);
        if (c==-1) {
            /* Error: input terminated before name of new command */
            return createError(ErrorCode.TTEUC0, startTokenIndex, position);
        }
        else if (c=='{') { /* It's {\name}, with possible whitespace */
            position++;
            skipOverWhitespace();
            nameIsInBraces = true;
        }
        /* Try to read in \name */
        if (workingDocument.charAt(position)!='\\') {
            /* Error: command name must be preceded by \\ */
            return createError(ErrorCode.TTEUC1, startTokenIndex, position);
        }
        String commandName = readCommandOrEnvironmentName(++position);
        if (commandName==null) {
            /* Error: input terminated before name of new command */
            return createError(ErrorCode.TTEUC0, startTokenIndex, position);
        }
        else if (reservedCommands.contains(commandName)) {
            /* Error: Not allowed to redefine reserved command */
            return createError(ErrorCode.TTEUC8, startTokenIndex, position+commandName.length(),
                    commandName);
        }
        position += commandName.length();
        if (nameIsInBraces) {
            /* Skip over whitespace and make sure closing brace is there */
            skipOverWhitespace();
            if (workingDocument.charAt(position)!='}') {
                /* Error: No matching '}' after command name */
                return createError(ErrorCode.TTEUC6, startTokenIndex, position);
            }
            position++;
        }
        
        /* Read in specification of arguments. */
        ArgumentDefinitionResult argumentDefinitionResult = new ArgumentDefinitionResult();
        ErrorToken error = advanceOverUserDefinedCommandOrEnvironmentArgumentDefinition(commandName, argumentDefinitionResult);
        if (error!=null) {
            return error;
        }
        
        /* Finally we read the command body, specified within {...} */
        c = workingDocument.charAt(position);
        if (c!='{') {
            /* Error: No command definition found */
            return createError(ErrorCode.TTEUC3, startTokenIndex, position, commandName);
        }
        int startCurlyIndex = position;
        int endCurlyIndex = findEndCurlyBrackets(position);
        if (endCurlyIndex==-1) {
            /* Error: Document ended before end of command definition */
            return createError(ErrorCode.TTEUC2, startTokenIndex, workingDocument.length());
        }
        
        /* Skip trailing whitespace */
        position = endCurlyIndex;
        skipOverWhitespace();
        
        /* Now create the new command */
        FrozenSlice definitionSlice = workingDocument.freezeSlice(startCurlyIndex+1, endCurlyIndex-1);
        UserDefinedCommand userCommand = new UserDefinedCommand(commandName,
                argumentDefinitionResult.allowOptionalArgument,
                argumentDefinitionResult.requiredArgumentCount,
                definitionSlice);
        
        /* Register the command so that it can be used, depending on whether we are doing a renew
         * or not. */
        Map<String, UserDefinedCommand> userCommandMap = sessionContext.getUserCommandMap();
        boolean isRenewing = definitionCommand==GlobalBuiltins.RENEWCOMMAND;
        boolean isCommandAlreadyDefined = userCommandMap.containsKey(commandName) || sessionContext.getCommandByTeXName(commandName)!=null;
        if (isRenewing && !isCommandAlreadyDefined) {
            /* Command does not already exist so can't be renewed */
            return createError(ErrorCode.TTEUC4, startTokenIndex, position, commandName);
        }
        else if (!isRenewing && isCommandAlreadyDefined) {
            /* Command already exists so can't be "new"ed */
            return createError(ErrorCode.TTEUC5, startTokenIndex, position, commandName);
        }
        userCommandMap.put(commandName, userCommand);
        
        /* Finally, return token representing the definition */
        return new CommandToken(workingDocument.freezeSlice(startTokenIndex, position),
                currentModeState.latexMode, definitionCommand);
    }
    
    /**
     * Finishes reading the definition of a user-defined environment specified using
     * <tt>\\newenvironment</tt> or similar.
     * <p>
     * PRE-CONDITION: position will point to the character immediately after <tt>\\newenvironment</tt>.
     */
    private FlowToken finishReadingEnvironmentDefinition(final BuiltinCommand definitionCommand)
            throws SnuggleParseException {
        /* Skip whitespace after \\newenvironment or whatever. */
        skipOverWhitespace();
        
        /* Read name of new environment, specified as {envName} */
        String environmentName = advanceOverBracesAndEnvironmentName();
        if (environmentName==null) {
            /* Expected to find {envName} */
            return createError(ErrorCode.TTEUE0, startTokenIndex, position);
        }
        else if (reservedCommands.contains(environmentName)) {
            /* Error: Cannot redefine these special commands */
            return createError(ErrorCode.TTEUC8, startTokenIndex, position+2+environmentName.length() /* Skip {envName} */,
                    environmentName);
        }
        
        /* Skip whitespace after name */
        skipOverWhitespace();
        
        /* Read in specification of arguments. */
        ArgumentDefinitionResult argumentDefinitionResult = new ArgumentDefinitionResult();
        ErrorToken error = advanceOverUserDefinedCommandOrEnvironmentArgumentDefinition(environmentName, argumentDefinitionResult);
        if (error!=null) {
            return error;
        }
        
        /* Finally we read the 'begin' and 'end' bodies, specified within {...} */
        FrozenSlice[] definitionSlices = new FrozenSlice[2];
        int c;
        for (int i=0; i<2; i++) {
            c = workingDocument.charAt(position);
            if (c!='{') {
                /* Missing definition for begin/end */
                return createError(ErrorCode.TTEUE1, startTokenIndex, position,
                        ((i==0) ? "begin" : "end"), environmentName);
            }
            int startCurlyIndex = position;
            int endCurlyIndex = findEndCurlyBrackets(position);
            
            /* Skip trailing whitespace */
            position = endCurlyIndex;
            skipOverWhitespace();

            /* Record slice */
            definitionSlices[i] = workingDocument.freezeSlice(startCurlyIndex+1, endCurlyIndex-1);
        }
        
        /* Now create new environment */
        UserDefinedEnvironment userEnvironment = new UserDefinedEnvironment(environmentName,
                argumentDefinitionResult.allowOptionalArgument,
                argumentDefinitionResult.requiredArgumentCount,
                definitionSlices[0], definitionSlices[1]);
        
        /* Register the environment so that it can be used, depending on whether we are doing a renew
         * or not. */
        Map<String, UserDefinedEnvironment> userEnvironmentMap = sessionContext.getUserEnvironmentMap();
        boolean isRenewing = definitionCommand==GlobalBuiltins.RENEWENVIRONMENT;
        boolean isEnvAlreadyDefined = userEnvironmentMap.containsKey(environmentName) || sessionContext.getEnvironmentByTeXName(environmentName)!=null;
        if (isRenewing && !isEnvAlreadyDefined) {
            /* Error: Environment is not already defined so can't be renewed */
            return createError(ErrorCode.TTEUE2, startTokenIndex, position, environmentName);
        }
        else if (!isRenewing && isEnvAlreadyDefined) {
            /* Error: Environment is already defined so can't be "new"ed */
            return createError(ErrorCode.TTEUE3, startTokenIndex, position, environmentName);
        }
        userEnvironmentMap.put(environmentName, userEnvironment);
        
        /* Finally, return token representing the definition */
        CommandToken result = new CommandToken(workingDocument.freezeSlice(startTokenIndex, position),
                currentModeState.latexMode, definitionCommand);
        return result;
    }
    
    /**
     * Trivial result Object for {@link LaTeXTokeniser#advanceOverUserDefinedCommandOrEnvironmentArgumentDefinition(String, ArgumentDefinitionResult)}
     */
    static final class ArgumentDefinitionResult {
        public boolean allowOptionalArgument;
        public int requiredArgumentCount;
    }
    
    /**
     * Reads in the argument specification for a new command or environment. This will be of
     * the form <tt>[n]</tt> or <tt>[n][]</tt>.
     * <p>
     * PRE-CONDITION: position should be set to just after <tt>\\newcommand{name}</tt>
     *   or <tt>\\newenvironment{name}</tt>
     * POST-CONDITION: position will now be directly after the arguments
     * 
     * @param commandOrEnvironmentName
     * @param result
     * @throws SnuggleParseException
     */
    private ErrorToken advanceOverUserDefinedCommandOrEnvironmentArgumentDefinition(final String commandOrEnvironmentName,
            final ArgumentDefinitionResult result) throws SnuggleParseException {
        /* Skip initial whitespace */
        skipOverWhitespace();
        
        /* Next we read in the number of arguments, specified as [<1-9>],
         * then whether there are optional arguments (specified as a further [<anything>])
         * 
         * Also note that the number of arguments includes whether there is an optional argument!
         * 
         * NOTE: Don't blame me - I didn't write \newcommand and friends!!
         * 
         * Examples:
         * 
         * [2] -> no optional argument, 2 mandatory arguments
         * [][2] -> 1 optional argument, 2-1=1 mandatory argument
         */
        int argCount = 0;
        boolean allowOptArgs = false;
        int c = workingDocument.charAt(position);
        if (c=='[') {
            int afterFirstSquare = findEndSquareBrackets(position);
            if (afterFirstSquare==-1) {
                /* Error: no ']' found! */
                return createError(ErrorCode.TTEUC9, startTokenIndex, workingDocument.length());
            }
            String rawArgCount = workingDocument.extract(position+1, afterFirstSquare-1).toString().trim();
            try {
                argCount = Integer.parseInt(rawArgCount);
            }
            catch (NumberFormatException e) {
                /* Error: Not an integer! */
                return createError(ErrorCode.TTEUC7, startTokenIndex, afterFirstSquare,
                        commandOrEnvironmentName, rawArgCount);
            }
            if (argCount<1 || argCount>9) {
                /* Error: Number of args must be between 1 and 9 inclusive */
                return createError(ErrorCode.TTEUC7, startTokenIndex, afterFirstSquare,
                        commandOrEnvironmentName, rawArgCount);
            }
            position = afterFirstSquare;
            skipOverWhitespace();
            if (workingDocument.charAt(position)=='[') {
                allowOptArgs = true;
                argCount--;
                position = findEndSquareBrackets(position);
                if (position==-1) {
                    /* Error: no ']' found! */
                    return createError(ErrorCode.TTEUC9, startTokenIndex, workingDocument.length());
                }
            }
        }
        
        /* Skip trailing whitespace */
        skipOverWhitespace();
        
        /* Fill in result and return null to indicate 'no error' */
        result.allowOptionalArgument = allowOptArgs;
        result.requiredArgumentCount = argCount;
        return null;
    }
    
    //-----------------------------------------
    // General Helpers
    
    /**
     * Returns the index after the next ']', handling any balanced braces appropriately.
     * Returns -1 if no corresponding ']' was found.
     */
    private int findEndSquareBrackets(final int openSquareBracketIndex) {
        /* NOTE: Curly brackets protect square brackets */
        boolean inEscape = false; /* Whether we are in the middle of a character escape */
        int index;
        int c;
        for (index=openSquareBracketIndex; index<workingDocument.length(); index++) {
            c = workingDocument.charAt(index);
            if (c==']') {
                index++;
                return index;
            }
            else if (!inEscape && c=='\\') {
                /* Start of an escape */
                inEscape = true;
            }
            else if (!inEscape && c=='{') {
                /* We have started {....}, which will protect any square brackets inside.
                 * Let's move over this.
                 */
                index = findEndCurlyBrackets(index) - 1; /* Subtracting 1 as it'll get added again when loop continues */
            }
            else if (inEscape) {
                /* End of an escape - stop escaping and go back to normal */
                inEscape = false;
            }
        }
        return -1;
    }
    
    /**
     * Returns the index <strong>after</strong> the next balanced '}', or -1 if no balance was
     * found.
     * 
     * @param openBraceIndex
     */
    private int findEndCurlyBrackets(final int openBraceIndex) {
        boolean inEscape = false; /* Whether we are in the middle of a character escape */
        int depth = 0; /* Current depth of brackets */
        int index;
        int c;
        for (index=openBraceIndex; index<workingDocument.length(); index++) {
            c = workingDocument.charAt(index);
            if (!inEscape && c=='\\') {
                /* Start of an escape */
                inEscape = true;
            }
            else if (inEscape) {
                /* End of an escape - stop escaping and go back to normal */
                inEscape = false;
            }
            else if (c=='{') {
                /* Found generic opener */
                depth++;
            }
            else if (c=='}') {
                /* Generic end of bracket */
                depth--;
                if (depth==0) {
                    /* Balanced delimiters */
                    index++;
                    return index;
                }
            }
        }
        return -1;
    }
    
    /**
     * Locates the first non-whitespace character in the document after the given index.
     * <p>
     * The result will be >= startIndex, and strictly greater if there was whitespace found.
     * 
     * @param startIndex
     */
    private int findNextNonWhitespace(final int startIndex) {
        int index = startIndex;
        while (index<workingDocument.length() && Character.isWhitespace(workingDocument.charAt(index))) {
            index++;
        }
        return index;
    }
    
    /**
     * Advances the current position past any whitespace.
     */
    private void skipOverWhitespace() {
        position = findNextNonWhitespace(position);
    }
    
    //-----------------------------------------
    // Error Handling
    
    private ErrorToken createError(final ErrorCode errorCode, final int errorStartIndex,
            final int errorEndIndex, final Object... arguments) throws SnuggleParseException {
        FrozenSlice errorSlice = workingDocument.freezeSlice(errorStartIndex, errorEndIndex);
        InputError error = new InputError(errorCode, errorSlice, arguments);
        sessionContext.registerError(error);
        return new ErrorToken(error, currentModeState!=null ? currentModeState.latexMode : LaTeXMode.PARAGRAPH);
    }
}