/* $Id$
 *
 * Copyright (c) 2010, The University of Edinburgh.
 * All Rights Reserved
 */
package uk.ac.ed.ph.snuggletex.dombuilding;

import uk.ac.ed.ph.snuggletex.definitions.TextFlowContext;
import uk.ac.ed.ph.snuggletex.internal.DOMBuilder;
import uk.ac.ed.ph.snuggletex.internal.SnuggleParseException;
import uk.ac.ed.ph.snuggletex.semantics.ComputedStyle;
import uk.ac.ed.ph.snuggletex.semantics.ComputedStyle.FontFamily;
import uk.ac.ed.ph.snuggletex.semantics.ComputedStyle.FontSize;
import uk.ac.ed.ph.snuggletex.tokens.ArgumentContainerToken;
import uk.ac.ed.ph.snuggletex.tokens.EnvironmentToken;
import uk.ac.ed.ph.snuggletex.tokens.FlowToken;

import org.w3c.dom.Element;

/**
 * Handles the internal <![CDATA[<style>]]> environment delimiting a branch of content to be
 * rendered in a different style.
 *
 * @author  David McKain
 * @version $Revision$
 */
public final class StyleHandler implements EnvironmentHandler {
    
    public void handleEnvironment(DOMBuilder builder, Element parentElement, EnvironmentToken token)
            throws SnuggleParseException {
        Element builderElement = parentElement; /* Default if we can't do any sensible styling */
        ArgumentContainerToken contentContainerToken = token.getContent();
        ComputedStyle newStyle = token.getComputedStyle();
        if (builder.isBuildingMathMLIsland()) {
            /* We're doing MathML. We create an <mstyle/> element, but only if we can reasonably
             * handle this style.
             * 
             * NB: Currently we only support setting font family styles.
             * 
             * Note: Even though there is no \mathsc{...}, we can legally end up here if doing
             * something like \mbox{\sc ....} so we'll ignore unsupported stylings, rather than
             * failing.
             * 
             * Regression Note: If the content is truly empty, we'll generate an empty <mrow/>
             * instead of an empty <mstyle/>
             */
            String mathVariantName = newStyle.getFontFamily().getTargetMathMLMathVariantName();
            if (mathVariantName!=null && !contentContainerToken.getContents().isEmpty()) {
                builderElement = builder.appendMathMLElement(builderElement, "mstyle");
                builderElement.setAttribute("mathvariant", mathVariantName);
            }
            else {
                builderElement = builder.appendMathMLElement(builderElement, "mrow");
            }
        }
        else {
            /* We're doing XHTML */
            
            /* Is the content block or inline? */
            boolean hasBlockContent = false;
            for (FlowToken contentToken : contentContainerToken) {
                if (contentToken.getTextFlowContext()==TextFlowContext.START_NEW_XHTML_BLOCK) {
                    hasBlockContent = true;
                }
            }
            
            ComputedStyle currentTextStyle = builder.getCurrentTextStyle();
            
            /* Adjust font size if required */
            FontSize newFontSize = newStyle.getFontSize();
            if (newFontSize!=currentTextStyle.getFontSize()) {
                builderElement = builder.appendXHTMLElement(builderElement, hasBlockContent ? "div" : "span");
                builder.applyCSSStyle(builderElement, newFontSize.getTargetCSSClassName());
            }
            
            /* Adjust font family if required */
            FontFamily newFontFamily = newStyle.getFontFamily();
            if (newFontFamily!=currentTextStyle.getFontFamily()) {
                String elementName = hasBlockContent ? newFontFamily.getTargetBlockXHTMLElementName() : newFontFamily.getTargetInlineXHTMLElementName();
                String cssClassName = hasBlockContent ? newFontFamily.getTargetBlockCSSClassName() : newFontFamily.getTargetInlineCSSClassName();
                builderElement = builder.appendXHTMLElement(builderElement, elementName);
                if (cssClassName!=null) {
                    builder.applyCSSStyle(builderElement, cssClassName);
                }
            }
        }
        /* Descend as normal */
        builder.pushTextStyle(newStyle);
        builder.handleTokens(builderElement, contentContainerToken, false);
        builder.popTextStyle();
    }
    
//    public void handleEnvironmentOLD(DOMBuilder builder, Element parentElement, EnvironmentToken token)
//    throws SnuggleParseException {
//Element result = parentElement; /* Default if we can't do any sensible styling */
//ArgumentContainerToken contentContainerToken = token.getContent();
//ComputedStyle computedStyle = token.getComputedStyle();
//if (builder.isBuildingMathMLIsland()) {
////    /* We're doing MathML. We create an <mstyle/> element, but only if we can reasonably
////     * handle this style.
////     * 
////     * Note: Even though there is no \mathsc{...}, we can legally end up here if doing
////     * something like \mbox{\sc ....} so we'll ignore unsupported stylings, rather than
////     * failing.
////     * 
////     * Regression Note: If the content is truly empty, we'll generate an empty <mrow/>
////     * instead of an empty <mstyle/>
////     */
////    String mathVariant = interpretation.getTargetMathMLMathVariantName();
////    if (mathVariant!=null && !contentContainerToken.getContents().isEmpty()) {
////        result = builder.appendMathMLElement(parentElement, "mstyle");
////        result.setAttribute("mathvariant", mathVariant);
////    }
////    else {
////        result = builder.appendMathMLElement(parentElement, "mrow");
////    }
//}
//else {
//    /* We're doing XHTML */
//    boolean hasBlockContent = false;
//    for (FlowToken contentToken : contentContainerToken) {
//        if (contentToken.getTextFlowContext()==TextFlowContext.START_NEW_XHTML_BLOCK) {
//            hasBlockContent = true;
//        }
//    }
//    /* We'll just to sizing for the time being */
//    FontSize fontSize = computedStyle.getFontSize();
//    if (hasBlockContent) {
//        result = builder.appendXHTMLElement(parentElement, "div");
//        if (interpretation.getTargetBlockCSSClassName()!=null) {
//            builder.applyCSSStyle(result, interpretation.getTargetBlockCSSClassName());
//        }
//    }
//    else if (!hasBlockContent && interpretation.getTargetInlineXHTMLElementName()!=null) {
//        result = builder.appendXHTMLElement(parentElement, interpretation.getTargetInlineXHTMLElementName());
//        if (interpretation.getTargetInlineCSSClassName()!=null) {
//            builder.applyCSSStyle(result, interpretation.getTargetInlineCSSClassName());
//        }
//    }
//}
///* Descend as normal */
//builder.handleTokens(result, contentContainerToken, false);
//}
}
