/* $Id$
 *
 * Copyright (c) 2010, The University of Edinburgh.
 * All Rights Reserved
 */
package uk.ac.ed.ph.snuggletex.internal;

import uk.ac.ed.ph.snuggletex.SnuggleLogicException;
import uk.ac.ed.ph.snuggletex.definitions.BuiltinEnvironment;
import uk.ac.ed.ph.snuggletex.definitions.CorePackageDefinitions;
import uk.ac.ed.ph.snuggletex.semantics.ComputedStyle;
import uk.ac.ed.ph.snuggletex.semantics.ComputedStyle.FontFamily;
import uk.ac.ed.ph.snuggletex.semantics.ComputedStyle.FontSize;
import uk.ac.ed.ph.snuggletex.semantics.InterpretationType;
import uk.ac.ed.ph.snuggletex.semantics.StyleDeclarationInterpretation;
import uk.ac.ed.ph.snuggletex.tokens.ArgumentContainerToken;
import uk.ac.ed.ph.snuggletex.tokens.BraceContainerToken;
import uk.ac.ed.ph.snuggletex.tokens.CommandToken;
import uk.ac.ed.ph.snuggletex.tokens.EnvironmentToken;
import uk.ac.ed.ph.snuggletex.tokens.FlowToken;
import uk.ac.ed.ph.snuggletex.tokens.RootToken;
import uk.ac.ed.ph.snuggletex.tokens.Token;
import uk.ac.ed.ph.snuggletex.tokens.TokenType;

import java.util.List;

/**
 * This calculates the {@link ComputedStyle} for each raw parsed {@link Token}. It is
 * used directly after {@link LaTeXTokeniser} but before the {@link TokenFixer}.
 * 
 * @author  David McKain
 * @version $Revision$
 */
public final class StyleEvaluator {
    
    private final SessionContext sessionContext;
    
    public StyleEvaluator(final SessionContext sessionContext) {
        this.sessionContext = sessionContext;
    }
    
    //-----------------------------------------

    public void evaluateStyles(RootToken rootToken) throws SnuggleParseException {
        visitSiblings(rootToken.getContents(), makeRootStyle());
    }
    
    //-----------------------------------------
    
    private void visitSiblings(List<FlowToken> content, ComputedStyle scopeStyle)
            throws SnuggleParseException {
        /* Compute resultant style for each token */
        computeStyles(content, scopeStyle);
        
        /* Then descend into each token */
        for (FlowToken token : content) {
            visitToken(token);
        }
    }
    
    
    private void visitToken(Token startToken) throws SnuggleParseException {
        /* Dive into containers */
        switch (startToken.getType()) {
            case ARGUMENT_CONTAINER:
                visitContainerContent((ArgumentContainerToken) startToken, newStyleScope(startToken.getComputedStyle()));
                break;
                
            case COMMAND:
                visitCommand((CommandToken) startToken);
                break;
                
            case ENVIRONMENT:
                visitEnvironment((EnvironmentToken) startToken);
                break;
                
            case BRACE_CONTAINER:
                visitContainerContent(((BraceContainerToken) startToken).getBraceContent(), 
                        newStyleScope(startToken.getComputedStyle()));
                break;
                
            case TEXT_MODE_TEXT:
            case VERBATIM_MODE_TEXT:
            case LR_MODE_NEW_PARAGRAPH:
            case MATH_NUMBER:
            case MATH_CHARACTER:
            case ERROR:
            case TAB_CHARACTER:
            case NEW_PARAGRAPH:
                /* Nothing to do here */
                break;
                
            default:
                throw new SnuggleLogicException("Unhandled type " + startToken.getType());
        }
    }
    
    private void visitContainerContent(ArgumentContainerToken parent, ComputedStyle scopeStyle) throws SnuggleParseException {
        visitSiblings(parent.getContents(), scopeStyle);
    }
    
    private void visitCommand(CommandToken commandToken) throws SnuggleParseException {
        /* Visit arguments and content */
        ArgumentContainerToken optArgument = commandToken.getOptionalArgument();
        if (optArgument!=null) {
            visitContainerContent(optArgument, commandToken.getComputedStyle());
        }
        ArgumentContainerToken[] arguments = commandToken.getArguments();
        if (arguments!=null) {
            for (ArgumentContainerToken argument : arguments) {
                visitContainerContent(argument, commandToken.getComputedStyle());
            }
        }
    }

    private void visitEnvironment(EnvironmentToken environmentToken) throws SnuggleParseException {
        /* Visit arguments (usually)...
         * 
         * We don't drill into the arguments of ENV_BRACKETED as that will end up with an infinite
         * loop of parenthesis nesting!
         */
        BuiltinEnvironment environment = environmentToken.getEnvironment();
        if (environment!=CorePackageDefinitions.ENV_BRACKETED) {
            ArgumentContainerToken optArgument = environmentToken.getOptionalArgument();
            if (optArgument!=null) {
                visitContainerContent(optArgument, environmentToken.getComputedStyle());
            }
            ArgumentContainerToken[] arguments = environmentToken.getArguments();
            if (arguments!=null) {
                for (ArgumentContainerToken argument : arguments) {
                    visitContainerContent(argument, environmentToken.getComputedStyle());
                }
            }
        }
        
        /* Visit content */
        visitContainerContent(environmentToken.getContent(), environmentToken.getComputedStyle());
    }
    
    //-----------------------------------------
    // Style computation (happens before mainstream fixing)
    
    private ComputedStyle makeRootStyle() {
        return new ComputedStyle(null, FontFamily.RM, FontSize.NORMALSIZE);
    }
    
    private ComputedStyle newStyleScope(ComputedStyle currentStyle) {
        if (currentStyle==null) {
            throw new SnuggleLogicException("currentStyle should not be null");
        }
        return new ComputedStyle(currentStyle, currentStyle.getFontFamily(), currentStyle.getFontSize());
    }
    
    private ComputedStyle mergeStyle(ComputedStyle currentStyle, StyleDeclarationInterpretation interpretation) {
        FontFamily newFontFamily = interpretation.getFontFamily();
        FontSize newFontSize = interpretation.getFontSize();
        return new ComputedStyle(currentStyle, newFontFamily!=null ? newFontFamily : currentStyle.getFontFamily(),
                newFontSize!=null ?  newFontSize : currentStyle.getFontSize());
    }
    
    private void computeStyles(List<FlowToken> tokens, ComputedStyle scopeStyle) {
        FlowToken token;
        ComputedStyle currentStyle = scopeStyle;
        for (int i=0; i<tokens.size(); i++) { /* (This does fix-in-place as required) */
            token = tokens.get(i);
            if (token.hasInterpretationType(InterpretationType.STYLE_DECLARATION)) {
                /* Compute effective style */
                currentStyle = mergeStyle(currentStyle, (StyleDeclarationInterpretation) token.getInterpretation(InterpretationType.STYLE_DECLARATION));
//                if (token.getType()==TokenType.COMMAND && ((CommandToken) token).getCommand().getArgumentCount()==0) {
//                    /* Old style change commands like \bf and \it get removed from the tree here */
//                    tokens.remove(i--);
//                    continue;
//                }
            }
            token.setComputedStyle(currentStyle);
        }
    }
}
