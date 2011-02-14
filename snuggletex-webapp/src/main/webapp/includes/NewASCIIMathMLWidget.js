/*
 * This provides some basic code for managing the ASCIIMathML input widget
 * used in the ASCIIMathML input demo in SnuggleTeX.
 *
 * The general ideas may be useful in other scenarios, so feel free to use
 * and/or build on this as is necessary.
 *
 * NOTE:
 *
 * This code uses the lovely jQuery library, but avoids using the
 * $(...) function just in case your code also uses some other library
 * like prototype that defines its own $(...) function.
 * (In this case, you will still want to read:
 *
 * http://docs.jquery.com/Using_jQuery_with_Other_Libraries
 *
 * to make sure you do whatver is necessary to make sure that both
 * libraries co-exist correctly.)
 *
 * Requirements:
 *
 * ASCIIMathML.js
 * jquery.js
 *
 * Author: David McKain
 *
 * $Id:web.xml 158 2008-07-31 10:48:14Z davemckain $
 *
 * Copyright (c) 2008-2011, The University of Edinburgh
 * All Rights Reserved
 */

/************************************************************/

/* (Reset certain defaults chosen by ASCIIMathML) */
var mathcolor = "";
var mathfontfamily = "";

var newline = "\r\n";

/**
 * Simple hash that will keep track of the current value of each
 * ASCIIMathML input box, keyed on its ID. This is used by
 * updatePreviewIfChanged() to determine whether the MathML preview
 * should be updated or not.
 */
var inputTextByIdMap = {};

/**
 * Checks the content of the <input/> element having the given asciiMathInputControlId,
 * and calls {@link #updatePreview} if its contents have changed since the
 * last call to this.
 */
function updatePreviewIfChanged(asciiMathInputControlId, mathJaxRenderingContainerId,
        validatedRenderingContainerId, previewSourceContainerId,
        validatedCMathSourceContainerId) {
    var inputSelector = jQuery("#" + asciiMathInputControlId);
    var newValue = inputSelector.get(0).value;
    var oldValue = inputTextByIdMap[asciiMathInputControlId];
    if (oldValue==null || newValue!=oldValue) {
        updatePreview(newValue, mathJaxRenderingContainerId, validatedRenderingContainerId,
            previewSourceContainerId, validatedCMathSourceContainerId);
    }
    inputTextByIdMap[asciiMathInputControlId] = newValue;
}

/**
 * Hacked version of AMdisplay() from ASCIIMathMLeditor.js that allows
 * us to specify which element to display the resulting MathML
 * in and where the raw input is going to come from.
 */
function updatePreview(mathModeInput, mathJaxRenderingContainerId, validatedRenderingContainerId,
        previewSourceContainerId, validatedCMathSourceContainerId) {
    /* Get ASCIIMathML to generate a <math> element */
    var asciiMathElement = callASCIIMath(mathModeInput);

    /* Maybe update preview source box */
    if (previewSourceContainerId!=null) {
        var source = extractMathML(asciiMathElement);
        jQuery("#" + previewSourceContainerId).text(source);
    }

    /* Insert MathML into the DOM */
    var mathJaxRenderingContainer = replaceContainerContent(mathJaxRenderingContainerId, asciiMathElement);

    /* Maybe validate the input */
    if (validatedRenderingContainerId!=null) {
        var verified = jQuery.getJSON("/snuggletex/ASCIIMathMLUpConversionService",
            { asciiMathML: source.replace(/\r\n/g, '') }, /* FIXME: Hard-coded newlines */
            function(data) {
                var replacement;
                if (data['cmath']!=null) {
                    var pmathDoc = jQuery.parseXML(data['pmath']);
                    var cmathText = document.createTextNode(data['cmath']);
                    replaceContainerContent(validatedRenderingContainerId, pmathDoc.childNodes[0]);
                    replaceContainerContent(validatedCMathSourceContainerId, cmathText);
                }
                else if (data['errors']!=null) {
                    replaceContainerContent(validatedRenderingContainerId, document.createTextNode("?"));
                    replaceContainerContent(validatedCMathSourceContainerId, null);
                }
                else {
                    replaceContainerContent(validatedRenderingContainerId, document.createTextNode("UNEXPECTED ERROR"));
                    replaceContainerContent(validatedCMathSourceContainerId, null);
                }
            }
        );
    }
}

function replaceContainerContent(containerId, node) {
    var container = null;
    if (containerId!=null) {
        container = document.getElementById(containerId);
        for (var i=container.childNodes.length-1; i>=0; i--) {
            container.removeChild(container.childNodes[i]);
        }
        if (node!=null) {
            container.appendChild(node);

            /* Schedule MathJax update if this is a MathML Node */
            if (node.nodeType==1 && node.nodeName=="math") {
                MathJax.Hub.Queue(["Typeset", MathJax.Hub, container]);
            }
        }
    }
    return container;
}

/************************************************************/

