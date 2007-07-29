package lua.compile;

import lua.value.LString;

/** 
 * TODO: eliminate this class, 
 * use LString instead, and just maintain a 
 * single static map from reserved words to 
 * reserved word opcodes or enum values.
 */
public class TString  extends LString {
    byte reserved;
	public TString(String name) {
		super( name );
	}
}
