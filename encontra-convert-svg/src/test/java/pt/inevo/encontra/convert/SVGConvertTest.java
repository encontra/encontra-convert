package pt.inevo.encontra.convert;

import java.io.*;
import java.io.File;
import junit.framework.TestCase;

/**
 * Simple class to test the PSD Converter
 * @author rpd
 */
public class SVGConvertTest extends TestCase {

    private SVGConverter converter;
    private static String _test_filename="/inkscape_blend_layers.svg";
    private static String _output_filename="inkscape_blend_layers.png";

    public SVGConvertTest(String testName) {
        super(testName);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        converter=new SVGConverter();
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }

    /**
     * Test of converting a SVG file to a PNG.
     */
    public void testRead() {

        try {
            InputStream input = getClass().getResourceAsStream(_test_filename);
            OutputStream output=new FileOutputStream(new File(_output_filename));
            converter.convertToMimeType("image/png",input,output);
        } catch (FileNotFoundException e) {
            e.printStackTrace();

        }
    }
}