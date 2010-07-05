package pt.inevo.encontra.convert;

import java.io.InputStream;
import java.io.OutputStream;

public interface Converter {
    public void convertToMimeType(String mimetype, InputStream input, OutputStream output);
}