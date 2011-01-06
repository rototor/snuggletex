/* $Id$
 *
 * Copyright (c) 2010, The University of Edinburgh.
 * All Rights Reserved
 */
package uk.ac.ed.ph.snuggletex.semantics;

import uk.ac.ed.ph.snuggletex.internal.util.DumpMode;
import uk.ac.ed.ph.snuggletex.internal.util.ObjectDumperOptions;

/**
 * FIXME: Document this type!
 *
 * @author  David McKain
 * @version $Revision$
 */
@ObjectDumperOptions(DumpMode.TO_STRING)
public final class ComputedStyle {
    
    public static enum FontFamily {
        BF,
        RM,
        EM,
        IT,
        TT,
        SC,
        SL,
        SF,
    }
    
    public static enum FontSize {
        TINY,
        SCRIPTSIZE,
        FOOTNOTESIZE,
        SMALL,
        NORMALSIZE,
        LARGE,
        LARGE_2,
        LARGE_3,
        HUGE,
        HUGE_2,
    }
    
    private final ComputedStyle parentStyle;
    private final FontFamily fontFamily;
    private final FontSize fontSize;
    
    public ComputedStyle(ComputedStyle parentStyle, FontFamily fontFamily, FontSize fontSize) {
        this.parentStyle = parentStyle;
        this.fontFamily = fontFamily;
        this.fontSize = fontSize;
    }

    public ComputedStyle getParentStyle() {
        return parentStyle;
    }
    
    public FontFamily getFontFamily() {
        return fontFamily;
    }
    
    public FontSize getFontSize() {
        return fontSize;
    }
    
    
    @Override
    public String toString() {
        return getClass().getSimpleName()
            + "@" + Integer.toString(hashCode(), 16)
            + "(fontFamily=" + fontFamily
            + ",fontSize=" + fontSize
            + ",parentStyle=" + (parentStyle!=null ? Integer.toString(parentStyle.hashCode(), 16) : null)
            + ")";
    }

}
