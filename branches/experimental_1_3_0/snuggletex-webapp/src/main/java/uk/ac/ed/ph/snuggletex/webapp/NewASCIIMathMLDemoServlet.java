/* $Id:FullLaTeXInputDemoServlet.java 158 2008-07-31 10:48:14Z davemckain $
 *
 * Copyright (c) 2008-2011, The University of Edinburgh.
 * All Rights Reserved
 */
package uk.ac.ed.ph.snuggletex.webapp;

import uk.ac.ed.ph.snuggletex.SerializationSpecifier;
import uk.ac.ed.ph.snuggletex.SnuggleEngine;
import uk.ac.ed.ph.snuggletex.SnuggleInput;
import uk.ac.ed.ph.snuggletex.SnuggleSession;
import uk.ac.ed.ph.snuggletex.WebPageOutputOptions;
import uk.ac.ed.ph.snuggletex.WebPageOutputOptionsTemplates;
import uk.ac.ed.ph.snuggletex.WebPageOutputOptions.WebPageType;
import uk.ac.ed.ph.snuggletex.upconversion.MathMLUpConverter;
import uk.ac.ed.ph.snuggletex.utilities.MathMLUtilities;

import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.transform.Transformer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * Servlet demonstrating the up-conversion process on MathML generated
 * dynamically in the browser by ASCIIMathML.
 * 
 * @author  David McKain
 * @version $Revision:158 $
 */
public final class NewASCIIMathMLDemoServlet extends BaseServlet {
    
    private static final long serialVersionUID = 2261754980279697343L;

    /** Logger so that we can log what users are trying out to allow us to improve things */
    private static Logger logger = LoggerFactory.getLogger(NewASCIIMathMLDemoServlet.class);
    
    /** Generates initial input form with some demo JavaScript. */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException,
            IOException {
        generatePage(request, response, true, "2(x-4)", null, null, null, null, null);
    }
    
    /** Handles the posted raw input & PMathML extracted from ASCIIMathML. */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        /* Get the raw ASCIIMathML input and Presentation MathML created by the ASCIIMathML
         * JavaScript code.
         */
        String asciiMathInput = request.getParameter("asciiMathInput");
        String asciiMathOutput = request.getParameter("asciiMathML");
        if (asciiMathInput==null || asciiMathOutput==null) {
            logger.warn("Could not extract data from ASCIIMath: asciiMathInput={}, asciiMathOutput={}", asciiMathInput, asciiMathOutput);
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Could not extract data passed by ASCIIMathML");
            return;
        }
        asciiMathOutput = asciiMathOutput.trim();
        
        /* Do up-conversion and extract wreckage */
        MathMLUpConverter upConverter = new MathMLUpConverter(getStylesheetManager());
        SerializationSpecifier sourceSerializationOptions = createMathMLSourceSerializationOptions();
        Document upConvertedMathDocument = upConverter.upConvertASCIIMathML(asciiMathOutput, null);
        Element mathElement = upConvertedMathDocument.getDocumentElement(); /* NB: Document is <math/> here */
        String parallelMathML = MathMLUtilities.serializeElement(mathElement, sourceSerializationOptions);
        String pMathMLUpConverted = MathMLUtilities.serializeDocument(MathMLUtilities.isolateFirstSemanticsBranch(mathElement), sourceSerializationOptions);
        Document cMathMLDocument = MathMLUtilities.isolateAnnotationXML(mathElement, MathMLUpConverter.CONTENT_MATHML_ANNOTATION_NAME);
        String cMathML = cMathMLDocument!=null ? MathMLUtilities.serializeDocument(cMathMLDocument, sourceSerializationOptions) : null;
        String maximaInput = MathMLUtilities.extractAnnotationString(mathElement, MathMLUpConverter.MAXIMA_ANNOTATION_NAME);
        
        logger.info("ASCIIMathML Input: {}", asciiMathInput);
        logger.info("Raw ASCIIMathML Output: {}", asciiMathOutput);
        logger.info("Final parallel MathML: {}", parallelMathML);
        
        generatePage(request, response, false, asciiMathInput, mathElement,
                parallelMathML, pMathMLUpConverted, cMathML, maximaInput);
    }
    
    private void generatePage(HttpServletRequest request, HttpServletResponse response,
            boolean isNewForm, String asciiMathInput, Element parallelMathMLElement,
            String parallelMathML, String pMathMLUpConverted,
            String cMathML, String maximaInput)
            throws IOException, ServletException {
        response.setContentType("text/html; charset=UTF-8");
        String contextPath = request.getContextPath();
        
        /* This is temp until I get everything sorted out */
        PrintWriter writer = response.getWriter();
        writer.println("<!DOCTYPE html>\n"
        		+ "  <html lang=\"en\">\n"
        		+ "  <head>\n" 
        		+ "    <meta charset='UTF-8'>"
        		+ "    <title>Hello</title>\n" 
        		+ "    <script type=\"text/javascript\" src=\"" + contextPath + "/includes/jquery/jquery-1.5.0.min.js\"></script>\n"
        		+ "    <script type=\"text/javascript\" src=\"" + contextPath + "/includes/ASCIIMathML.js\"></script>\n"
        		+ "    <script type=\"text/javascript\" src=\"" + contextPath + "/includes/NewASCIIMathMLWidget.js\"></script>\n"
        		+ "    <script type=\"text/javascript\" src=\"" + contextPath + "/lib/MathJax/MathJax.js\">\n" 
        		+ "      MathJax.Hub.Config({\n" 
        		+ "        /* MathML input, or sort of SnuggleTeX output */\n" 
        		+ "        config: [\"MMLorHTML.js\"],\n" 
        		+ "        extensions: [\"mml2jax.js\"],\n" 
        		+ "        jax: [\"input/MathML\"]\n" 
        		+ "      });\n" 
        		+ "    </script>\n" 
        		+ "    <script type=\"text/javascript\">//<![CDATA[\n" 
        		+ "      registerASCIIMathMLInputWidget('asciiMathInputControl', 'asciiMathOutputControl', 'mathJaxRendering', 'validatedRendering', 'previewSource');\n" 
        		+ "    //]]></script>\n" 
        		+ "  </head>\n" 
        		+ "  <body>\n" 
        		+ "    <form action=\"" + contextPath + "/NewASCIIMathMLDemo\" method=\"post\">\n" 
        		+ "      <input id=\"asciiMathInputControl\" name=\"asciiMathInputControl\" type=\"text\" value=\"" + asciiMathInput + "\">\n" /* NB: Need to escape input in future */ 
        		+ "      <input id=\"asciiMathOutputControl\" name=\"asciiMathOutputControl\" type=\"hidden\">\n" 
        		+ "      <input type=\"submit\" value=\"Go!\">\n" 
        		+ "    </form>\n"
        		+ "    <h3>ASCIIMathML rendered by MathJax</h3>\n" 
        		+ "    <div id=\"mathJaxRendering\"></div>\n"
                + "    <h3>Verified MathML</h3>"
                + "    <div id=\"validatedRendering\"></div>\n"
        		+ "    <h3>ASCIIMathML source</h3>\n"
        		+ "    <pre id=\"previewSource\"></div>\n"
        		+ "  </body>\n" 
        		+ "</html>");
        writer.flush();
    }
}