/* $Id:MathTests.java 179 2008-08-01 13:41:24Z davemckain $
 *
 * Copyright 2009 University of Edinburgh.
 * All Rights Reserved
 */
package uk.ac.ed.ph.snuggletex.upconversion;

import uk.ac.ed.ph.snuggletex.DOMOutputOptions;
import uk.ac.ed.ph.snuggletex.MathTests;
import uk.ac.ed.ph.snuggletex.definitions.Globals;
import uk.ac.ed.ph.snuggletex.testutil.TestFileHelper;
import uk.ac.ed.ph.snuggletex.utilities.MathMLUtilities;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.transform.TransformerFactory;

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
public class MathUpConversionCMathMLTests extends AbstractGoodUpConversionTest {
    
    public static final String TEST_RESOURCE_NAME = "math-upconversion-cmathml-tests.txt";
    
    @Parameters
    public static Collection<String[]> data() throws Exception {
        return TestFileHelper.readAndParseSingleLineInputTestResource(TEST_RESOURCE_NAME);
    }
    
    private final UpConvertingPostProcessor upconverter;
    
    public MathUpConversionCMathMLTests(final String inputLaTeXMaths, final String expectedMathMLContent) {
        super(inputLaTeXMaths, expectedMathMLContent);
        
        /* Set up up-converter */
        Map<String, Object> upconversionParameterMap = new HashMap<String, Object>();
        upconversionParameterMap.put(UpConversionParameters.DO_CONTENT_MATHML, Boolean.TRUE);
        upconversionParameterMap.put(UpConversionParameters.DO_MAXIMA, Boolean.FALSE);
        upconverter = new UpConvertingPostProcessor(upconversionParameterMap);
    }

    /**
     * Overridden to tear the resulting MathML document apart and just leave the Content MathML.
     */
    @Override
    protected void fixupDocument(Document document) {
        /* Let superclass make a MathML document */
        super.fixupDocument(document);
        
        /* Extract CMathML annotation if present, which should be a single element */
        Element mathML = document.getDocumentElement();
        NodeList cmathMLList = MathMLUtilities.extractAnnotationXML(mathML, MathMLUpConverter.CONTENT_MATHML_ANNOTATION_NAME);
        if (cmathMLList!=null) {
            Assert.assertEquals(1, cmathMLList.getLength());
            Node cmathML = cmathMLList.item(0);

            Assert.assertEquals(Node.ELEMENT_NODE, cmathML.getNodeType());
            Assert.assertEquals(Globals.MATHML_NAMESPACE, cmathML.getNamespaceURI());
            
            mathML.removeChild(mathML.getFirstChild()); /* Removes <semantics/> */
            mathML.appendChild(cmathML); /* Moves CMathML element to child of <math/> */
        }
        else {
            /* Just leave alone, this will be checked later */
        }
    }

    /**
     * Overridden to cope with up-conversion failures, checking them against the given error code.
     */
    @Override
    protected void verifyResultDocument(TransformerFactory transformerFactory, Document resultDocument) throws Throwable {
        List<UpConversionFailure> upConversionFailures = UpConversionUtilities.extractUpConversionFailures(resultDocument);
        String result;
        if (upConversionFailures.isEmpty()) {
            /* Should have succeeded, so verify as normal */
            super.verifyResultDocument(transformerFactory, resultDocument);
        }
        else {
            /* Make sure we get the correct error code(s) */
            result = expectedXML.replaceAll("<.+?>", ""); /* (Yes, it's not really XML in this case!) */
            if (result.charAt(0)!='!') {
                Assert.fail("Did not expect up-conversion errors!");
            }
            String[] expectedErrorCodes = result.substring(1).split(",\\s*");
            Assert.assertEquals(expectedErrorCodes.length, upConversionFailures.size());
            for (int i=0; i<expectedErrorCodes.length; i++) {
                Assert.assertEquals(expectedErrorCodes[i], upConversionFailures.get(i).getErrorCode().toString());
            }
        }
    }

    @Override
    protected DOMOutputOptions createDOMOutputOptions() {
        DOMOutputOptions result = super.createDOMOutputOptions();
        result.setDOMPostProcessors(upconverter);
        return result;
    }
    
    @Override
    protected boolean showTokensOnFailure() {
        return false;
    }
    
    @Override
    @Test
    public void runTest() throws Throwable {
        super.runTest();
    }
}
