/* $Id:MathTests.java 179 2008-08-01 13:41:24Z davemckain $
 *
 * Copyright (c) 2008-2011, The University of Edinburgh.
 * All Rights Reserved
 */
package uk.ac.ed.ph.snuggletex.upconversion;

import uk.ac.ed.ph.snuggletex.MathTests;
import uk.ac.ed.ph.snuggletex.testutil.TestFileHelper;

import java.util.Collection;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/**
 * Same idea as {@link MathTests}, but tests the up-conversion to Content
 * MathML.
 * 
 * @author  David McKain
 * @version $Revision:179 $
 */
@RunWith(Parameterized.class)
public class MathUpConversionPMathMLTests extends AbstractGoodUpConversionXMLTest {
    
    public static final String TEST_RESOURCE_NAME = "math-upconversion-pmathml-tests.txt";
    
    @Parameters
    public static Collection<String[]> data() throws Exception {
        return TestFileHelper.readAndParseSingleLineInputTestResource(TEST_RESOURCE_NAME);
    }
    
    public MathUpConversionPMathMLTests(final String inputLaTeXMaths, final String expectedMathMLContent) {
        super(inputLaTeXMaths, expectedMathMLContent);
    }
    
    @Test
    public void runTest() throws Throwable {
        /* Set up up-converter so that it only generates fixed up Presentation MathML */
        UpConversionOptions upConversionOptions = new UpConversionOptions();
        upConversionOptions.setSpecifiedOption(UpConversionOptionDefinitions.DO_CONTENT_MATHML_NAME, "false");
        upConversionOptions.setSpecifiedOption(UpConversionOptionDefinitions.DO_MAXIMA_NAME, "false");
        super.runTest(upConversionOptions);
    }

}