function callASCIIMath(mathModeInput) {
    /* Escape use of backquote symbol to prevent exiting math mode */
    mathModeInput = mathModeInput.replace(/`/, "\\`");

    var span = AMparseMath(mathModeInput); // This is <span><math>...</math></span>
    return span.childNodes[0]; /* This is <math>...</math> */
}

/**
 * Extracts the source MathML contained within the ASCIIMath-generated
 * <math> element.
 */
function extractMathML(asciiMathElement) {
    return AMnode2string(asciiMathElement, "").substring(newline.length); /* Trim off leading newline */
}

/* Fixed up version of the function of the same name in ASCIIMathMLeditor.js,
 * with the following changes:
 *
 * * Used newline variable for line breaks
 * * Attribute values are escape correctly
 */
function AMnode2string(inNode, indent) {
    var i, str = "";
    if (inNode.nodeType == 1) {
        var name = inNode.nodeName.toLowerCase(); // (IE fix)
        str = newline + indent + "<" + name;
        for (i=0; i < inNode.attributes.length; i++) {
            var attrValue = inNode.attributes[i].nodeValue;
            if (attrValue!="italic" &&
                    attrValue!="" &&  //stop junk attributes
                    attrValue!="inherit" && // (mostly IE)
                    attrValue!=undefined) {
                str += " " + inNode.attributes[i].nodeName
                    + "=\"" + AMescapeValue(inNode.attributes[i].nodeValue) + "\"";
            }
        }
        if (name == "math") str += " xmlns=\"http://www.w3.org/1998/Math/MathML\"";
        str += ">";
        for(i=0; i<inNode.childNodes.length; i++) {
            str += AMnode2string(inNode.childNodes[i], indent+"  ");
        }
        if (name != "mo" && name != "mi" && name != "mn") {
            str += newline + indent;
        }
        str += "</" + name + ">";
    }
    else if (inNode.nodeType == 3) {
        str += AMescapeValue(inNode.nodeValue);
    }
    return str;
}

function AMescapeValue(value) {
    var str = "";
    for (i=0; i<value.length; i++) {
        if (value.charCodeAt(i)<32 || value.charCodeAt(i)>126) str += "&#"+value.charCodeAt(i)+";";
        else if (value.charAt(i)=="<") str += "&lt;";
        else if (value.charAt(i)==">") str += "&gt;";
        else if (value.charAt(i)=="&") str += "&amp;";
        else str += value.charAt(i);
    }
    return str;
}

/************************************************************/

/**
 * Sets up an ASCIIMathML input using the elements provided, binding
 * the appropriate event handlers to make everything work correctly.
 *
 * @param {String} asciiMathInputControlId ID of the ASCIIMath input <input/> element
 * @param {String} asciiMathOutputControlId ID of the hidden <input/> field that will
 *   hold the raw ASCIIMathML output on submission.
 * @param {String} mathJaxRenderingContainerId ID of the XHTML element that will be
 *   used to hold the resulting MathML preview. Note that all of its child
 *   Nodes will be removed.
 * @param {String} validatedRenderingContainerId ID of the XHTML elememt that will contain
 *   the MathJax rendering of the validated ASCIIMathML. All child Nodes will be removed.
 * @param {String} previewSourceContainerId optional ID of the XHTML element that will show
 *   the generated MathML source as it is built. This may be null to suppress this
 *   behaviour.
 */
function setupASCIIMathMLInputWidget(asciiMathInputControlId, asciiMathOutputControlId,
        mathJaxRenderingContainerId, validatedRenderingContainerId, previewSourceContainerId,
        validatedCMathSourceContainerId) {
    /* Set up submit handler for the form */
    jQuery("#" + asciiMathInputControlId).closest("form").bind("submit", function(evt) {
        /* We'll redo the ASCIIMathML process, just in case we want to allow auto-preview to be disabled in future */
        var asciiMathInput = jQuery("#" + asciiMathInputControlId).get(0).value;
        var asciiMathElement = callASCIIMath(asciiMathInput);
        var asciiMathSource = extractMathML(asciiMathElement);
        var mathmlResultControl = document.getElementById(asciiMathOutputControlId);
        mathmlResultControl.value = asciiMathSource;
        return true;
    });

    /* Set up initial preview */
    var inputSelector = jQuery("#" + asciiMathInputControlId);
    var initialInput = inputSelector.get(0).value;
    updatePreview(initialInput, mathJaxRenderingContainerId, validatedRenderingContainerId,
        previewSourceContainerId, validatedCMathSourceContainerId);

    /* Set up handler to update preview when required */
    inputSelector.bind("change keyup keydown", function() {
        updatePreviewIfChanged(asciiMathInputControlId, mathJaxRenderingContainerId,
            validatedRenderingContainerId, previewSourceContainerId, validatedCMathSourceContainerId);
    });

    /* TODO: Do we want to set up a timer as well? If so, we probably want
     * one to be global to a page, rather than each interaction.
     */
}

/**
 * Registers a new ASCIIMathML input widget. This calls {@link #setupASCIIMathMLInputWidget}
 * once the document has finished loading to bind everything together correctly.
 *
 * @param {String} asciiMathInputControlId ID of the ASCIIMath input <input/> element
 * @param {String} asciiMathOutputControlId ID of the hidden <input/> field that will
 *   hold the raw ASCIIMathML output on submission.
 * @param {String} mathJaxRenderingContainerId ID of the XHTML element that will contain
 *   the MathJax rendering of the ASCIIMathML output. Note that all of its child
 *   Nodes will be removed.
 * @param {String} validatedRenderingContainerId ID of the XHTML elememt that will contain
 *   the MathJax rendering of the validated ASCIIMathML. All child Nodes will be removed.
 * @param {String} previewSourceContainerId optional ID of the XHTML element that will show
 *   the generated MathML source as it is built. This may be null to suppress this
 *   behaviour.
 * @param {String} validatedCMathSourceContainerId optional ID of the XHTML element that
 *   will show the source of the validated Content MathML as it is built.
 */
function registerASCIIMathMLInputWidget(asciiMathInputControlId, asciiMathOutputControlId,
        mathJaxRenderingContainerId, validatedRenderingContainerId, previewSourceContainerId,
        validatedCMathSourceContainerId) {
    jQuery(document).ready(function() {
        setupASCIIMathMLInputWidget(asciiMathInputControlId, asciiMathOutputControlId,
            mathJaxRenderingContainerId, validatedRenderingContainerId,
            previewSourceContainerId, validatedCMathSourceContainerId);
    });
}
