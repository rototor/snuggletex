/* $Id:FullLaTeXInputDemoServlet.java 158 2008-07-31 10:48:14Z davemckain $
 *
 * Copyright (c) 2008-2011, The University of Edinburgh.
 * All Rights Reserved
 */
package uk.ac.ed.ph.snuggletex.webapp;

import uk.ac.ed.ph.snuggletex.SerializationSpecifier;
import uk.ac.ed.ph.snuggletex.internal.util.IOUtilities;
import uk.ac.ed.ph.snuggletex.upconversion.MathMLUpConverter;
import uk.ac.ed.ph.snuggletex.upconversion.UpConversionOptionDefinitions;
import uk.ac.ed.ph.snuggletex.upconversion.UpConversionOptions;
import uk.ac.ed.ph.snuggletex.utilities.MathMLUtilities;
import uk.ac.ed.ph.snuggletex.utilities.SerializationOptions;
import uk.ac.ed.ph.snuggletex.utilities.StylesheetManager;

import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * FIXME: Document this type!
 * 
 * @author  David McKain
 * @version $Revision:158 $
 */
public final class ASCIIMathMLUpConversionService extends HttpServlet {
    
    private static final long serialVersionUID = 2261754980279697343L;
    
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        String asciiMathML = request.getParameter("asciiMathML");
        doService(response, asciiMathML);
    }

    /** Handles the posted raw input & PMathML extracted from ASCIIMathML. */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        /* Read MathML created by ASCIIMathML */
        String asciiMathML = IOUtilities.readCharacterStream(request.getReader());
        doService(response, asciiMathML);
    }
    
    private void doService(HttpServletResponse response, String asciiMathML) throws IOException {
        /* Do up-conversion */
        MathMLUpConverter upConverter = new MathMLUpConverter(getStylesheetManager());
        UpConversionOptions upConversionOptions = new UpConversionOptions();
        upConversionOptions.setSpecifiedOption(UpConversionOptionDefinitions.DO_BRACKETED_PRESENTATION_MATHML, "true");
        Document upConvertedMathDocument = upConverter.upConvertASCIIMathML(asciiMathML, upConversionOptions);
        Element mathElement = upConvertedMathDocument.getDocumentElement(); /* NB: Document is <math/> here */
        
        /* Extract just (content) errors & up-converted PMathML for the time being.
         * 
         * TODO: Might be best to flag up things that can't get as far as Maxima
         * TODO: Would be nice to return PMathML with brackets added in for readability!
         */
        Document pmathBracketedDocument = MathMLUtilities.isolateAnnotationXML(mathElement, MathMLUpConverter.BRACKETED_PRESENTATION_MATHML_ANNOTATION_NAME);
        Document cmathDocument = MathMLUtilities.isolateAnnotationXML(mathElement, MathMLUpConverter.CONTENT_MATHML_ANNOTATION_NAME);
        Document failureAnnotation = MathMLUtilities.isolateAnnotationXML(mathElement, MathMLUpConverter.CONTENT_FAILURES_ANNOTATION_NAME);

        /* Create JSON Object encapsulating result */
        StringBuilder jsonBuilder = new StringBuilder();
        maybeAppendJson(jsonBuilder, "asciiMathML", asciiMathML);
        maybeAppendJson(jsonBuilder, "pmath", pmathBracketedDocument);
        maybeAppendJson(jsonBuilder, "cmath", cmathDocument);
        maybeAppendJson(jsonBuilder, "errors", failureAnnotation);
        endJson(jsonBuilder);
        
        response.setContentType("text/json; charset=UTF-8");
        PrintWriter responseWriter = response.getWriter();
        responseWriter.append(jsonBuilder);
        responseWriter.flush();
    }
    
    private void maybeAppendJson(StringBuilder stringBuilder, String key, Document valueDocument) {
        if (valueDocument!=null) {
            SerializationSpecifier options = new SerializationOptions();
            options.setIndenting(true);
            maybeAppendJson(stringBuilder, key, MathMLUtilities.serializeDocument(valueDocument, options));
        }
    }
    
    private void maybeAppendJson(StringBuilder stringBuilder, String key, String value) {
        if (value!=null) {
            if (stringBuilder.length()==0) {
                stringBuilder.append("{\n");
            }
            else {
                stringBuilder.append(",\n");
            }
            stringBuilder.append('"')
                .append(key)
                .append("\": \"")
                .append(value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n"))
                .append("\"");
        }
    }
    
    private void endJson(StringBuilder stringBuilder) {
        stringBuilder.append("\n}\n");
    }
    
    protected StylesheetManager getStylesheetManager() {
        return (StylesheetManager) getServletContext().getAttribute(ContextInitialiser.STYLESHEET_MANAGER_ATTRIBUTE_NAME);
    }
}