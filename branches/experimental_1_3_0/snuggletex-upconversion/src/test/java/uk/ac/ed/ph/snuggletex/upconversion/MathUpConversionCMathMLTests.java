/* $Id:MathTests.java 179 2008-08-01 13:41:24Z davemckain $
 *
 * Copyright (c) 2008-2011, The University of Edinburgh.
 * All Rights Reserved
 */
package uk.ac.ed.ph.snuggletex.upconversion;

import uk.ac.ed.ph.snuggletex.MathTests;
import uk.ac.ed.ph.snuggletex.SnuggleTeXCaller.DOMFixupCallback;
import uk.ac.ed.ph.snuggletex.TestUtilities;
import uk.ac.ed.ph.snuggletex.definitions.W3CConstants;
import uk.ac.ed.ph.snuggletex.testutil.TestFileHelper;
import uk.ac.ed.ph.snuggletex.utilities.MathMLUtilities;

import java.util.Collection;

import junit.framework.Assert;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Same idea as {@link MathTests}, but tests the initial up-conversion to more
 * semantic Presentation MathML.
 * 
 * @author  David McKain
 * @version $Revision:179 $
 */
@RunWith(Parameterized.class)
public class MathUpConversionCMathMLTests extends AbstractGoodUpConversionXMLTest implements DOMFixupCallback {
    
    public static final String TEST_RESOURCE_NAME = "math-upconversion-cmathml-tests.txt";
    
    @Parameters
    public static Collection<String[]> data() throws Exception {
        return TestFileHelper.readAndParseSingleLineInputTestResource(TEST_RESOURCE_NAME);
    }
    
    public MathUpConversionCMathMLTests(final String inputLaTeXMaths, final String expectedMathMLContent) {
        super(inputLaTeXMaths, expectedMathMLContent);
    }
    
    @Test
    public void runTest() throws Throwable {
        /* Set up up-converter so that it only generates fixed up Presentation MathML */
        UpConversionOptions upConversionOptions = new UpConversionOptions();
        upConversionOptions.setSpecifiedOption(UpConversionOptionDefinitions.DO_CONTENT_MATHML_NAME, "true");
        upConversionOptions.setSpecifiedOption(UpConversionOptionDefinitions.DO_MAXIMA_NAME, "false");
        super.runTest(upConversionOptions);
    }
    
    /**
     * Overridden to tear the resulting MathML document apart and just leave the Content MathML.
     */
    @Override
    public void fixupDOM(Document document) throws Throwable {
        /* First extract the MathML element */
        TestUtilities.extractMathElement(document);
        
        /* Extract CMathML annotation if present, which should be a single element */
        Element mathML = document.getDocumentElement();
        NodeList cmathMLList = MathMLUtilities.extractAnnotationXML(mathML, MathMLUpConverter.CONTENT_MATHML_ANNOTATION_NAME);
        if (cmathMLList!=null) {
            Assert.assertEquals(1, cmathMLList.getLength());
            Node cmathML = cmathMLList.item(0);

            Assert.assertEquals(Node.ELEMENT_NODE, cmathML.getNodeType());
            Assert.assertEquals(W3CConstants.MATHML_NAMESPACE, cmathML.getNamespaceURI());
            
            mathML.removeChild(mathML.getFirstChild()); /* Removes <semantics/> */
            mathML.appendChild(cmathML); /* Moves CMathML element to child of <math/> */
        }
        else {
            /* Just leave alone, this will be checked later */
        }
    }
}
