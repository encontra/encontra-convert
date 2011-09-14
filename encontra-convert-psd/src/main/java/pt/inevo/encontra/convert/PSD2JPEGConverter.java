package pt.inevo.encontra.convert;


import psd.Layer;
import psd.Psd;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.logging.Logger;

public class PSD2JPEGConverter implements Converter {

    private static Logger log = Logger.getLogger(PSD2JPEGConverter.class.getName());

    /*Supported Mime-types*/
    public static String JPEG_MIMETYPE = "image/jpeg";

    /**
     * Converts the PSD file to a JPEG image.
     * @param input
     * @param output
     */
    private void convertToJPEG(InputStream input, OutputStream output) {
        try {
            // Parse the psd file
            Psd psdFile = new Psd(input);

            Layer baseLayer = psdFile.getBaseLayer();
            if (baseLayer.getImage() != null){
                ImageIO.write(baseLayer.getImage(), "jpeg", output);
            }

        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    @Override
    public void convertToMimeType(String mimetype, InputStream input, OutputStream output) {
        if (mimetype.equals(JPEG_MIMETYPE)) {
            convertToJPEG(input, output);
        } else {
            log.warning("Couldn't convert to the specified mime type.");
        }
    }
}
