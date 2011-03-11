/* $Id$
 *
 * Copyright (c) 2008-2011, The University of Edinburgh.
 * All Rights Reserved
 */
package uk.ac.ed.ph.snuggletex;

import uk.ac.ed.ph.snuggletex.definitions.W3CConstants;
import uk.ac.ed.ph.snuggletex.internal.DOMBuildingController;
import uk.ac.ed.ph.snuggletex.internal.LaTeXTokeniser;
import uk.ac.ed.ph.snuggletex.internal.SessionContext;
import uk.ac.ed.ph.snuggletex.internal.SnuggleInputReader;
import uk.ac.ed.ph.snuggletex.internal.StyleEvaluator;
import uk.ac.ed.ph.snuggletex.internal.StyleRebuilder;
import uk.ac.ed.ph.snuggletex.internal.TokenFixer;
import uk.ac.ed.ph.snuggletex.internal.util.DumpMode;
import uk.ac.ed.ph.snuggletex.internal.util.ObjectDumper;
import uk.ac.ed.ph.snuggletex.internal.util.XMLUtilities;
import uk.ac.ed.ph.snuggletex.tokens.RootToken;
import uk.ac.ed.ph.snuggletex.utilities.MathMLUtilities;
import uk.ac.ed.ph.snuggletex.utilities.MessageFormatter;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import junit.framework.Assert;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * Helper class to drive tests that expect successful parsing and DOM Building,
 * carefull logging things if they go wrong.
 *
 * @author  David McKain
 * @version $Revision$
 */
public final class SnuggleTeXTestDriver {
    
    private static final Logger log = Logger.getLogger(SnuggleTeXTestDriver.class.getName());
    
    public static interface DOMFixupCallback {
        void fixupDOM(Document document) throws Throwable;
    }
    
    public static interface DOMVerifyCallback {
        void verifyDOM(Document document) throws Throwable;
    }
    
    private final SnuggleEngine snuggleEngine;
    private DOMPostProcessor domPostProcessor;
    private DOMFixupCallback domFixupCallback;
    private DOMVerifyCallback domVerifyCallback;
    private boolean showTokensOnFailure;
    
    
    public SnuggleTeXTestDriver(final SnuggleEngine snuggleEngine) {
        this.snuggleEngine = snuggleEngine;
    }
    
    public boolean isShowTokensOnFailure() {
        return showTokensOnFailure;
    }
    
    public void setShowTokensOnFailure(boolean showTokensOnFailure) {
        this.showTokensOnFailure = showTokensOnFailure;
    }
    
    
    public DOMPostProcessor getDomPostProcessor() {
        return domPostProcessor;
    }
    
    public void setDomPostProcessor(DOMPostProcessor domPostProcessor) {
        this.domPostProcessor = domPostProcessor;
    }


    public DOMFixupCallback getDomFixupCallback() {
        return domFixupCallback;
    }
    
    public void setDomFixupCallback(DOMFixupCallback domFixupCallback) {
        this.domFixupCallback = domFixupCallback;
    }


    public DOMVerifyCallback getDomVerifyCallback() {
        return domVerifyCallback;
    }
    
    public void setDomVerifyCallback(DOMVerifyCallback domVerifyCallback) {
        this.domVerifyCallback = domVerifyCallback;
    }


    public void run(String inputLaTeX, String expectedOutput) throws Throwable {
        String output = null, rawDump = null, styledDump = null, fixedDump = null, rebuiltDump = null;
        try {
            /* We'll drive the process manually as that gives us richer information if something
             * goes wrong.
             */
            SnuggleSession session = snuggleEngine.createSession();
            SnuggleInputReader inputReader = new SnuggleInputReader(session, new SnuggleInput(TestUtilities.massageInputLaTeX(inputLaTeX)));
            
            /* Tokenise */
            LaTeXTokeniser tokeniser = new LaTeXTokeniser(session);
            RootToken rootToken = tokeniser.tokenise(inputReader);
            rawDump = ObjectDumper.dumpObject(rootToken, DumpMode.DEEP);
            
            /* Make sure we got no errors */
            assertNoErrors(session);
            
            /* Evaluate styles */
            StyleEvaluator styleEvaluator = new StyleEvaluator(session);
            styleEvaluator.evaluateStyles(rootToken);
            styledDump = ObjectDumper.dumpObject(rootToken, DumpMode.DEEP);
            
            /* Run token fixer */
            TokenFixer fixer = new TokenFixer(session);
            fixer.fixTokenTree(rootToken);
            fixedDump = ObjectDumper.dumpObject(rootToken, DumpMode.DEEP);
            
            /* Rebuild styles */
            StyleRebuilder styleRebuilder = new StyleRebuilder(session);
            styleRebuilder.rebuildStyles(rootToken);
            rebuiltDump = ObjectDumper.dumpObject(rootToken, DumpMode.DEEP);
               
            /* Make sure we have still got no errors */
            assertNoErrors(session);

            /* Convert to XML */
            Document resultDocument = XMLUtilities.createNSAwareDocumentBuilder().newDocument();
            Element rootElement = resultDocument.createElementNS(W3CConstants.XHTML_NAMESPACE, "body");
            resultDocument.appendChild(rootElement);
            
            DOMOutputOptions domOutputOptions = new DOMOutputOptions();
            domOutputOptions.setMathVariantMapping(true);
            domOutputOptions.setPrefixingSnuggleXML(true);
            if (domPostProcessor!=null) {
                domOutputOptions.setDOMPostProcessors(domPostProcessor);
            }
            
            DOMBuildingController domBuildingController = new DOMBuildingController(session, domOutputOptions);
            domBuildingController.buildDOMSubtree(rootElement, rootToken.getContents());
               
            /* Make sure we have still got no errors */
            assertNoErrors(session);
            
            /* Maybe fix-up DOM */
            if (domFixupCallback!=null) {
                domFixupCallback.fixupDOM(resultDocument);
            }
            
            /* Serialize the output */
            output = MathMLUtilities.serializeDocument(resultDocument);
            
            /* Now maybe verify */
            if (domVerifyCallback!=null) {
                domVerifyCallback.verifyDOM(resultDocument);
            }
        }
        catch (Throwable e) {
            log.severe("SnuggleTeXCaller failure. Input was: " + inputLaTeX);
            if (showTokensOnFailure) {
                if (rawDump!=null) {
                    log.severe("Raw dump was: " + rawDump);
                }
                if (styledDump!=null) {
                    log.severe("Style evaluated dump was: " + styledDump);
                }
                if (fixedDump!=null) {
                    log.severe("Fixed dump was: " + fixedDump);
                }
                if (rebuiltDump!=null) {
                    log.severe("Rebuilt dump was: " + rebuiltDump);
                }
            }
            if (output!=null) {
                log.severe("Expected output: " + expectedOutput);
                log.severe("Actual output:   " + output);
            }
            log.log(Level.SEVERE, "Error was: ", e);
            throw e;
        }
    }
    
    private void assertNoErrors(SessionContext sessionContext) {
        List<InputError> errors = sessionContext.getErrors();
        if (!errors.isEmpty()) {
            log.warning("Got " + errors.size() + " unexpected error(s). Details following...");
            for (InputError error : errors) {
                log.warning(MessageFormatter.formatErrorAsString(error));
            }
        }
        Assert.assertTrue(errors.isEmpty());
    }

}
