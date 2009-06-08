/* $Id$
 *
 * Copyright 2009 University of Edinburgh.
 * All Rights Reserved
 */
package uk.ac.ed.ph.snuggletex.samples;

import uk.ac.ed.ph.snuggletex.SnuggleEngine;
import uk.ac.ed.ph.snuggletex.SnuggleInput;
import uk.ac.ed.ph.snuggletex.SnuggleSession;
import uk.ac.ed.ph.snuggletex.WebPageOutputOptions;
import uk.ac.ed.ph.snuggletex.WebPageOutputOptionsTemplates;
import uk.ac.ed.ph.snuggletex.WebPageOutputOptions.WebPageType;
import uk.ac.ed.ph.snuggletex.internal.util.IOUtilities;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Example demonstrating using SnuggleTeX to create a web page,
 * outputting the resulting XHTML to the console. (This is obviously
 * not very useful!)
 *
 * @author  David McKain
 * @version $Revision$
 */
public final class WebPageExample {
    
    public static void main(String[] args) throws IOException {
        SnuggleEngine engine = new SnuggleEngine();
        SnuggleSession session = engine.createSession();
        
        SnuggleInput input = new SnuggleInput("$$1+2=3$$");
        session.parseInput(input);
        
        WebPageOutputOptions options = WebPageOutputOptionsTemplates.createWebPageOptions(WebPageType.MATHPLAYER_HTML);
        options.setIncludingStyleElement(false);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        session.writeWebPage(options, outputStream);
        String webPageAsString = IOUtilities.readUnicodeStream(new ByteArrayInputStream(outputStream.toByteArray()));
        
        System.out.println("Input " + input.getString()
                + " generated page:\n"
                +  webPageAsString);
    }
}
