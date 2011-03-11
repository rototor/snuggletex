/* $Id:MathTests.java 179 2008-08-01 13:41:24Z davemckain $
 *
 * Copyright (c) 2008-2011, The University of Edinburgh.
 * All Rights Reserved
 */
package uk.ac.ed.ph.snuggletex.upconversion;

import uk.ac.ed.ph.snuggletex.MathTests;
import uk.ac.ed.ph.snuggletex.SnuggleEngine;
import uk.ac.ed.ph.snuggletex.SnuggleTeXTestDriver;
import uk.ac.ed.ph.snuggletex.SnuggleTeXTestDriver.DOMFixupCallback;
import uk.ac.ed.ph.snuggletex.SnuggleTeXTestDriver.DOMVerifyCallback;
import uk.ac.ed.ph.snuggletex.TestUtilities;
import uk.ac.ed.ph.snuggletex.definitions.W3CConstants;
import uk.ac.ed.ph.snuggletex.upconversion.internal.UpConversionPackageDefinitions;

import java.util.List;

import junit.framework.Assert;

import org.w3c.dom.Document;

/**
 * Base class for up-conversion tests. This is pretty much the same as {@link MathTests}
 * but only wraps the input in "$...$" delimiters if they don't already end with one of these.
 * This allows assumptions to be set up in advance.
 * 
 * @author  David McKain
 * @version $Revision:179 $
 */
public abstract class UpConversionXMLTestBase implements DOMFixupCallback, DOMVerifyCallback {

    private final String inputLaTeX;
    private final String expectedOutput;
    private final String expectedMathML;
    
    public UpConversionXMLTestBase(final String inputFragment, final String expectedMathMLContent) {
        this.inputLaTeX = inputFragment.endsWith("$") ? inputFragment : "$" + inputFragment + "$";
        this.expectedOutput = expectedMathMLContent;
        this.expectedMathML = "<math xmlns='" + W3CConstants.MATHML_NAMESPACE + "'>"
                + expectedMathMLContent.replaceAll("(?m)^\\s+", "").replaceAll("(?m)\\s+$", "").replace("\n", "")
                + "</math>";
    }
    
    public void runTest(UpConversionOptions upConversionOptions) throws Throwable {
        SnuggleEngine engine = new SnuggleEngine();
        engine.addPackage(UpConversionPackageDefinitions.getPackage());
        
        SnuggleTeXTestDriver driver = new SnuggleTeXTestDriver(engine);
        driver.setShowTokensOnFailure(false);
        driver.setDomFixupCallback(this);
        driver.setDomVerifyCallback(this);
        driver.setDomPostProcessor(new UpConvertingPostProcessor(upConversionOptions));
        
        driver.run(inputLaTeX, expectedOutput);
    }
    
    public void fixupDOM(Document document) throws Throwable {
        TestUtilities.extractMathElement(document);
    }
    
    public void verifyDOM(Document document) throws Throwable {
        List<UpConversionFailure> upConversionFailures = UpConversionUtilities.extractUpConversionFailures(document);
        if (upConversionFailures.isEmpty()) {
            /* Check XML verifies against what we expect */
            TestUtilities.verifyXML(expectedMathML, document);
        }
        else {
            /* Make sure we get the correct error code(s) */
            String result = expectedOutput;
            if (result.length()==0 || result.charAt(0)!='!') {
                Assert.fail("Did not expect up-conversion errors!");
            }
            String[] expectedErrorCodes = result.substring(1).split(",\\s*");
            Assert.assertEquals(expectedErrorCodes.length, upConversionFailures.size());
            for (int i=0; i<expectedErrorCodes.length; i++) {
                Assert.assertEquals(expectedErrorCodes[i], upConversionFailures.get(i).getErrorCode().toString());
            }
        }
    }
}
