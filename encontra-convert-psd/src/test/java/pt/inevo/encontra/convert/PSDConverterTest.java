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
    private static String _test_filename="/sigma.psd";
    private static String _output_filename="sigma.svg";

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
    public void testRead() {

        try {
            InputStream input = getClass().getResourceAsStream(_test_filename);
            OutputStream output=new FileOutputStream(new File(_output_filename));
            converter.convertToMimeType("psd",input,output);
        } catch (FileNotFoundException e) {
            e.printStackTrace();

        }
    }
}