package pt.inevo.encontra.convert;

import java.io.*;


import java.io.File;

import junit.framework.TestCase;

/**
 *
 * @author nfgs
 */
public class DXFConverterTest extends TestCase {

    private DXFConverter converter;
    private static String _test_filename="/5_lines.dxf";
    private static String _output_filename="5_lines.svg";

    public DXFConverterTest(String testName) {
        super(testName);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        converter=new DXFConverter();
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }

    /**
     * Test of Read method, of class DXFDrawing.
     */
    public void testRead() {

        try {
            
            InputStream input = getClass().getResourceAsStream(_test_filename);
            OutputStream output=new FileOutputStream(new File(_output_filename));
             converter.convertToMimeType("dxf",input,output);
        } catch (FileNotFoundException e) {
            e.printStackTrace();

        }



        // TODO - Add SVGViewer 
        // SVGViewer viewer=new SVGViewer();
        //viewer.setSVG(instance.getSVGDocument());
        //viewer.frame.setVisible(true);
        //viewer.waitUntilClosed();

        //assertTrue(result);
    }

}
