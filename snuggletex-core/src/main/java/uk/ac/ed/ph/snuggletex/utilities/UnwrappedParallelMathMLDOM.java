/* $Id$
 *
 * Copyright (c) 2008-2011, The University of Edinburgh.
 * All Rights Reserved
 */
package uk.ac.ed.ph.snuggletex.utilities;

import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.util.HashMap;
import java.util.Map;

/**
 * Trivial wrapper Object that encapsulates the results of
 * {@link MathMLUtilities#unwrapParallelMathMLDOM(Element)}.
 *
 * @author  David McKain
 * @version $Revision$
 */
public final class UnwrappedParallelMathMLDOM {
    
    /** Containing <math/> element */
    private Element mathElement;
    
    /** First branch of the <semantics/> element */
    private Element firstBranch;
    
    /** Map of all <annotation/> contents, keyed on encoding attribute */
    private final Map<String, String> textAnnotations;
    
    /** Map of all <annotation-xml/> contents, keyed on encoding attribute */
    private final Map<String, NodeList> xmlAnnotations;
    
    public UnwrappedParallelMathMLDOM() {
        this.textAnnotations = new HashMap<String, String>();
        this.xmlAnnotations = new HashMap<String, NodeList>();
    }
    
    /** Returns the containing &lt;math/&gt; element */
    public Element getMathElement() {
        return mathElement;
    }
    
    /** Sets the containing &lt;math/&gt; element */
    public void setMathElement(Element mathElement) {
        this.mathElement = mathElement;
    }

    /** Returns the first branch of the top &lt;semantics/&gt; element. */
    public Element getFirstBranch() {
        return firstBranch;
    }
    
    /** Sets the first branch of the top &lt;semantics/&gt; element. */
    public void setFirstBranch(Element firstBranch) {
        this.firstBranch = firstBranch;
    }

    /**
     * Returns a {@link Map} of &lt;annotation/&gt; elements, keyed on the "encoding" attribute with
     * the text content as values.
     */
    public Map<String, String> getTextAnnotations() {
        return textAnnotations;
    }

    /**
     * Returns a {@link Map} of &lt;annotation-xml/&gt; elements, keyed on the "encoding" attribute with
     * the {@link NodeList} content as values.
     */
    public Map<String, NodeList> getXmlAnnotations() {
        return xmlAnnotations;
    }
}
