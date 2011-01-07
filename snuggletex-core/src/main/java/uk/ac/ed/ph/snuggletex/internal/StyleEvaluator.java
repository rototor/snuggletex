/* $Id$
 *
 * Copyright (c) 2010, The University of Edinburgh.
 * All Rights Reserved
 */
package uk.ac.ed.ph.snuggletex.internal;

import uk.ac.ed.ph.snuggletex.SnuggleLogicException;
import uk.ac.ed.ph.snuggletex.definitions.LaTeXMode;
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
        rootToken.setComputedStyle(makeRootStyle());
        visitSiblings(rootToken.getContents(), rootToken.getComputedStyle());
    }
    
    //-----------------------------------------
    
    private void visitSiblings(List<FlowToken> content, ComputedStyle scopeStyle)
            throws SnuggleParseException {
        ComputedStyle currentStyle = scopeStyle;
        for (FlowToken token : content) {
            /* Use current style */
            token.setComputedStyle(currentStyle);
            
            /* Note any style changes for subsequent tokens */
            if (token.hasInterpretationType(InterpretationType.STYLE_DECLARATION)) {
                /* This is a style change, so compute effective style for next tokens.
                 * 
                 * Note that we associate the OLD style to THIS token. The new style will
                 * be associated to successive tokens.
                 */
                currentStyle = mergeStyle(currentStyle, (StyleDeclarationInterpretation) token.getInterpretation(InterpretationType.STYLE_DECLARATION));
            }

            /* Descend as appropriate */
            switch (token.getType()) {
                case COMMAND:
                    visitCommand((CommandToken) token, currentStyle);
                    break;
                    
                case ENVIRONMENT:
                    visitEnvironment((EnvironmentToken) token, currentStyle);
                    break;
                    
                case BRACE_CONTAINER:
                    visitContainerContent(((BraceContainerToken) token).getBraceContent(), 
                            newStyleScope(currentStyle));
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
                    throw new SnuggleLogicException("Unhandled type " + token.getType());
            }
        }
    }
    
    private void visitCommand(CommandToken commandToken, ComputedStyle currentStyle) throws SnuggleParseException {
        /* Visit arguments and content */
        ArgumentContainerToken optArgument = commandToken.getOptionalArgument();
        if (optArgument!=null) {
            visitContainerContent(optArgument, currentStyle);
        }
        ArgumentContainerToken[] arguments = commandToken.getArguments();
        if (arguments!=null) {
            for (ArgumentContainerToken argument : arguments) {
                visitContainerContent(argument, currentStyle);
            }
        }
    }

    private void visitEnvironment(EnvironmentToken environmentToken, ComputedStyle currentStyle) throws SnuggleParseException {
        /* Visit arguments */
        ArgumentContainerToken optArgument = environmentToken.getOptionalArgument();
        if (optArgument!=null) {
            visitContainerContent(optArgument, currentStyle);
        }
        ArgumentContainerToken[] arguments = environmentToken.getArguments();
        if (arguments!=null) {
            for (ArgumentContainerToken argument : arguments) {
                visitContainerContent(argument, currentStyle);
            }
        }
        
        /* Now descend into content.
         * 
         * We use current style, unless we're transitioning into MATH mode, in which case the
         * font size is inherited but the font family reverts to the default.
         */
        ComputedStyle contentStyle = currentStyle;
        if (environmentToken.getEnvironment().getContentMode()==LaTeXMode.MATH && environmentToken.getLatexMode()!=LaTeXMode.MATH) {
            /* We're transitioning into MATH mode.
             * 
             * MATH Mode inherits the current font size, but reverts font family
             */
            contentStyle = new ComputedStyle(contentStyle, FontFamily.NORMAL, contentStyle.getFontSize());
        }
        visitContainerContent(environmentToken.getContent(), contentStyle);
    }
    
    private void visitContainerContent(ArgumentContainerToken parent, ComputedStyle scopeStyle) throws SnuggleParseException {
        parent.setComputedStyle(scopeStyle);
        visitSiblings(parent.getContents(), scopeStyle);
    }
    
    //-----------------------------------------
    
    private ComputedStyle makeRootStyle() {
        return new ComputedStyle(null, FontFamily.NORMAL, FontSize.NORMALSIZE);
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
}
