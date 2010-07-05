package pt.inevo.encontra.convert;

import java.util.HashMap;
import java.util.Map;


public class ConverterFactory<T> {
    protected final static Map<String,Class> converters = new HashMap<String,Class>();
    /**
     * Create a converter for a specified MIME type.
     * @param mimeType The MIME type of the destination format
     * @return A suitable converter, or <code>null</code> if one cannot be found
     */
    public T createConverter(String mimeType) {
        Class transcoderClass = converters.get(mimeType);
        if (transcoderClass == null) {
            return null;
        } else {
            try {
                return (T) transcoderClass.newInstance();
            } catch (Exception ex) {
                return null;
            }
        }
    }

    /**
     * Add a mapping from the specified MIME type to a converter.
     * Note: The converter must have a no-argument constructor.
     * @param mimeType The MIME type of the converter
     * @param converterClass The <code>Class</code> object for the converter.
     */
    public void addConverter(String mimeType, Class converterClass) {
        converters.put(mimeType, converterClass);
    }

    /**
     * Remove the mapping from a specified MIME type.
     * @param mimeType The MIME type to remove from the mapping.
     */
    public void removeConverter(String mimeType) {
        converters.remove(mimeType);
    }
}