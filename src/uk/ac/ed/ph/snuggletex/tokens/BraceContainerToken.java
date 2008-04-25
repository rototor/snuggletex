/* $Id$
 *
 * Copyright 2008 University of Edinburgh.
 * All Rights Reserved
 */
package uk.ac.ed.ph.snuggletex.tokens;

import uk.ac.ed.ph.snuggletex.conversion.FrozenSlice;
import uk.ac.ed.ph.snuggletex.definitions.LaTeXMode;

/**
 * FIXME: Document this type!
 *
 * @author  David McKain
 * @version $Revision$
 */
public final class BraceContainerToken extends FlowToken {
    
    private final ArgumentContainerToken braceContent;

    public BraceContainerToken(FrozenSlice slice, LaTeXMode latexMode, ArgumentContainerToken braceContent) {
        super(slice, TokenType.BRACE_CONTAINER, latexMode, null);
        this.braceContent = braceContent;
    }

    public ArgumentContainerToken getBraceContent() {
        return braceContent;
    }
}
