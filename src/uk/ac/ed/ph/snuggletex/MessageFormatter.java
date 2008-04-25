/* $Id: MessageFormatter.java,v 1.7 2008/04/23 11:23:36 dmckain Exp $
 *
 * Copyright 2008 University of Edinburgh.
 * All Rights Reserved
 */
package uk.ac.ed.ph.snuggletex;

import uk.ac.ed.ph.snuggletex.conversion.FrozenSlice;
import uk.ac.ed.ph.snuggletex.conversion.SnuggleInputReader;
import uk.ac.ed.ph.snuggletex.conversion.WorkingDocument;
import uk.ac.ed.ph.snuggletex.conversion.WorkingDocument.CharacterSource;
import uk.ac.ed.ph.snuggletex.conversion.WorkingDocument.IndexResolution;
import uk.ac.ed.ph.snuggletex.conversion.WorkingDocument.Slice;
import uk.ac.ed.ph.snuggletex.conversion.WorkingDocument.SourceContext;
import uk.ac.ed.ph.snuggletex.definitions.Globals;

import java.text.MessageFormat;
import java.util.MissingResourceException;
import java.util.PropertyResourceBundle;
import java.util.ResourceBundle;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * Utility class that formats an {@link InputError} in various ways.
 * 
 * NOTE: We're using {@link MessageFormat} extensively here, which hasn't been updated to use
 * {@link StringBuilder} so we'll sadly have to make do with {@link StringBuffer}
 *
 * @author  David McKain
 * @version $Revision: 1.7 $
 */
public final class MessageFormatter {
    
    private static final PropertyResourceBundle errorMessageBundle;
    private static final PropertyResourceBundle generalMessageBundle;
    
    static {
        try {
            errorMessageBundle = (PropertyResourceBundle) ResourceBundle.getBundle(Globals.ERROR_MESSAGES_PROPERTIES_BASENAME);
            generalMessageBundle = (PropertyResourceBundle) ResourceBundle.getBundle(Globals.GENERAL_MESSAGES_PROPERTIES_BASENAME);
        }
        catch (MissingResourceException e) {
            throw new SnuggleLogicException(e);
        }
    }
    
    /** Constructs an error message for the given {@link InputError}. */
    public static String getErrorMessage(InputError error) {
        return MessageFormat.format(errorMessageBundle.getString(error.getErrorCode().toString()),
                error.getArguments());
    }
    
    /** Creates a full diagnosis of the given error */
    public static String formatErrorAsString(InputError error) {
        StringBuffer resultBuilder = new StringBuffer();
        appendErrorAsString(resultBuilder, error);
        return resultBuilder.toString();
    }
    
    /**
     * Creates a DOM {@link Element} containing information about the given error, including
     * either just the {@link ErrorCode} or full details.
     * 
     * @param ownerDocument {@link Document} that will contain the resulting element.
     * @param error {@link InputError} to format
     * @param fullDetails false if you just want the error code, true for full details.
     */
    public static Element formatErrorAsXML(Document ownerDocument, InputError error, boolean fullDetails) {
        Element result = ownerDocument.createElementNS(SnuggleTeX.SNUGGLETEX_NAMESPACE, "error");
        result.setAttribute("code", error.getErrorCode().name());
        
        if (fullDetails) {
            /* Nicely format XML error content */
            StringBuffer messageBuilder = new StringBuffer(getErrorMessage(error));
            FrozenSlice errorSlice = error.getSlice();
            if (errorSlice!=null) {
                appendSliceContext(messageBuilder, errorSlice);
            }
            
            /* Add message as child node */
            result.appendChild(ownerDocument.createTextNode(messageBuilder.toString()));
        }
        /* That's it! */
        return result;
    }
    
    public static void appendErrorAsString(StringBuffer messageBuilder, InputError error) {
        new MessageFormat(generalMessageBundle.getString("error_as_string")).format(new Object[] {
                error.getErrorCode().toString(), /* Error code */
                getErrorMessage(error) /* Error Message */
        }, messageBuilder, null);
        FrozenSlice errorSlice = error.getSlice();
        if (errorSlice!=null) {
            appendSliceContext(messageBuilder, errorSlice);
        }
    }
    
    private static void appendNewlineIfRequired(StringBuffer messageBuilder) {
        if (messageBuilder.length()>0) {
            messageBuilder.append('\n');
        }
    }

    public static void appendSliceContext(StringBuffer messageBuilder, FrozenSlice slice) {
        WorkingDocument document = slice.getDocument();
        
        /* Work out where the error occurred */
        IndexResolution errorResolution = document.resolveIndex(slice.startIndex, false);
        if (errorResolution==null) {
            /* (If this happens, then most likely the error occurred at the end of the document) */
            errorResolution = document.resolveIndex(slice.startIndex, true);
        }
        if (errorResolution==null) {
            throw new SnuggleLogicException("Could not resolve component containing error slice starting at "
                    + slice.startIndex);
        }
        Slice errorSlice = errorResolution.slice;
        CharacterSource errorComponent = errorSlice.resolvedComponent;
        int errorIndex = errorResolution.indexInComponent;
        appendFrame(messageBuilder, errorComponent, errorIndex);
    }
    
    private static void appendFrame(StringBuffer messageBuilder, CharacterSource source, int offsetInSource) {
        SourceContext context = source.context;
        if (context instanceof SnuggleInputReader) {
            SnuggleInputReader inputContext = (SnuggleInputReader) context;
            int[] location = inputContext.getLineAndColumn(offsetInSource);
            appendNewlineIfRequired(messageBuilder);
            new MessageFormat(generalMessageBundle.getString("input_context")).format(new Object[] {
                  location[0], /* Line */
                  location[1], /* Column */
                  inputContext.getInput() /* Input description */
            }, messageBuilder, null);
        }
        if (source.substitutedSource!=null) {
            appendNewlineIfRequired(messageBuilder);
            new MessageFormat(generalMessageBundle.getString("subs_context")).format(new Object[] {
                    offsetInSource, /* Character */
                    source.substitutedText, /* Original text */
            }, messageBuilder, null);
            appendFrame(messageBuilder, source.substitutedSource, source.substitutionOffset);
        }
    }
}