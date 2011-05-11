package pt.inevo.encontra.convert;

import org.apache.batik.dom.svg.SVGDOMImplementation;
import org.apache.batik.dom.svg.SVGOMDocument;
import org.apache.batik.dom.svg.SVGOMImageElement;

import org.apache.batik.svggen.*;

import org.apache.batik.transcoder.TranscoderException;
import org.apache.batik.transcoder.TranscoderInput;
import org.apache.batik.transcoder.TranscoderOutput;
import org.apache.batik.transcoder.svg2svg.SVGTranscoder;
import org.w3c.dom.Comment;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import psd.Layer;
import psd.Psd;
import psd.parser.BlendMode;
import psd.parser.layer.LayerType;
import psd.parser.layer.additional.effects.*;

import javax.imageio.ImageIO;
import java.awt.*;
import java.io.*;
import java.util.*;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PSDConverter implements Converter {

    private static Logger log = Logger.getLogger(PSDConverter.class.getName());

    //SVG namespace
    private static String svgNS = "http://www.w3.org/2000/svg";

    /**
     * Supported Blend Modes - Normal is the Default one, that's why it's not here
     */
    private static final Map<BlendMode, String> SVG_BLEND_MODES =
            Collections.unmodifiableMap(new HashMap<BlendMode, String>() {{
                this.put(BlendMode.OVERLAY, "overlay");
                this.put(BlendMode.MULTIPLY, "multiply");
                this.put(BlendMode.SCREEN, "screen");
                this.put(BlendMode.DARKEN, "darken");
                this.put(BlendMode.LIGHTEN, "lighten");
            }});

    public PSDConverter() {
    }

    /*Converts the input PSD file to a SVG one.*/
    public void convertToMimeType(String mimetype, InputStream input, OutputStream output) {

        if (mimetype.equals("svg")) {
            convertToSVG(input, output);
        } else if (mimetype.equals("image/png") || mimetype.equals("png")) {
            convertToPNG(input, output);
        } else {
            log.log(Level.WARNING, "Error: cannot convert to the specified mimetype(" + mimetype + ")");
        }
    }

    public void convertToSVG(InputStream input, OutputStream output) {
        try {
            // Parse the psd file
            Psd psdFile = new Psd(input);

            // Get a DOMImplementation.
            SVGDOMImplementation domImpl = new SVGDOMImplementation();

            // Create an instance of org.w3c.dom.Document.
            SVGOMDocument document = (SVGOMDocument) domImpl.createDocument(svgNS, "svg", null);
            document.setIsSVG12(true);

            // Get the root element (the 'svg' element).
            Element svgRoot = document.getDocumentElement();

            // Set the width and height attributes on the root 'svg' element.
            svgRoot.setAttributeNS(null, "width", String.valueOf(psdFile.getWidth()));
            svgRoot.setAttributeNS(null, "height", String.valueOf(psdFile.getHeight()));
            svgRoot.setAttributeNS(null, "depth", String.valueOf(psdFile.getDepth()));
            svgRoot.setAttributeNS(null, "channels", String.valueOf(psdFile.getChannelsCount()));
            svgRoot.setAttributeNS(null, "enable-background", "new");
            svgRoot.setAttributeNS(null, "version", "1.2");

            // Convert all the layers to SVG nodes
            int numberLayers = psdFile.getLayersCount();
            for (int i = 0; i < numberLayers; i++) {
                Layer l = psdFile.getLayer(i);

                //get the svg node that represents the layer and append it to the root
                Element layer = parseLayer(l, document);
                svgRoot.appendChild(layer);
            }

            writeSVG(document, output);

        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    public void convertToPNG(InputStream input, OutputStream output) {
        try {
            // Parse the psd file
            Psd psdFile = new Psd(input);

            Layer baseLayer = psdFile.getBaseLayer();
            if (baseLayer.getImage() != null){
                ImageIO.write(baseLayer.getImage(), "png", output);
            }

        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    /**
     * Parses the layer and returns a SVG node. If the layer is LayerType.FOLDER than it recursively parses
     * all the layers in the folder and returns an hierarchy of SVG nodes.
     * @param l
     * @param document
     * @return
     */
    private Element parseLayer(Layer l, Document document) {

        /**
         * If LayerType is Folder, than iterate over all the childs
         */
        if (l.getType().equals(LayerType.FOLDER)) {
            Element folderLayer = document.createElementNS(svgNS, "g");
            folderLayer.setAttribute("name", l.toString().replaceAll(" ", "-"));
            folderLayer.setAttribute("type", l.getType().toString());

            int internalLayersNumber = l.getLayersCount();
            for (int j = 0; j < internalLayersNumber; j++) {
                Element layerElement = parseLayer(l.getLayer(j), document);
                folderLayer.appendChild(layerElement);
            }
            return folderLayer;

        } else {    // it's a NORMAL or HIDDEN Layer
            Element folderLayer = document.createElementNS(svgNS, "g");
            folderLayer.setAttribute("name", l.toString().replaceAll(" ", "-"));
            folderLayer.setAttribute("type", l.getType().toString());

            //set the visibility property
            if (l.getType().equals(LayerType.HIDDEN)) {
                folderLayer.setAttribute("visibility", "hidden");
            } else {
                folderLayer.setAttribute("visibility", "visible");
            }

            // Get the element representing the layer has a SVG node (the layer image)
            Element innerLayer = getSVGLayerElement(l, document);
            parseEffects(l, folderLayer, document);
            folderLayer.appendChild(innerLayer);

            return folderLayer;
        }
    }

    /**
     * Parses the layer effects and converts them to SVG using SVG Filters.
     * @param l
     * @param layerElement
     * @param document
     */
    private void parseEffects(Layer l, Element layerElement, Document document){
        List<PSDEffect> effects = l.getEffectsList();

        Element layerEffectsDefs = null;
        Element filtersElement = null;
        //layer name used in all the filters (without spaces, using a '-')
        String layerName = l.toString().replaceAll(" ", "-");

        boolean existsEffectEnabled = false;
        /*Detect if at least one is enabled, otherwise we don't want to proceed*/
        for (PSDEffect effect: effects) {
            if (effect.isEnabled()) {
                existsEffectEnabled = true;
            }
        }

        if (effects.size() > 0 && existsEffectEnabled || !l.getLayerBlendMode().equals(BlendMode.NORMAL)){
            layerEffectsDefs = document.createElement("defs");
            layerElement.appendChild(layerEffectsDefs);

            filtersElement = document.createElement("filter");
            layerEffectsDefs.appendChild(filtersElement);

            //TO DO - must fix the sizing of the bounding box?
            filtersElement.setAttribute("width", "200%");
            filtersElement.setAttribute("height", "200%");
            filtersElement.setAttribute("id", layerName + "-filters");

            filtersElement.setAttribute("filterUnits", "userSpaceOnUse");
            layerElement.setAttribute("filter", "url(#" + filtersElement.getAttribute("id") + ")");
        }

        /**
         * Different filters have to be merged, because they are independent
         */
        List<Element> elementsToMerge = new ArrayList<Element>();
        for (PSDEffect effect: effects) {

            if (!effect.isEnabled()) {
                //THE EFFECT IS NOT ENABLED, SO WE DON'T WANT TO RENDER IT.
                //TO DO - render it, but disable the effect.
                continue;
            }

            if (effect instanceof DropShadowEffect) {
                DropShadowEffect dpEffect = (DropShadowEffect)effect;
                Color effectColor = dpEffect.getColor();

                if (dpEffect.isInner()){
                    //INNER DROP-SHADOW
                    Comment innerComment = document.createComment("INNER DROPSHADOW");
                    filtersElement.appendChild(innerComment);

                    Element floodElement = document.createElement("feFlood");
                    floodElement.setAttribute("result", layerName + "-flooded" + (dpEffect.isInner()? "-inner": "-outer"));
                    floodElement.setAttribute("style", "flood-color:rgb(" + effectColor.getRed() + ", "
                            + effectColor.getGreen() + ", " + effectColor.getBlue() + ");flood-opacity:" + dpEffect.getAlpha());
                    filtersElement.appendChild(floodElement);

                    Element composite = document.createElement("feComposite");
                    composite.setAttribute("in", layerName + "-flooded" + (dpEffect.isInner()? "-inner": "-outer"));
                    composite.setAttribute("in2", "SourceAlpha");
                    composite.setAttribute("result", layerName + "-composite-Flood" + (dpEffect.isInner()? "-inner": "-outer"));
                    composite.setAttribute("operator", "in");
                    filtersElement.appendChild(composite);

                    Element floodElementPure = document.createElement("feFlood");
                    floodElementPure.setAttribute("result", layerName + "-flooded-pure" + (dpEffect.isInner() ? "-inner" : "-outer"));
                    floodElementPure.setAttribute("style", "flood-color:rgb(" + effectColor.getRed() + ", "
                            + effectColor.getGreen() + ", " + effectColor.getBlue() + ");flood-opacity:" + l.getAlpha()/255.0);
                    filtersElement.appendChild(floodElementPure);

                    Element compositeFloodPure = document.createElement("feComposite");
                    compositeFloodPure.setAttribute("in", layerName + "-flooded-pure" + (dpEffect.isInner() ? "-inner" : "-outer"));
                    compositeFloodPure.setAttribute("in2", "SourceAlpha");
                    compositeFloodPure.setAttribute("result", layerName + "-composite-Flood-pure" + (dpEffect.isInner() ? "-inner" : "-outer"));
                    compositeFloodPure.setAttribute("operator", "in");
                    filtersElement.appendChild(compositeFloodPure);

                    Element gaussianBlurElement = document.createElement("feGaussianBlur");
                    filtersElement.appendChild(gaussianBlurElement);
                    gaussianBlurElement.setAttribute("in", "SourceGraphic");
                    gaussianBlurElement.setAttribute("result", layerName + "-blur-out" + (dpEffect.isInner()? "-inner": "-outer"));
                    gaussianBlurElement.setAttribute("stdDeviation", dpEffect.getBlur()/2 + "");

                    Element feOffset = document.createElement("feOffset");
                    filtersElement.appendChild(feOffset);
                    feOffset.setAttribute("in", layerName + "-blur-out" + (dpEffect.isInner()? "-inner": "-outer"));
                    feOffset.setAttribute("result", layerName + "-the-shadow" + (dpEffect.isInner() ? "-inner" : "-outer"));
                    feOffset.setAttribute("dx", (Math.cos(Math.toRadians(dpEffect.getAngle())) * dpEffect.getDistance() + ""));
                    feOffset.setAttribute("dy", (Math.sin(Math.toRadians(dpEffect.getAngle())) * dpEffect.getDistance() + ""));

                    Element compositeFinal = document.createElement("feComposite");
                    compositeFinal.setAttribute("in", layerName + "-the-shadow" + (dpEffect.isInner()? "-inner": "-outer"));
                    compositeFinal.setAttribute("in2", layerName + "-composite-Flood-pure" + (dpEffect.isInner()? "-inner": "-outer"));
                    compositeFinal.setAttribute("result", layerName + "-composite-blend" + (dpEffect.isInner()? "-inner": "-outer"));
                    compositeFinal.setAttribute("operator", "in");
                    filtersElement.appendChild(compositeFinal);

                    Element compositeGroup = document.createElement("feComposite");
                    compositeGroup.setAttribute("in", layerName + "-composite-Flood" + (dpEffect.isInner()? "-inner": "-outer"));
                    compositeGroup.setAttribute("in2", layerName + "-composite-blend" + (dpEffect.isInner()? "-inner": "-outer"));
                    compositeGroup.setAttribute("result", layerName + "-composite-Group" + (dpEffect.isInner()? "-inner": "-outer"));
                    compositeGroup.setAttribute("operator", "xor");
                    filtersElement.appendChild(compositeGroup);

                    Element feBlend = document.createElement("feBlend");
                    filtersElement.appendChild(feBlend);
                    feBlend.setAttribute("in", "SourceGraphic");
                    feBlend.setAttribute("in2", layerName + "-composite-Flood" + (dpEffect.isInner()? "-inner": "-outer"));
                    feBlend.setAttribute("result", layerName + "composite-Blend" + (dpEffect.isInner()? "-inner": "-outer"));

                    String blendMode = "normal";
                    if (SVG_BLEND_MODES.containsKey(dpEffect.getBlendMode())) {
                        blendMode = SVG_BLEND_MODES.get(dpEffect.getBlendMode());

                        if (blendMode.equals("overlay")) {  //might not work in some browsers
                            blendMode = "normal";
                        }
                    }
                    feBlend.setAttribute("mode", blendMode);

                    Element feMerge = document.createElement("feMerge");
                    feMerge.setAttribute("result", layerName + "-dropShadowFinalMerge");
                    filtersElement.appendChild(feMerge);

                    Element feMergeNode1 = document.createElement("feMergeNode");
                    feMerge.appendChild(feMergeNode1);
                    feMergeNode1.setAttribute("in", feBlend.getAttribute("result"));

                    Element feMergeNode2 = document.createElement("feMergeNode");
                    feMerge.appendChild(feMergeNode2);
                    feMergeNode2.setAttribute("in", layerName + "-composite-blend" + (dpEffect.isInner()? "-inner": "-outer"));

                    //add it to the merge list
                    elementsToMerge.add(feMerge);

                    //TO DO - MUST CHECK THE GLOBAL LIGHTING FOR THE GLOBAL ANGLE
                } else {
                    //OUTER DROP-SHADOW
                    Comment innerComment = document.createComment("OUTER DROPSHADOW");
                    filtersElement.appendChild(innerComment);

                    Element floodElement = document.createElement("feFlood");
                    floodElement.setAttribute("result", layerName + "-flooded" + (dpEffect.isInner()? "-inner": "-outer"));
                    floodElement.setAttribute("style", "flood-color:rgb(" + effectColor.getRed() + ", "
                            + effectColor.getGreen() + ", " + effectColor.getBlue() + ");flood-opacity:" + dpEffect.getAlpha());
                    filtersElement.appendChild(floodElement);

                    Element gaussianBlurElement = document.createElement("feGaussianBlur");
                    filtersElement.appendChild(gaussianBlurElement);
                    gaussianBlurElement.setAttribute("in", "SourceAlpha");
                    gaussianBlurElement.setAttribute("result", layerName + "-blur-out" + (dpEffect.isInner()? "-inner": "-outer"));
                    gaussianBlurElement.setAttribute("stdDeviation", dpEffect.getBlur()/2 + "");

                    Element feOffset = document.createElement("feOffset");
                    filtersElement.appendChild(feOffset);
                    feOffset.setAttribute("in", layerName + "-blur-out" + (dpEffect.isInner()? "-inner": "-outer"));
                    feOffset.setAttribute("result", layerName + "-the-shadow" + (dpEffect.isInner()? "-inner": "-outer"));
                    feOffset.setAttribute("dx", (Math.cos(Math.toRadians(dpEffect.getAngle()))* dpEffect.getDistance())+"");
                    feOffset.setAttribute("dy", (Math.sin(Math.toRadians(dpEffect.getAngle()))* dpEffect.getDistance())+"");

                    Element composite = document.createElement("feComposite");
                    composite.setAttribute("in", layerName + "-flooded" + (dpEffect.isInner()? "-inner": "-outer"));
                    composite.setAttribute("in2", layerName + "-the-shadow" + (dpEffect.isInner()? "-inner": "-outer"));
                    composite.setAttribute("result", layerName + "-composite-blend" + (dpEffect.isInner()? "-inner": "-outer"));
                    composite.setAttribute("operator", "in");
                    filtersElement.appendChild(composite);

                    Element feBlend = document.createElement("feBlend");
                    filtersElement.appendChild(feBlend);
                    feBlend.setAttribute("in", "SourceGraphic");
                    feBlend.setAttribute("in2", layerName + "-composite-blend" + (dpEffect.isInner()? "-inner": "-outer"));
                    feBlend.setAttribute("result", layerName + "-DropShadowFull" + (dpEffect.isInner()? "-inner": "-outer"));

                    String blendMode = "normal";
                    if (SVG_BLEND_MODES.containsKey(dpEffect.getBlendMode())) {
                        blendMode = SVG_BLEND_MODES.get(dpEffect.getBlendMode());

                        if (blendMode.equals("overlay")) {  //might not work in some browsers
                            blendMode = "normal";
                        }
                    }
                    feBlend.setAttribute("mode", blendMode);

                    //add it to the merge list
                    elementsToMerge.add(feBlend);
                }
            } else if (effect instanceof GlowEffect) {
                GlowEffect glowEffect = (GlowEffect)effect;

                if (glowEffect.isInner()){

                    Comment innerComment = document.createComment("INNER GLOW");
                    filtersElement.appendChild(innerComment);

                    Element gaussianBlurElement = document.createElement("feGaussianBlur");
                    gaussianBlurElement.setAttribute("id", layerName + "-glow-blur" + (glowEffect.isInner()? "-inner": "-outer"));
                    gaussianBlurElement.setAttribute("in", "SourceGraphic");
                    gaussianBlurElement.setAttribute("stdDeviation", glowEffect.getBlur()/2+"");
                    gaussianBlurElement.setAttribute("result", layerName + "-glow-result" + (glowEffect.isInner()? "-inner": "-outer"));
                    filtersElement.appendChild(gaussianBlurElement);

                    Element morphology = document.createElement("feMorphology");
                    morphology.setAttribute("in", layerName + "-glow-result" + (glowEffect.isInner()? "-inner": "-outer"));
                    morphology.setAttribute("operator", "erode");
                    morphology.setAttribute("radius", "4");
                    morphology.setAttribute("result", layerName + "-morphedAlpha" + (glowEffect.isInner()? "-inner": "-outer"));
                    filtersElement.appendChild(morphology);

                    Color effectColor = glowEffect.getColor();
                    Element flood = document.createElement("feFlood");
                    flood.setAttribute("result", layerName + "-flooded" + (glowEffect.isInner()? "-inner": "-outer"));
                    flood.setAttribute("style", "flood-color:rgb(" + effectColor.getRed() + ", "
                            + effectColor.getGreen() + ", " + effectColor.getBlue() + ");flood-opacity:" + glowEffect.getAlpha());
                    filtersElement.appendChild(flood);

                    Element compositeElement = document.createElement("feComposite");
                    compositeElement.setAttribute("in2",  "SourceAlpha");
                    compositeElement.setAttribute("in", layerName + "-flooded" + (glowEffect.isInner()? "-inner": "-outer"));
                    compositeElement.setAttribute("operator", "in");
                    compositeElement.setAttribute("result", layerName + "-coloredShadow" + (glowEffect.isInner()? "-inner": "-outer"));
                    filtersElement.appendChild(compositeElement);

                    Element feMerge = document.createElement("feMerge");
                    feMerge.setAttribute("result", layerName + "-glowEffectMerge" + (glowEffect.isInner()? "-inner": "-outer"));
                    filtersElement.appendChild(feMerge);

                    Element feMergeNode2 = document.createElement("feMergeNode");
                    feMerge.appendChild(feMergeNode2);
                    feMergeNode2.setAttribute("in", compositeElement.getAttribute("result"));

                    Element feMergeNode1 = document.createElement("feMergeNode");
                    feMerge.appendChild(feMergeNode1);
                    feMergeNode1.setAttribute("in", morphology.getAttribute("result"));

                    //add it to the merge list
                    elementsToMerge.add(feMerge);

                } else {
                    Comment innerComment = document.createComment("OUTER GLOW");
                    filtersElement.appendChild(innerComment);

                    Element morphology = document.createElement("feMorphology");
                    morphology.setAttribute("in", "SourceAlpha");
                    morphology.setAttribute("operator", "dilate");
                    morphology.setAttribute("radius", "1");
                    morphology.setAttribute("result", layerName + "-morphedAlpha" + (glowEffect.isInner()? "-inner": "-outer"));
                    filtersElement.appendChild(morphology);

                    Element gaussianBlurElement = document.createElement("feGaussianBlur");
                    gaussianBlurElement.setAttribute("id", layerName + "-glow-blur" + (glowEffect.isInner()? "-inner": "-outer"));
                    gaussianBlurElement.setAttribute("in", layerName + "-morphedAlpha" + (glowEffect.isInner()? "-inner": "-outer"));

                    //TO DO - must map the glowEffect.getBlur() to the svg gaussianBlur sdtdeviation property
                    gaussianBlurElement.setAttribute("stdDeviation", glowEffect.getBlur()/2+"");
                    gaussianBlurElement.setAttribute("result", layerName + "-glow-result" + (glowEffect.isInner()? "-inner": "-outer"));
                    filtersElement.appendChild(gaussianBlurElement);

                    Color effectColor = glowEffect.getColor();
                    Element flood = document.createElement("feFlood");
                    flood.setAttribute("result", layerName + "-flooded" + (glowEffect.isInner()? "-inner": "-outer"));
                    flood.setAttribute("style", "flood-color:rgb(" + effectColor.getRed() + ", "
                            + effectColor.getGreen() + ", " + effectColor.getBlue() + ");flood-opacity:" + glowEffect.getAlpha());
                    filtersElement.appendChild(flood);

                    Element compositeElement = document.createElement("feComposite");
                    compositeElement.setAttribute("in2",  layerName + "-glow-result" + (glowEffect.isInner()? "-inner": "-outer"));
                    compositeElement.setAttribute("in", layerName + "-flooded" + (glowEffect.isInner()? "-inner": "-outer"));
                    compositeElement.setAttribute("operator", "in");
                    compositeElement.setAttribute("result", layerName + "-coloredShadow" + (glowEffect.isInner()? "-inner": "-outer"));
                    filtersElement.appendChild(compositeElement);

                    Element finalCompositeElement = document.createElement("feComposite");
                    finalCompositeElement.setAttribute("in2",  layerName + "-coloredShadow" + (glowEffect.isInner()? "-inner": "-outer"));
                    finalCompositeElement.setAttribute("in", "SourceGraphic");
                    finalCompositeElement.setAttribute("operator", "over");
                    finalCompositeElement.setAttribute("result", layerName + "-finalComposite" + (glowEffect.isInner()? "-inner": "-outer"));
                    filtersElement.appendChild(finalCompositeElement);

                    Element feBlend = document.createElement("feBlend");
                    filtersElement.appendChild(feBlend);
                    feBlend.setAttribute("in", "SourceGraphic");
                    feBlend.setAttribute("in2", layerName + "-finalComposite" + (glowEffect.isInner()? "-inner": "-outer"));
                    feBlend.setAttribute("result", layerName + "-finalGlow" + (glowEffect.isInner()? "-inner": "-outer"));
                    filtersElement.appendChild(feBlend);

                    String blendMode = "normal";
                    if (SVG_BLEND_MODES.containsKey(glowEffect.getBlendMode())) {
                        blendMode = SVG_BLEND_MODES.get(glowEffect.getBlendMode());

                        if (blendMode.equals("overlay")) {  //might not work in some browsers
                            blendMode = "normal";
                        }
                    }
                    feBlend.setAttribute("mode", blendMode);

                    //add it to the merge list
                    elementsToMerge.add(feBlend);
                }
            } else if (effect instanceof BevelEffect) {
                BevelEffect blEffect = (BevelEffect)effect;
                Comment innerComment = document.createComment("BEVEL");
                filtersElement.appendChild(innerComment);

                int angle = blEffect.getAngle();
                int style = blEffect.getBevelStyle();
                BlendMode blendMode = blEffect.getBlendMode();
                BlendMode blendShadowMode = blEffect.getBlendShadowMode();
                int blur = blEffect.getBlur();
                int direction = blEffect.getDirection();    //PHOTOSHOP Up or Down
                Color highlightColor = blEffect.getHighlightColor();
                Color shadowColor = blEffect.getShadowColor();
                float highlightOpacity = blEffect.getHighlightOpacity();
                float shadowOpacity = blEffect.getShadowOpacity();
                int strength = blEffect.getStrength();

                Element gaussianBlur = document.createElement("feGaussianBlur");
                filtersElement.appendChild(gaussianBlur);
                gaussianBlur.setAttribute("in", "SourceGraphic");
                gaussianBlur.setAttribute("stdDeviation", blur+"");     //TO DO - use blur variable
                gaussianBlur.setAttribute("result", layerName + "-bevelBlur");

                Element specularLighting = document.createElement("feSpecularLighting");
                filtersElement.appendChild(specularLighting);
                specularLighting.setAttribute("in", layerName + "-bevelBlur");
                specularLighting.setAttribute("surfaceScale", "5");
                specularLighting.setAttribute("specularConstant", "0.75");
                specularLighting.setAttribute("specularExponent", "5");
                specularLighting.setAttribute("result", layerName + "-bevelSpecularLighting");
                specularLighting.setAttribute("style", "lighting-color:rgb(" + highlightColor.getRed() + ", " + highlightColor.getGreen()
                        + ", " + highlightColor.getBlue() +")");

                System.out.println(highlightColor);
                System.out.println(shadowColor);
                System.out.println(highlightOpacity);
                System.out.println(shadowOpacity);

                Element fePoint = document.createElement("fePointLight");
                specularLighting.appendChild(fePoint);
                fePoint.setAttribute("x", (Math.cos(Math.toRadians(angle))*200)+"");
                fePoint.setAttribute("y", (Math.sin(Math.toRadians(angle))*200)+"");
                fePoint.setAttribute("z", "10");

                Element feCompositeFirst = document.createElement("feComposite");
                filtersElement.appendChild(feCompositeFirst);
                feCompositeFirst.setAttribute("in", layerName + "-bevelSpecularLighting");
                feCompositeFirst.setAttribute("in2", "SourceAlpha");
                feCompositeFirst.setAttribute("operator", "in");
                feCompositeFirst.setAttribute("result", layerName + "-bevelSpecComposite");

                Element finalComposite = document.createElement("feComposite");
                filtersElement.appendChild(finalComposite);
                finalComposite.setAttribute("in", "SourceGraphic");
                finalComposite.setAttribute("in2", layerName + "-bevelSpecComposite");
                finalComposite.setAttribute("operator", "arithmetic");
                finalComposite.setAttribute("k1", "0");
                finalComposite.setAttribute("k2", "1");
                finalComposite.setAttribute("k3", "1");
                finalComposite.setAttribute("k4", "0");
                finalComposite.setAttribute("result", layerName + "-bevelFilter");

                elementsToMerge.add(finalComposite);

                //TO DO - use direction for inner bevel (emboss)
                //TO DO - use the blendMode

            } else if (effect instanceof SolidFillEffect){
                Comment innerComment = document.createComment("SOLID FILL");
                filtersElement.appendChild(innerComment);

                SolidFillEffect slEffect = (SolidFillEffect)effect;
                Color highlightColor = slEffect.getHighlightColor();
                String colorString = "rgb(" + highlightColor.getRed() + "," + highlightColor.getGreen() + "," + highlightColor.getBlue() + ")";
                float opacity = slEffect.getOpacity();
                BlendMode blendMode = slEffect.getBlendMode();

                if (blendMode.equals(BlendMode.OVERLAY)){

                    Element flood = document.createElement("feFlood");
                    filtersElement.appendChild(flood);
                    flood.setAttribute("result", layerName + "-floodedSlFill");
                    flood.setAttribute("style", "flood-color:" + colorString + ";flood-opacity: " + opacity);

                    Element composite = document.createElement("feComposite");
                    filtersElement.appendChild(composite);
                    composite.setAttribute("operator", "in");
                    composite.setAttribute("in", layerName + "-floodedSlFill");
                    composite.setAttribute("in2", "SourceGraphic");
                    composite.setAttribute("result", layerName +"-fillColorFlood");

                    /*Overlay blend mode*/
                    Element feColorMatrix = document.createElement("feColorMatrix");
                    filtersElement.appendChild(feColorMatrix);
                    feColorMatrix.setAttribute("type", "luminanceToAlpha");
//                    feColorMatrix.setAttribute("in", layerName +"-fillColorFlood");
                    feColorMatrix.setAttribute("result", layerName + "-screenMask");

                    Element feComponentTransfer = document.createElement("feComponentTransfer");
                    filtersElement.appendChild(feComponentTransfer);
                    feComponentTransfer.setAttribute("result", layerName + "-multiplyMask");

                    Element feFuncA = document.createElement("feFuncA");
                    feComponentTransfer.appendChild(feFuncA);
                    feFuncA.setAttribute("type", "linear");
                    feFuncA.setAttribute("slope", "-1");
                    feFuncA.setAttribute("intercept", "1");

                    Element feBlend = document.createElement("feBlend");
                    feBlend.setAttribute("mode", "screen");
                    feBlend.setAttribute("in", "BackgroundImage");
                    feBlend.setAttribute("in2", "SourceGraphic");
                    feBlend.setAttribute("result", layerName + "-screen");
                    filtersElement.appendChild(feBlend);

                    Element feBlend2 = document.createElement("feBlend");
                    feBlend2.setAttribute("mode", "multiply");
                    feBlend2.setAttribute("in", "BackgroundImage");
                    feBlend2.setAttribute("in2", "SourceGraphic");
                    feBlend2.setAttribute("result", layerName + "-multiply");
                    filtersElement.appendChild(feBlend2);

                    Element feComposite = document.createElement("feComposite");
                    filtersElement.appendChild(feComposite);
                    feComposite.setAttribute("operator", "in");
                    feComposite.setAttribute("in", layerName + "-screen");
                    feComposite.setAttribute("in2", layerName + "-screenMask");
                    feComposite.setAttribute("result", layerName + "-maskedScreen");

                    Element feCompositeMultiply = document.createElement("feComposite");
                    filtersElement.appendChild(feCompositeMultiply);
                    feCompositeMultiply.setAttribute("operator", "multiply");
                    feCompositeMultiply.setAttribute("in", layerName + "-multiply");
                    feCompositeMultiply.setAttribute("in2", layerName + "-multiplyMask");
                    feCompositeMultiply.setAttribute("result", layerName + "-maskedMultiply");

                    Element finalComposite = document.createElement("feComposite");
                    filtersElement.appendChild(finalComposite);
                    finalComposite.setAttribute("in", layerName + "-maskedScreen");
                    finalComposite.setAttribute("in2", layerName + "-maskedMultiply");
                    finalComposite.setAttribute("operator", "arithmetic");
                    finalComposite.setAttribute("k1", "0");
                    finalComposite.setAttribute("k2", "1");
                    finalComposite.setAttribute("k3", "1");
                    finalComposite.setAttribute("k4", "0");
                    finalComposite.setAttribute("result", layerName + "-OverlayEffect");

                } else {
                    Element floodElement = document.createElement("feFlood");
                    floodElement.setAttribute("flood-color", colorString);
                    floodElement.setAttribute("flood-opacity", opacity+"");
                    floodElement.setAttribute("result", "flood-solid");
                    filtersElement.appendChild(floodElement);

                    Element composite = document.createElement("feComposite");
                    composite.setAttribute("in", "flood-solid");
                    composite.setAttribute("in2", "SourceAlpha");
                    composite.setAttribute("operator", "in");
                    composite.setAttribute("result", layerName + "-solidFillEffect");
                    filtersElement.appendChild(composite);

                    //add it to the merge list
                    elementsToMerge.add(composite);
                }
            }
        }

        /*Merge all the effects, to render the final SVG*/
        if (elementsToMerge.size() > 0){
            Element finalEffectsMerge = document.createElement("feMerge");
            finalEffectsMerge.setAttribute("result", layerName + "-finalMerge");
            filtersElement.appendChild(finalEffectsMerge);

            for (Element elem: elementsToMerge) {
                Element elemMerge = document.createElement("feMergeNode");
                elemMerge.setAttribute("in", elem.getAttribute("result"));
                finalEffectsMerge.appendChild(elemMerge);
            }

            String blendMode = "normal";
            if (SVG_BLEND_MODES.containsKey(l.getLayerBlendMode())) {
                blendMode = SVG_BLEND_MODES.get(l.getLayerBlendMode());

                if (blendMode.equals("overlay")) {  //might not work in some browsers
                    blendMode = "normal";
                }

                Element layerBlend = document.createElement("feBlend");
                filtersElement.appendChild(layerBlend);
                layerBlend.setAttribute("mode", blendMode);
                layerBlend.setAttribute("in2", "BackgroundImage");

                //blend the merge filter with the background image
                layerBlend.setAttribute("in", layerName + "-finalMerge");
            }   //if the blend is normal, then it does not need to do anything
        } else {    //no filter was applied (no need to merge), so add a new one to blend the layer (if necessary)
            String blendMode = "normal";
            if (SVG_BLEND_MODES.containsKey(l.getLayerBlendMode())) {
                blendMode = SVG_BLEND_MODES.get(l.getLayerBlendMode());

                if (blendMode.equals("overlay")) {
                    /*Overlay blend mode*/
                    Element feColorMatrix = document.createElement("feColorMatrix");
                    filtersElement.appendChild(feColorMatrix);
                    feColorMatrix.setAttribute("type", "luminanceToAlpha");
                    feColorMatrix.setAttribute("result", layerName + "-screenMask");

                    Element feComponentTransfer = document.createElement("feComponentTransfer");
                    filtersElement.appendChild(feComponentTransfer);
                    feComponentTransfer.setAttribute("result", layerName + "-multiplyMask");

                    Element feFuncA = document.createElement("feFuncA");
                    feComponentTransfer.appendChild(feFuncA);
                    feFuncA.setAttribute("type", "linear");
                    feFuncA.setAttribute("slope", "-1");
                    feFuncA.setAttribute("intercept", "1");

                    Element feBlend = document.createElement("feBlend");
                    feBlend.setAttribute("mode", "screen");
                    feBlend.setAttribute("in", "BackgroundImage");
                    feBlend.setAttribute("in2", "SourceGraphic");
                    feBlend.setAttribute("result", layerName + "-screen");
                    filtersElement.appendChild(feBlend);

                    Element feBlend2 = document.createElement("feBlend");
                    feBlend2.setAttribute("mode", "multiply");
                    feBlend2.setAttribute("in", "BackgroundImage");
                    feBlend2.setAttribute("in2", "SourceGraphic");
                    feBlend2.setAttribute("result", layerName + "-multiply");
                    filtersElement.appendChild(feBlend2);

                    Element feComposite = document.createElement("feComposite");
                    filtersElement.appendChild(feComposite);
                    feComposite.setAttribute("operator", "in");
                    feComposite.setAttribute("in", layerName + "-screen");
                    feComposite.setAttribute("in2", layerName + "-screenMask");
                    feComposite.setAttribute("result", layerName + "-maskedScreen");

                    Element feCompositeMultiply = document.createElement("feComposite");
                    filtersElement.appendChild(feCompositeMultiply);
                    feCompositeMultiply.setAttribute("operator", "multiply");
                    feCompositeMultiply.setAttribute("in", layerName + "-multiply");
                    feCompositeMultiply.setAttribute("in2", layerName + "-multiplyMask");
                    feCompositeMultiply.setAttribute("result", layerName + "-maskedMultiply");

                    Element finalComposite = document.createElement("feComposite");
                    filtersElement.appendChild(finalComposite);
                    finalComposite.setAttribute("in", layerName + "-maskedScreen");
                    finalComposite.setAttribute("in2", layerName + "-maskedMultiply");
                    finalComposite.setAttribute("operator", "arithmetic");
                    finalComposite.setAttribute("k1", "0");
                    finalComposite.setAttribute("k2", "1");
                    finalComposite.setAttribute("k3", "1");
                    finalComposite.setAttribute("k4", "0");
                    finalComposite.setAttribute("result", layerName + "-OverlayEffect");
                } else {
                    Element layerBlend = document.createElement("feBlend");
                    filtersElement.appendChild(layerBlend);
                    layerBlend.setAttribute("mode", blendMode);
                    layerBlend.setAttribute("in2", "BackgroundImage");
                }
            }
        }
    }

    /**
     * Gets the layer image as a SVG node.
     * @param l
     * @param document
     * @return
     */
    private Element getSVGLayerElement(Layer l, Document document) {
        SVGOMImageElement image = (SVGOMImageElement) document.createElementNS(svgNS, "image");

        ImageHandlerBase64Encoder dih = new ImageHandlerBase64Encoder();
        try {
            dih.handleHREF((java.awt.image.RenderedImage) l.getImage(), image, SVGGeneratorContext.createDefault(document));
        } catch (SVGGraphics2DIOException e) {
            e.printStackTrace();
        }

        Element innerLayer = document.createElementNS(svgNS, "g");
        image.setAttribute("x", Integer.toString(l.getX()));
        image.setAttribute("y", Integer.toString(l.getY()));
        image.setAttribute("width", Integer.toString(l.getImage().getWidth()));
        image.setAttribute("height", Integer.toString(l.getImage().getHeight()));
        innerLayer.appendChild(image);
        return innerLayer;
    }

    /**
     * Converts the SVG document to a string and writes it to the supplied output stream
     * @param document
     * @param stream
     */
    private void writeSVG(Document document, OutputStream stream) {
        String result = "";
        SVGTranscoder transcoder = new SVGTranscoder();
        TranscoderInput svgInput = new TranscoderInput(document);
        TranscoderOutput out;
        try {
            out = new TranscoderOutput(new OutputStreamWriter(stream));
            transcoder.transcode(svgInput, out);
        } catch (TranscoderException e) {
            e.printStackTrace();
        }
    }
}
