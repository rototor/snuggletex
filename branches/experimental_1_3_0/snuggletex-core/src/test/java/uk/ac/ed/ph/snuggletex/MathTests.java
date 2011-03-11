/* $Id:MathTests.java 179 2008-08-01 13:41:24Z davemckain $
 *
 * Copyright (c) 2008-2011, The University of Edinburgh.
 * All Rights Reserved
 */
package uk.ac.ed.ph.snuggletex;

import uk.ac.ed.ph.snuggletex.SnuggleTeXCaller.DOMFixupCallback;
import uk.ac.ed.ph.snuggletex.SnuggleTeXCaller.DOMVerifyCallback;
import uk.ac.ed.ph.snuggletex.definitions.W3CConstants;
import uk.ac.ed.ph.snuggletex.testutil.TestFileHelper;

import java.util.Collection;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.w3c.dom.Document;

/**
 * Set of simple maths-based tests that read their data in from <tt>{@link #TEST_RESOURCE_NAME}</tt>.
 * The input is a single line of LaTeX which will be put into <tt>$...$</tt> and parsed
 * then compared with the given multi-line XML. The enclosing <tt>math</tt> element is
 * automatically added to the XML for convenience. See the sample file for examples.
 * <p>
 * As of SnuggleTeX 1.3.0, the resulting XML is validated against the MathML 3.0 RELAX NG schema.
 * 
 * @author  David McKain
 * @version $Revision:179 $
 */
@RunWith(Parameterized.class)
public class MathTests implements DOMFixupCallback, DOMVerifyCallback {
    
    public static final String TEST_RESOURCE_NAME = "math-tests.txt";
    
    @Parameters
    public static Collection<String[]> data() throws Exception {
        return TestFileHelper.readAndParseSingleLineInputTestResource(TEST_RESOURCE_NAME);
    }
    
    private final String inputLaTeXMaths;
    private final String expectedMathML;
    
    public MathTests(final String inputLaTeXMaths, final String expectedMathMLContent) {
        this.inputLaTeXMaths = inputLaTeXMaths;
        this.expectedMathML = "<math xmlns='" + W3CConstants.MATHML_NAMESPACE + "'>"
            + expectedMathMLContent.replaceAll("(?m)^\\s+", "").replaceAll("(?m)\\s+$", "").replace("\n", "")
            + "</math>";
    }
    
    @Test
    public void runTest() throws Throwable {
        SnuggleEngine engine = new SnuggleEngine();
        
        SnuggleTeXCaller caller = new SnuggleTeXCaller(engine);
        caller.setShowTokensOnFailure(true);
        caller.setDomFixupCallback(this);
        caller.setDomVerifyCallback(this);
        
        String inputLaTeX = "$" + inputLaTeXMaths + "$";
        caller.run(inputLaTeX, expectedMathML);
    }
    
    public void fixupDOM(Document document) throws Throwable {
        TestUtilities.extractMathElement(document);
    }
    
    public void verifyDOM(Document document) throws Throwable {
        /* Check XML verifies against what we expect */
        TestUtilities.verifyXML(expectedMathML, document);
        
        /* Additionally do RELAX-NG validation on the MathML */
        TestUtilities.assertMathMLValid(document);
    }
}
