/* $Id:MathTests.java 179 2008-08-01 13:41:24Z davemckain $
 *
 * Copyright (c) 2008-2011, The University of Edinburgh.
 * All Rights Reserved
 */
package uk.ac.ed.ph.snuggletex;

import uk.ac.ed.ph.snuggletex.testutil.ClassPathResolver;
import uk.ac.ed.ph.snuggletex.testutil.TestFileHelper;
import uk.ac.ed.ph.snuggletex.utilities.MathMLUtilities;

import java.io.StringReader;
import java.util.Collection;

import junit.framework.Assert;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.XMLReaderFactory;

import com.thaiopensource.util.PropertyMap;
import com.thaiopensource.util.PropertyMapBuilder;
import com.thaiopensource.validate.Schema;
import com.thaiopensource.validate.SchemaReader;
import com.thaiopensource.validate.ValidateProperty;
import com.thaiopensource.validate.Validator;
import com.thaiopensource.validate.rng.CompactSchemaReader;
import com.thaiopensource.xml.sax.CountingErrorHandler;

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
public class MathTests extends AbstractGoodMathTest {
    
    public static final String TEST_RESOURCE_NAME = "math-tests.txt";
    
    public static final String MATHML_30_SCHEMA_LOCATION = "classpath:/mathml3.rnc";
    
    @Parameters
    public static Collection<String[]> data() throws Exception {
        return TestFileHelper.readAndParseSingleLineInputTestResource(TEST_RESOURCE_NAME);
    }
    
    public MathTests(final String inputLaTeXMaths, final String expectedMathMLContent) {
        super(inputLaTeXMaths, expectedMathMLContent);
    }
    
    @Override
    @Test
    public void runTest() throws Throwable {
        super.runTest();
    }

    /**
     * Overridden to perform RELAX-NG validation against the MathML 3.0 schema
     * to ensure that there are no warning, errors or fatal errors in the resulting XML.
     */
    @Override
    protected void validateResultDocument(Document resultDocument) throws Throwable {
        ClassPathResolver resolver = new ClassPathResolver();
        PropertyMapBuilder builder = new PropertyMapBuilder();
        builder.put(ValidateProperty.RESOLVER, resolver);
        PropertyMap schemaProperties = builder.toPropertyMap();
        
        CountingErrorHandler errorHandler = new CountingErrorHandler();
        builder = new PropertyMapBuilder();
        builder.put(ValidateProperty.ERROR_HANDLER, errorHandler);
        PropertyMap validationProperties = builder.toPropertyMap();
        
        SchemaReader sr = CompactSchemaReader.getInstance();
        InputSource schemaSource = new InputSource(MATHML_30_SCHEMA_LOCATION);
        Schema schema = sr.createSchema(schemaSource, schemaProperties);
        Validator validator = schema.createValidator(validationProperties);
        
        XMLReader xmlReader = XMLReaderFactory.createXMLReader();
        xmlReader.setContentHandler(validator.getContentHandler());
        xmlReader.parse(new InputSource(new StringReader(MathMLUtilities.serializeDocument(resultDocument))));
        
        Assert.assertEquals(0, errorHandler.getWarningCount());
        Assert.assertEquals(0, errorHandler.getErrorCount());
        Assert.assertEquals(0, errorHandler.getFatalErrorCount());
    }
}
