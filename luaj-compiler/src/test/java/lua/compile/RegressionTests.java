package lua.compile;

/**
 * Framework to add regression tests as problem areas are found.
 * 
 * @author jrosebor
 */
public class RegressionTests extends AbstractUnitTests {
	
	public RegressionTests() {
		super( "regressions" );
	}
	
	public void testMathRandomseed() { doTest("mathrandomseed.lua"); }

}
