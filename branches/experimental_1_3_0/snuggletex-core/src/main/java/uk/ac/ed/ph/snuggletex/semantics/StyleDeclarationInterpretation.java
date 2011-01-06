/* $Id:StyleDeclarationInterpretation.java 179 2008-08-01 13:41:24Z davemckain $
 *
 * Copyright (c) 2010, The University of Edinburgh.
 * All Rights Reserved
 */
package uk.ac.ed.ph.snuggletex.semantics;

import uk.ac.ed.ph.snuggletex.semantics.ComputedStyle.FontFamily;
import uk.ac.ed.ph.snuggletex.semantics.ComputedStyle.FontSize;

/**
 * Represents styled text in either MATH and/or TEXT Modes.
 * 
 * @author  David McKain
 * @version $Revision:179 $
 */
public enum StyleDeclarationInterpretation implements TextInterpretation {

    BF(FontFamily.BF, null, "div", "bf", "b",    null, "bold"),
    RM(FontFamily.RM, null, "div", "rm", "span", "rm", "normal"),
    EM(FontFamily.EM, null, "div", "em", "em",   null, "italic"),
    IT(FontFamily.IT, null, "div", "it", "i",    null, "italic"),
    TT(FontFamily.TT, null, "div", "tt", "tt",   null, "monospace"),
    SC(FontFamily.SC, null, "div", "sc", "span", "sc", null),
    SL(FontFamily.SL, null, "div", "sl", "span", "sl", null),
    SF(FontFamily.SF, null, "div", "sf", "span", "sf", "sans-serif"),

    TINY(null, FontSize.TINY, "div", "tiny", "span", "tiny", null),
    SCRIPTSIZE(null, FontSize.SCRIPTSIZE, "div", "scriptsize", "span", "scriptsize", null),
    FOOTNOTESIZE(null, FontSize.FOOTNOTESIZE, "div", "footnotesize", "span", "footnotesize", null),
    SMALL(null, FontSize.SMALL, "div", "small", "span", "small", null),
    NORMALSIZE(null, FontSize.NORMALSIZE, "div", "normalsize", "span", "normalsize", null),
    LARGE(null, FontSize.LARGE, "div", "large", "span", "large", null),
    LARGE_2(null, FontSize.LARGE_2, "div", "large2", "span", "large2", null),
    LARGE_3(null, FontSize.LARGE_3, "div", "large3", "span", "large3", null),
    HUGE(null, FontSize.HUGE, "div", "huge", "span", "huge", null),
    HUGE_2(null, FontSize.HUGE_2, "div", "huge2", "span", "huge2", null),

    UNDERLINE(null, null, "div", "underline", "span", "underline", null),
    
    ;
    
    private final FontFamily fontFamily;
    
    private final FontSize fontSize;
    
    /** Name of resulting XHTML block element name */
    private final String targetBlockXHTMLElementName;
    
    /** Name of resulting CSS class for XHTML block elements */
    private final String targetBlockCSSClassName;
    
    /** Name of resulting XHTML inline element name */
    private final String targetInlineXHTMLElementName;
    
    /** Name of resulting CSS class for XHTML inline elements */
    private final String targetInlineCSSClassName;
    
    /** 
     * Name of 'variant' attribute in resulting MathML <mstyle/> element, if supported, or null
     * if this style cannot be used in Math mode.
     */
    private final String targetMathMLMathVariantName;
    
    private StyleDeclarationInterpretation(final FontFamily fontFamily, final FontSize fontSize,
            final String targetBlockXHTMLElementName,
            final String targetBlockCSSClassName, final String targetInlineXHTMLElementName,
            final String targetInlineCSSClassName, final String targetMathMLMathVariantName) {
        this.fontFamily = fontFamily;
        this.fontSize = fontSize;
        this.targetBlockXHTMLElementName = targetBlockXHTMLElementName;
        this.targetBlockCSSClassName = targetBlockCSSClassName;
        this.targetInlineXHTMLElementName = targetInlineXHTMLElementName;
        this.targetInlineCSSClassName = targetInlineCSSClassName;
        this.targetMathMLMathVariantName = targetMathMLMathVariantName;
    }
    
    public FontFamily getFontFamily() {
        return fontFamily;
    }

    public FontSize getFontSize() {
        return fontSize;
    }

    public String getTargetBlockXHTMLElementName() {
        return targetBlockXHTMLElementName;
    }
    
    public String getTargetBlockCSSClassName() {
        return targetBlockCSSClassName;
    }


    public String getTargetInlineXHTMLElementName() {
        return targetInlineXHTMLElementName;
    }
    
    public String getTargetInlineCSSClassName() {
        return targetInlineCSSClassName;
    }

    public String getTargetMathMLMathVariantName() {
        return targetMathMLMathVariantName;
    }

    public InterpretationType getType() {
        return InterpretationType.STYLE_DECLARATION;
    }
}
