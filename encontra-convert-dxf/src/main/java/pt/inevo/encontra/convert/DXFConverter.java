package pt.inevo.encontra.convert;


import org.apache.batik.transcoder.TranscoderException;
import org.apache.batik.transcoder.TranscoderInput;
import org.apache.batik.transcoder.TranscoderOutput;
import org.apache.batik.transcoder.svg2svg.SVGTranscoder;
import org.kabeja.dxf.DXFDocument;
import org.kabeja.parser.DXFParser;
import org.kabeja.parser.ParseException;
import org.kabeja.parser.Parser;
import org.kabeja.parser.ParserBuilder;
import org.kabeja.svg.tools.DXFSAXDocumentFactory;
import org.w3c.dom.svg.SVGDocument;
import org.xml.sax.SAXException;

import java.io.*;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;


public class DXFConverter implements Converter{

    private static Logger log=Logger.getLogger(DXFConverter.class.getName());

    private Parser parser;


    public DXFConverter() {
        parser = ParserBuilder.createDefaultParser();

    }
    
    public void convertToMimeType(String mimetype, InputStream input, OutputStream output) {
        try {
            parser.parse(input, DXFParser.DEFAULT_ENCODING);
            DXFDocument dfxDocument=parser.getDocument();

            DXFSAXDocumentFactory factory=new DXFSAXDocumentFactory();
            SVGDocument document = factory.createDocument(dfxDocument, new HashMap());

            SVGTranscoder transcoder = new SVGTranscoder();
            TranscoderInput svgInput = new TranscoderInput(document);

            OutputStreamWriter osw=new OutputStreamWriter(output);
			TranscoderOutput out = new TranscoderOutput(osw);
			transcoder.transcode(svgInput, out);

            osw.flush();
            osw.close();

        } catch (IOException e) {
            log.log(Level.SEVERE, null, e);
        } catch (TranscoderException e) {
             log.log(Level.SEVERE, null, e);
        } catch (SAXException e) {
            log.log(Level.SEVERE, null, e);
        } catch (ParseException e) {
            log.log(Level.SEVERE, null, e);
        }
    }
}
