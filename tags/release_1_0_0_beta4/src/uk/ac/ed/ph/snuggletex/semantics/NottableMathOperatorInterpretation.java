/* $Id: MathRelationOperatorInterpretation.java 12 2008-05-06 21:17:06Z davemckain $
 *
 * Copyright 2008 University of Edinburgh.
 * All Rights Reserved
 */
package uk.ac.ed.ph.snuggletex.semantics;

import uk.ac.ed.ph.aardvark.commons.util.ObjectUtilities;

/**
 * Represents a mathematical relation operator that can be applied to the <tt>\\not</tt>
 * token to result in the corresponding inverse operator.
 * 
 * @author  David McKain
 * @version $Revision: 12 $
 */
public final class NottableMathOperatorInterpretation implements MathOperatorInterpretation {
    
    private final MathMLOperator operator;
    private final MathMLOperator notOperator;
    
    public NottableMathOperatorInterpretation(final MathMLOperator operator, final MathMLOperator notOperator) {
        this.operator = operator;
        this.notOperator = notOperator;
    }
    
    public MathMLOperator getOperator() {
        return operator;
    }

    public MathMLOperator getNotOperator() {
        return notOperator;
    }
    
    public InterpretationType getType() {
        return InterpretationType.MATH_RELATION_OPERATOR;
    }
    
    @Override
    public String toString() {
        return ObjectUtilities.beanToString(this);
    }
}