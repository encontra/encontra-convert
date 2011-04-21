package pt.inevo.encontra.convert;

import org.apache.batik.dom.svg.SVGDOMImplementation;
import org.apache.batik.dom.svg.SVGOMDocument;
import org.apache.batik.dom.svg.SVGOMImageElement;

import org.apache.batik.svggen.*;

import org.apache.batik.transcoder.TranscoderException;
import org.apache.batik.transcoder.TranscoderInput;
import org.apache.batik.transcoder.TranscoderOutput;
import org.apache.batik.transcoder.svg2svg.SVGTranscoder;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import psd.Layer;
import psd.Psd;
import psd.parser.layer.LayerType;

import java.io.*;
import java.util.logging.Logger;

public class PSDConverter implements Converter {

    private static Logger log = Logger.getLogger(PSDConverter.class.getName());

    private static String svgNS = "http://www.w3.org/2000/svg";

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
            folderLayer.appendChild(innerLayer);

            return folderLayer;
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
