package lua.compile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.Reader;
import java.net.URL;

import junit.framework.TestCase;
import lua.Print;
import lua.StackState;
import lua.io.LoadState;
import lua.io.Proto;

public class CompilerUnitTests extends TestCase {
	
	public static String jar = "jar:file:lua5.1-tests.zip";
	public static String dir = "/lua5.1-tests";
	
	public void testAll()        { doTest("all.lua"); }
	public void testApi()        { doTest("api.lua"); }
	public void testAttrib()     { doTest("attrib.lua"); }
	public void testBig()        { doTest("big.lua"); }
	public void testCalls()      { doTest("calls.lua"); }
	public void testChecktable() { doTest("checktable.lua"); }
	public void testClosure()    { doTest("closure.lua"); }
	public void testCode()       { doTest("code.lua"); }
	public void testConstruct()  { doTest("constructs.lua"); }
	public void testDb()         { doTest("db.lua"); }
	public void testErrors()     { doTest("errors.lua"); }
	public void testEvents()     { doTest("events.lua"); }
	public void testFiles()      { doTest("files.lua"); }
	public void testGc()         { doTest("gc.lua"); }
	public void testLiterals()   { doTest("literals.lua"); }
	public void testLocals()     { doTest("locals.lua"); }
	public void testMain()       { doTest("main.lua"); }
	public void testMath()       { doTest("math.lua"); }
	public void testNextvar()    { doTest("nextvar.lua"); }
	public void testPm()         { doTest("pm.lua"); }
	public void testSort()       { doTest("sort.lua"); }
	public void testStrings()    { doTest("strings.lua"); }
	public void testVararg()     { doTest("vararg.lua"); }
	public void testVerybig()    { doTest("verybig.lua"); }
	
	public void doTest( String file ) {
		try {
			// load source from jar
			String path = jar + "!" + dir + "/" + file;
			byte[] lua = bytesFromJar( path );
			
			// compile in memory
			InputStream is = new ByteArrayInputStream( lua );
	    	Reader r = new InputStreamReader( is );
	    	Proto p = Compiler.compile(r, file);
	    	String actual = protoToString( p );
			
			// load expected value from jar
			byte[] luac = bytesFromJar( path + "c" );
			Proto e = loadFromBytes( luac, file );
	    	String expected = protoToString( e );

			// compare results
			assertEquals( expected, actual );
			
		} catch (IOException e) {
			fail( e.toString() );
		}
	}
	
	private byte[] bytesFromJar(String path) throws IOException {
		URL url = new URL(path);
		InputStream is = url.openStream();
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		byte[] buffer = new byte[2048];
		int n;
		while ( (n = is.read(buffer)) >= 0 )
			baos.write( buffer, 0, n );
		is.close();
		return baos.toByteArray();
	}
	
	private Proto loadFromBytes(byte[] bytes, String script) throws IOException {
		StackState state = new StackState();
		InputStream is = new ByteArrayInputStream( bytes );
		return LoadState.undump(state, is, script);
	}
	
	private String protoToString(Proto p) {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		PrintStream ps = new PrintStream( baos );
		Print.ps = ps;
		new Print().printFunction(p, true);
		return baos.toString();
	}
	
}
