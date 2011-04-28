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

import java.awt.*;
import java.io.*;
import java.util.*;
import java.util.List;
import java.util.logging.Logger;

public class PSDConverter implements Converter {

    private static Logger log = Logger.getLogger(PSDConverter.class.getName());

    private static String svgNS = "http://www.w3.org/2000/svg";

    private static final Map<BlendMode, String> SVG_BLEND_MODES =
            Collections.unmodifiableMap(new HashMap<BlendMode, String>() {{
                this.put(BlendMode.NORMAL, "normal");
                this.put(BlendMode.OVERLAY, "normal");
                this.put(BlendMode.MULTIPLY, "multiply");
                this.put(BlendMode.SCREEN, "screen");
                this.put(BlendMode.DARKEN, "darken");
                this.put(BlendMode.LIGHTEN, "lighten");
            }});

    public PSDConverter() {
    }

    public void convertToMimeType(String mimetype, InputStream input, OutputStream output) {

        try {
            Psd psdFile = new Psd(input);

            // Get a DOMImplementation.
            SVGDOMImplementation domImpl = new SVGDOMImplementation();

            // Create an instance of org.w3c.dom.Document.
            SVGOMDocument document = (SVGOMDocument) domImpl.createDocument(svgNS, "svg", null);

            // Get the root element (the 'svg' element).
            Element svgRoot = document.getDocumentElement();

            // Set the width and height attributes on the root 'svg' element.
            svgRoot.setAttributeNS(null, "width", String.valueOf(psdFile.getWidth()));
            svgRoot.setAttributeNS(null, "height", String.valueOf(psdFile.getHeight()));
            svgRoot.setAttributeNS(null, "depth", String.valueOf(psdFile.getDepth()));
            svgRoot.setAttributeNS(null, "channels", String.valueOf(psdFile.getChannelsCount()));

            int numberLayers = psdFile.getLayersCount();
            for (int i = 0; i < numberLayers; i++) {
                Layer l = psdFile.getLayer(i);
                parseLayer(l, document, svgRoot);
            }

            writeSVG(document, output);

        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    private Element parseLayer(Layer l, Document document, Element root) {

        if (l.getType().equals(LayerType.FOLDER)) {
            Element folderLayer = document.createElementNS(svgNS, "g");
            folderLayer.setAttribute("name", l.toString());
            folderLayer.setAttribute("type", l.getType().toString());
            root.appendChild(folderLayer);

            int internalLayersNumber = l.getLayersCount();
            for (int j = 0; j < internalLayersNumber; j++) {
                Element layerElement = parseLayer(l.getLayer(j), document, root);
                folderLayer.appendChild(layerElement);
            }
            return folderLayer;

        } else {
            Element folderLayer = document.createElementNS(svgNS, "g");
            folderLayer.setAttribute("name", l.toString());
            folderLayer.setAttribute("type", l.getType().toString());
            root.appendChild(folderLayer);
            Element innerLayer = getSVGLayerElement(l, document);

            parseEffects(l, folderLayer, document, root);

            folderLayer.appendChild(innerLayer);

            return folderLayer;
        }
    }

    private void parseEffects(Layer l, Element layerElement, Document document, Element root){
        List<PSDEffect> effects = l.getEffectsList();

        Element layerEffectsDefs = null;
        Element filtersElement = null;
        String layerName = l.toString().replaceAll(" ", "-");
        if (effects.size() > 0){
            layerEffectsDefs = document.createElement("defs");
            layerElement.appendChild(layerEffectsDefs);

            filtersElement = document.createElement("filter");
            layerEffectsDefs.appendChild(filtersElement);

            String width = root.getAttribute("width");
            String height = root.getAttribute("height");

            filtersElement.setAttribute("width", width);
            filtersElement.setAttribute("height", height);

            //TO DO - must fix the sizing of the bounding box?
            filtersElement.setAttribute("width", "200%");
            filtersElement.setAttribute("height", "200%");
            filtersElement.setAttribute("id", layerName + "-filters");

            filtersElement.setAttribute("filterUnits", "userSpaceOnUse");
            layerElement.setAttribute("filter", "url(#" + filtersElement.getAttribute("id") + ")");
        }

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
                    gaussianBlurElement.setAttribute("stdDeviation", dpEffect.getBlur() + "");

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
                    gaussianBlurElement.setAttribute("stdDeviation", dpEffect.getBlur() + "");

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
                    gaussianBlurElement.setAttribute("stdDeviation", glowEffect.getBlur()+"");
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
                    gaussianBlurElement.setAttribute("stdDeviation", glowEffect.getBlur()+"");
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
                    }
                    feBlend.setAttribute("mode", blendMode);

                    //add it to the merge list
                    elementsToMerge.add(feBlend);
                }
            } else if (effect instanceof BevelEffect) {
                BevelEffect blEffect = (BevelEffect)effect;
                Comment innerComment = document.createComment("BEVEL");
                filtersElement.appendChild(innerComment);

            } else if (effect instanceof SolidFillEffect){
                Comment innerComment = document.createComment("SOLID FILL");
                filtersElement.appendChild(innerComment);

                SolidFillEffect slEffect = (SolidFillEffect)effect;

                Color highlightColor = slEffect.getHighlightColor();
                String colorString = "rgb(" + highlightColor.getRed() + "," + highlightColor.getGreen() + "," + highlightColor.getBlue() + ")";
                float opacity = slEffect.getOpacity();

                filtersElement.setAttribute("width", "100%");
                filtersElement.setAttribute("height", "100%");
                filtersElement.setAttribute("x", "0%");
                filtersElement.setAttribute("y", "0%");
                filtersElement.setAttribute("filterUnits", "objectBoundingBox");

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

        /*Merge all the effects, to render the final SVG*/
        if (filtersElement!=null){
            Element finalEffectsMerge = document.createElement("feMerge");
            filtersElement.appendChild(finalEffectsMerge);

            for (Element elem: elementsToMerge) {
                Element elemMerge = document.createElement("feMergeNode");
                elemMerge.setAttribute("in", elem.getAttribute("result"));
                finalEffectsMerge.appendChild(elemMerge);
            }
        }
    }

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

    private void writeSVG(Document document, OutputStream stream) {
        String result = "";
        SVGTranscoder transcoder = new SVGTranscoder();
        TranscoderInput svgInput = new TranscoderInput(document);
        TranscoderOutput out;
        try {
            out = new TranscoderOutput(new OutputStreamWriter(stream));
            transcoder.transcode(svgInput, out);
        } catch (TranscoderException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }
}
