package pt.inevo.encontra.convert;


import org.apache.batik.transcoder.Transcoder;
import org.apache.batik.transcoder.TranscoderException;
import org.apache.batik.transcoder.TranscoderInput;
import org.apache.batik.transcoder.TranscoderOutput;

import org.apache.batik.transcoder.image.JPEGTranscoder;
import org.apache.batik.transcoder.image.PNGTranscoder;
import org.apache.batik.transcoder.image.TIFFTranscoder;


import java.io.*;


public class SVGConverter implements Converter{

    protected final ConverterFactory factory = new ConverterFactory();

    public SVGConverter() {

        // Add the default transcoders which come with Batik
        factory.addConverter("image/jpeg", JPEGTranscoder.class);
        factory.addConverter("image/jpg", JPEGTranscoder.class);
        factory.addConverter("image/png", PNGTranscoder.class);
        factory.addConverter("image/tiff", TIFFTranscoder.class);
    }
    public void convertToMimeType(String mimetype, InputStream input, OutputStream output) {
        Transcoder t = (Transcoder)factory.createConverter(mimetype);


        // Set the transcoder input and output.
        TranscoderInput tinput = new TranscoderInput(input);
        try {
            TranscoderOutput toutput = new TranscoderOutput(output);

            // Perform the transcoding.
            t.transcode(tinput, toutput);
            output.flush();
            output.close();
        } catch (IOException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        } catch (TranscoderException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
    }

}
