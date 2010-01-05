/* $Id$
 *
 * Copyright (c) 2010 The University of Edinburgh.
 * All Rights Reserved
 */
package uk.ac.ed.ph.snuggletex.utilities;

import uk.ac.ed.ph.snuggletex.SerializationMethod;
import uk.ac.ed.ph.snuggletex.SerializationSpecifier;
import uk.ac.ed.ph.snuggletex.SnuggleRuntimeException;
import uk.ac.ed.ph.snuggletex.definitions.Globals;
import uk.ac.ed.ph.snuggletex.internal.util.ConstraintUtilities;
import uk.ac.ed.ph.snuggletex.internal.util.StringUtilities;

import java.io.StringReader;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Source;
import javax.xml.transform.Templates;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamSource;

/**
 * Trivial helper class to manage the loading of SnuggleTeX's internal stylesheets, using
 * a {@link StylesheetCache} to cache stylesheets for performance.
 * <p>
 * This has been made "public" as it is used by certain standalone tools, like the
 * {@link MathMLDownConverter}, but its use outside SnuggleTeX is perhaps somewhat limited.
 * <p>
 * This class is thread-safe.
 *
 * @author  David McKain
 * @version $Revision$
 */
public final class StylesheetManager {
    
    private TransformerFactoryChooser transformerFactoryChooser;
    private StylesheetCache stylesheetCache;
    
    /**
     * Creates a new {@link StylesheetManager} using the {@link DefaultTransformerFactoryChooser} and
     * no {@link StylesheetCache}.
     */
    public StylesheetManager() {
        this(DefaultTransformerFactoryChooser.getInstance(), null);
    }
    
    /**
     * Creates a new {@link StylesheetManager} using the {@link DefaultTransformerFactoryChooser} and
     * the given {@link StylesheetCache}.
     */
    public StylesheetManager(StylesheetCache cache) {
        this(DefaultTransformerFactoryChooser.getInstance(), cache);
    }
    
    /**
     * Creates a new {@link StylesheetManager} using the given {@link TransformerFactoryChooser}
     * and {@link StylesheetCache}.
     */
    public StylesheetManager(TransformerFactoryChooser transformerFactoryChooser, StylesheetCache cache) {
        this.transformerFactoryChooser = transformerFactoryChooser;
        this.stylesheetCache = cache;
    }
    
    //----------------------------------------------------------
    
    /**
     * Returns the current {@link TransformerFactoryChooser} for this {@link StylesheetManager}, which is
     * used to initialise {@link TransformerFactory} instances. This is never null.
     */
    public TransformerFactoryChooser getTransformerFactoryChooser() {
        return transformerFactoryChooser;
    }

    /**
     * Sets the {@link TransformerFactoryChooser} for this {@link StylesheetManager}, which is
     * used to initialise {@link TransformerFactory} instances.
     * 
     * @param transformerFactoryChooser new {@link TransformerFactoryChooser}, which must not be null.
     */
    public void setTransformerFactoryChooser(TransformerFactoryChooser transformerFactoryChooser) {
        ConstraintUtilities.ensureNotNull(transformerFactoryChooser, "transformerFactoryChooser");
        this.transformerFactoryChooser = transformerFactoryChooser;
    }

    
    /**
     * Returns the current {@link StylesheetCache} for this manager, which may be null.
     */
    public StylesheetCache getStylesheetCache() {
        return stylesheetCache;
    }

    /**
     * Sets the {@link StylesheetCache} for this manager, which may be null to indicate that
     * no caching should take place.
     */
    public void setStylesheetCache(StylesheetCache stylesheetCache) {
        this.stylesheetCache = stylesheetCache;
    }
    
    //----------------------------------------------------------
    
    /**
     * Uses the current {@link TransformerFactoryChooser} to obtain a {@link TransformerFactory}
     * suitable for either XSLT 1.0 or XSLT 2.0 (as specified), and configured to use a
     * {@link ClassPathURIResolver} to make it easy to find internal stylesheets.
     */
    public TransformerFactory getTransformerFactory(final boolean requireXSLT20) {
        ensureChooserSpecified();
        
        /* Choose appropriate TransformerFactory implementation */
        TransformerFactory transformerFactory;
        if (requireXSLT20) {
            transformerFactory = transformerFactoryChooser.getSuitableXSLT10TransformerFactory();
        }
        else {
            transformerFactory = transformerFactoryChooser.getSuitableXSLT20TransformerFactory();
        }
        
        /* Configure URIResolver */
        transformerFactory.setURIResolver(ClassPathURIResolver.getInstance());
        return transformerFactory;
    }
    
