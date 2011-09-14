package pt.inevo.encontra.convert;

import psd.Layer;
import psd.Psd;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.logging.Logger;

public class PSD2PNGConverter implements Converter {

    private static Logger log = Logger.getLogger(PSD2PNGConverter.class.getName());

    /*Supported Mime-types*/
    public static String PNG_MIMETYPE = "image/png";

    /**
     * Converts the PSD file to a PNG image.
     * @param input
     * @param output
     */
    private void convertToPNG(InputStream input, OutputStream output) {
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

    @Override
    public void convertToMimeType(String mimetype, InputStream input, OutputStream output) {
        if (mimetype.equals(PNG_MIMETYPE)) {
            convertToPNG(input, output);
        } else {
            log.warning("Couldn't convert to the specified mime type.");
        }
    }
}
