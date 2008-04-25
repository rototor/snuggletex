/* $Id: InputError.java,v 1.8 2008/04/23 11:23:36 dmckain Exp $
 *
 * Copyright 2008 University of Edinburgh.
 * All Rights Reserved
 */
package uk.ac.ed.ph.snuggletex;

import uk.ac.ed.ph.aardvark.commons.util.ObjectUtilities;
import uk.ac.ed.ph.snuggletex.conversion.FrozenSlice;

import java.io.Serializable;

/**
 * Encapsulates an error in the LaTeX input, providing information about what the error is and
 * where it occurred.
 * 
 * @author  David McKain
 * @version $Revision: 1.8 $
 */
public final class InputError implements Serializable {
    
    private static final long serialVersionUID = 437416586924703932L;
    
    /** Slice the error occurred in. This may be null in certain circumstances */
    private final FrozenSlice slice;
    
    /** Error code */
    private final ErrorCode errorCode;
    
    /** 
     * Any additional arguments about the error. These are passed to {@link MessageFormatter}
     * when formatting errors in a readable form.
     */
    private final Object[] arguments;
    
    public InputError(final ErrorCode errorCode, final FrozenSlice slice, final Object... arguments) {
        this.slice = slice;
        this.errorCode = errorCode;
        this.arguments = arguments;
    }
    
    public ErrorCode getErrorCode() {
        return errorCode;
    }
    
    public FrozenSlice getSlice() {
        return slice;
    }

    public Object[] getArguments() {
        return arguments;
    }

    @Override
    public String toString() {
        return ObjectUtilities.beanToString(this);
    }
}