    /**
     * Returns whether or not XSLT 2.0 is supported, by asking the underlying
     * {@link TransformerFactoryChooser}.
     */
    public boolean supportsXSLT20() {
        ensureChooserSpecified();
        return transformerFactoryChooser.isXSLT20SupportAvailable();
    }
    
    /**
     * Checks that XSLT 2.0 is supported, by asking the underlying
     * {@link TransformerFactoryChooser}. If XSLT 2.0 is not supported then
     * a {@link SnuggleRuntimeException} is thrown.
     * 
     * @throws SnuggleRuntimeException is XSLT 2.0 is not supported.
     */
    public void requireXSLT20(String requirement) {
        if (!supportsXSLT20()) {
            throw new SnuggleRuntimeException(requirement
                    + " requires an XSLT 2.0 processor. Your TransformerFactoryChooser "
                    + transformerFactoryChooser
                    + " could not provide such a processor. Please check that an XSLT 2.0 processor (e.g. Saxon) is available"
                    + " and your TransformerFactoryChooser is configured to provide this");
        }
    }

    /**
     * Obtains the XSLT stylesheet at the given ClassPathURI, using the {@link StylesheetCache}
     * (if set) to cache stylesheets for efficiency.
     * 
     * @param classPathUri location of the XSLT stylesheet in the ClassPath, following the
     *   URI scheme in {@link ClassPathURIResolver}.
     *   
     * @return compiled XSLT stylesheet.
     */
    public Templates getStylesheet(final String classPathUri) {
        return getStylesheet(classPathUri, false);
    }
    
    /**
     * Obtains the XSLT stylesheet at the given ClassPathURI, using the {@link StylesheetCache}
     * (if set) to cache stylesheets for efficiency.
     * 
     * @param classPathUri location of the XSLT stylesheet in the ClassPath, following the
     *   URI scheme in {@link ClassPathURIResolver}.
     * @param requireXSLT20 if false uses the JAXP default {@link TransformerFactory}, otherwise
     *   specifies that we require an XSLT 2.0-compliant transformer, of which the only currently
     *   supported implementation is SAXON 9.x.
     *   
     * @return compiled XSLT stylesheet.
     */
    public Templates getStylesheet(final String classPathUri, final boolean requireXSLT20) {
        Templates result;
        if (stylesheetCache==null) {
            result = compileInternalStylesheet(classPathUri, requireXSLT20);
        }
        else {
            synchronized(stylesheetCache) {
                result = stylesheetCache.getStylesheet(classPathUri);
                if (result==null) {
                    result = compileInternalStylesheet(classPathUri, requireXSLT20);
                    stylesheetCache.putStylesheet(classPathUri, result);
                }
            }
        }
        return result;
    }
    
    private Templates compileInternalStylesheet(final String classPathUri, final boolean requireXSLT20) {
        TransformerFactory transformerFactory = getTransformerFactory(requireXSLT20);
        Source resolved;
        try {
            resolved = transformerFactory.getURIResolver().resolve(classPathUri, "");
            if (resolved==null) {
                throw new SnuggleRuntimeException("Not a ClassPath URI: " + classPathUri);
            }
            return transformerFactory.newTemplates(resolved);
        }
        catch (TransformerConfigurationException e) {
            throw new SnuggleRuntimeException("Could not compile internal stylesheet at " + classPathUri, e);
        }
        catch (TransformerException e) {
            throw new SnuggleRuntimeException("Could not resolve internal stylesheet location " + classPathUri, e);
        }
    }
    
