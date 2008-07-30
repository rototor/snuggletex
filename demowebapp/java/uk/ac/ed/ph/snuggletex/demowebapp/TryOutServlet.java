/* $Id$
 *
 * Copyright 2008 University of Edinburgh.
 * All Rights Reserved
 */
package uk.ac.ed.ph.snuggletex.demowebapp;

import uk.ac.ed.ph.aardvark.commons.util.IOUtilities;
import uk.ac.ed.ph.snuggletex.InputError;
import uk.ac.ed.ph.snuggletex.MathMLWebPageBuilderOptions;
import uk.ac.ed.ph.snuggletex.MessageFormatter;
import uk.ac.ed.ph.snuggletex.SnuggleInput;
import uk.ac.ed.ph.snuggletex.SnuggleTeXEngine;
import uk.ac.ed.ph.snuggletex.SnuggleTeXSession;
import uk.ac.ed.ph.snuggletex.DOMBuilderOptions.ErrorOutputOptions;
import uk.ac.ed.ph.snuggletex.MathMLWebPageBuilderOptions.WebPageType;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.transform.Templates;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;

/**
 * Trivial servlet to provide the functionality for the "try out" page.
 *
 * @author  David McKain
 * @version $Revision$
 */
public final class TryOutServlet extends BaseServlet {
    
    private static final long serialVersionUID = 4376587500238353176L;
    
    /** Logger so that we can log what users are trying out to allow us to improve things */
    private Logger log = Logger.getLogger(TryOutServlet.class.getSimpleName());
    
    
    /** Location of XSLT controlling page layout */
    public static final String TRYOUT_XSLT_LOCATION = "/WEB-INF/tryout.xsl";
    
    /** Locaiton of default input to use when visiting the page for the first time */
    public static final String DEFAULT_INPUT_LOCATION = "/WEB-INF/tryout-default.tex";
    
    /** Compiled XSLT */
    public static Templates templates;
    
    @Override
    public void init() throws ServletException {
        super.init();
        
        /* Pre-compile special XSLT that builds on the standard XSLT to put the
         * input LaTeX into a simple form.
         */       
        templates = compileStylesheet(TRYOUT_XSLT_LOCATION);
    }
    
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException,
            IOException {
        doRequest(request, response);
    }
    
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        doRequest(request, response);
    }
    
    private void doRequest(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        /* Read in input LaTeX, using some placeholder text if nothing was provided */
        String rawInputLaTeX = request.getParameter("input");
        String resultingInputLaTeX;
        if (rawInputLaTeX!=null) {
            /* Tidy up line endings */
            resultingInputLaTeX = rawInputLaTeX.replaceAll("(\r\n|\r|\n)", "\n");
        }
        else {
            resultingInputLaTeX = readDefaultInput();
        }
        
        /* Parse the LaTeX */
        SnuggleTeXEngine engine = new SnuggleTeXEngine();
        SnuggleTeXSession session = engine.createSession();
        
        SnuggleInput input = new SnuggleInput(resultingInputLaTeX, "Form Input");
        session.parseInput(input);
        
        /* Set up web output options */
        MathMLWebPageBuilderOptions options = new MathMLWebPageBuilderOptions();
        options.setMathVariantMapping(true);
        options.setAddingMathAnnotations(true);
        options.setPageType(WebPageType.CROSS_BROWSER_XHTML);
        options.setErrorOutputOptions(ErrorOutputOptions.XHTML);
        options.setTitle("SnuggleTeX - Try Out");
        options.setAddingTitleHeading(false); /* We'll put our own title in */
        options.setIndenting(true);
        options.setCSSStylesheetURLs(
                request.getContextPath() + "/includes/physics.css",
                request.getContextPath() + "/includes/snuggletex.css"
        );
        
        /* Create output for logging purposes */
        String xmlString = session.buildXMLString(options);
        
        /* Log things nicely */
        if (rawInputLaTeX!=null) {
            List<InputError> errors = session.getErrors();
            Level level = errors.isEmpty() ? Level.INFO : Level.WARNING;
            log.log(level, "Input: " + resultingInputLaTeX);
            log.log(level, "Output: " + xmlString);
            log.log(level, "Error count: " + errors.size());
            for (InputError error : errors) {
                log.log(level, "Error: " + MessageFormatter.formatErrorAsString(error));
            }
        }
        
        /* Create XSLT to generate the resulting page */
        Transformer stylesheet;
        try {
            stylesheet = templates.newTransformer();
            stylesheet.setParameter("context-path", request.getContextPath());
            stylesheet.setParameter("latex-input", resultingInputLaTeX);
        }
        catch (TransformerConfigurationException e) {
            throw new ServletException("Could not create stylesheet from Templates", e);
        }
        options.setStylesheet(stylesheet);
        
        /* Generate and serve the resulting web page */
        try {
            session.writeWebPage(options, response, response.getOutputStream());
        }
        catch (Exception e) {
            throw new ServletException("Unexpected Exception", e);
        }
    }

    /**
     * Creates the initial sample input to use when we first visit the page.
     * <p>
     * This simply loads up data from {@link #DEFAULT_INPUT_LOCATION}.
     * <p>
     * We could maybe cache this in future, but there's probably no real point at the moment.
     * 
     * @throws ServletException 
     * @throws IOException 
     */
    private String readDefaultInput() throws ServletException, IOException {
        InputStream resourceStream = ensureReadResource(DEFAULT_INPUT_LOCATION);
        return IOUtilities.readUnicodeStream(resourceStream);
    }
}
