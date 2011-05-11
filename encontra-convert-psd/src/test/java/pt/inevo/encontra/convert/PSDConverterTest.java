package pt.inevo.encontra.convert;

import java.io.*;
import java.io.File;
import junit.framework.TestCase;

/**
 * Simple class to test the PSD Converter
 * @author rpd
 */
public class PSDConverterTest extends TestCase {

    private PSDConverter converter;
    private static String _test_filename="/color_pill.psd";
    private static String _output_filename="color_pill.svg";
    private static String _output_filename_jpg="color_pill.jpg";

    public PSDConverterTest(String testName) {
        super(testName);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        converter=new PSDConverter();
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }

    /**
     * Test of converting a PSD file to a SVG.
     */
    public void testConvertToSVG() {
        try {
            InputStream input = getClass().getResourceAsStream(_test_filename);
            OutputStream output=new FileOutputStream(new File(_output_filename));
            converter.convertToMimeType("svg",input,output);
        } catch (FileNotFoundException e) {
            e.printStackTrace();

        }
    }

    /**
     * Test of converting a PSD file to a SVG.
     */
    public void testConvertToJPG() {
        try {
            InputStream input = getClass().getResourceAsStream(_test_filename);
            OutputStream output=new FileOutputStream(new File(_output_filename_jpg));
//            converter.convertToMimeType("image/jpeg",input,output);
            converter.convertToMimeType("image/png",input,output);
        } catch (FileNotFoundException e) {
            e.printStackTrace();

        }
    }
}