    /**
     * Obtains a serializer stylesheet based on the stylesheet at the given URI, configured
     * as per  the given {@link SerializationSpecifier}. (Some options may require XSLT 2.0 support.)
     * 
     * @param serializerUri URI for the required serializing stylesheet, null for the default
     *   serializer.
     * @param serializationOptions desired {@link SerializationSpecifier}, null for default options
     * 
     * @throws SnuggleRuntimeException if the serializer could not be created, or if an XSLT 2.0 processor
     *   was required but could not be obtained.
     */
    public Transformer getSerializer(final String serializerUri, final SerializationSpecifier serializationOptions) {
        /* See if serializer needs any XSLT 2.0 features, failing if XSLT 2.0 is not available */
        if (serializationOptions!=null && !supportsXSLT20()) {
            if (serializationOptions.isUsingNamedEntities()) {
                requireXSLT20("The SerializatonOptions.usingNamedEntities property");
            }
            if (serializationOptions.getSerializationMethod()==SerializationMethod.XHTML) {
                requireXSLT20("SerializatonMethod.XHTML");
            }
        }
        Transformer serializer;
        try {
            /* Now create appropriate serializer */
            if (serializationOptions!=null && serializationOptions.isUsingNamedEntities() && serializerUri!=null) {
                /* Need to create a special serializer which does character mapping as well */
                serializer = cacheImporterStylesheet(true, serializerUri, Globals.MATHML_ENTITIES_MAP_XSL_RESOURCE_NAME)
                    .newTransformer();
            }
            else if (serializationOptions!=null && serializationOptions.isUsingNamedEntities()) {
                /* Use character mapping serializer */
                serializer = getStylesheet(Globals.SERIALIZE_WITH_NAMED_ENTITIES_XSL_RESOURCE_NAME, true)
                    .newTransformer();
            }
            else if (serializerUri!=null) {
                /* Use default serializer */
                serializer = getStylesheet(serializerUri)
                    .newTransformer();
            }
            else {
                serializer = getTransformerFactory(false).newTransformer();
            }
        }
        catch (TransformerConfigurationException e) {
            throw new SnuggleRuntimeException("Could not create serializer", e);
        }
        /* Now configure it as per options */
        if (serializationOptions!=null) {
            serializer.setOutputProperty(OutputKeys.METHOD, serializationOptions.getSerializationMethod().getName());
            serializer.setOutputProperty(OutputKeys.INDENT, StringUtilities.toYesNo(serializationOptions.isIndenting()));
            serializer.setOutputProperty(OutputKeys.ENCODING, serializationOptions.getEncoding());
            serializer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, StringUtilities.toYesNo(!serializationOptions.isIncludingXMLDeclaration()));
            if (serializationOptions.getDoctypePublic()!=null) {
                serializer.setOutputProperty(OutputKeys.DOCTYPE_PUBLIC, serializationOptions.getDoctypePublic());
            }
            if (serializationOptions.getDoctypeSystem()!=null) {
                serializer.setOutputProperty(OutputKeys.DOCTYPE_SYSTEM, serializationOptions.getDoctypeSystem());
            }  
        }
        return serializer;
    }
    
    private Templates cacheImporterStylesheet(final boolean requireXSLT20, final String... importUris) {
        Templates result;
        TransformerFactory transformerFactory = getTransformerFactory(requireXSLT20);
        if (stylesheetCache==null) {
            result = compileImporterStylesheet(transformerFactory, requireXSLT20, importUris);
        }
        else {
            String cacheKey = "snuggletex-importer(" + StringUtilities.join(importUris, ",") + ")";
            synchronized(stylesheetCache) {
                result = stylesheetCache.getStylesheet(cacheKey);
                if (result==null) {
                    result = compileImporterStylesheet(transformerFactory, requireXSLT20, importUris);
                    stylesheetCache.putStylesheet(cacheKey, result);
                }
            }
        }
        return result;
    }
    
    /**
     * Helper to create "driver" XSLT stylesheets that import the stylesheets at the given URIs,
     * using the given {@link TransformerFactory}.
     * 
     * @param transformerFactory
     * @param importUris
     */
    private Templates compileImporterStylesheet(final TransformerFactory transformerFactory,
            boolean requireXSLT20, final String... importUris) {
        /* Build up driver XSLT that simply imports the required stylesheets */
        StringBuilder xsltBuilder = new StringBuilder("<stylesheet version='")
            .append(requireXSLT20 ? "2.0" : "1.0")
            .append("' xmlns='http://www.w3.org/1999/XSL/Transform'>\n");
        for (String importUri : importUris) {
            xsltBuilder.append("<import href='").append(importUri).append("'/>\n");
        }
        xsltBuilder.append("</stylesheet>");
        String xslt = xsltBuilder.toString();
        
        /* Now compile and return result */
        try {
            return transformerFactory.newTemplates(new StreamSource(new StringReader(xslt)));
        }
        catch (TransformerConfigurationException e) {
            throw new SnuggleRuntimeException("Could not compile stylesheet driver " + xslt, e);
        }
    }
    
    private void ensureChooserSpecified() {
        if (transformerFactoryChooser==null) {
            throw new SnuggleRuntimeException("No TransformerFactoryChooser set on this StylesheetManager");
        }
    }
}
