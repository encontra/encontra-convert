package pt.inevo.encontra.convert;

import java.io.InputStream;
import java.io.OutputStream;

/**
 * Class that contains the conversion services for Photoshop(c) Files (PSD) to supported mime-types.
 * Supported mime-types: image/svg+xml, image/png, image/jpeg
 * @author rpd
 */
public class PSDConverterFactory extends ConverterFactory implements Converter {

    public PSDConverterFactory() {
        addConverter(PSD2JPEGConverter.JPEG_MIMETYPE, PSD2JPEGConverter.class);
        addConverter(PSD2PNGConverter.PNG_MIMETYPE, PSD2PNGConverter.class);
        addConverter(PSD2SVGConverter.SVG_MIMETYPE, PSD2SVGConverter.class);
    }

    @Override
    public void convertToMimeType(String mimetype, InputStream input, OutputStream output) {
        Converter converter = (Converter)createConverter(mimetype);
        if (converter != null) {
            converter.convertToMimeType(mimetype, input, output);
        }
    }
}
