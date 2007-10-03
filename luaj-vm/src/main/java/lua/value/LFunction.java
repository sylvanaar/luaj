package lua.value;

import lua.VM;


public class LFunction extends LValue {

	public static final LString TYPE_NAME = new LString(Type.function.toString());
	
	public LString luaAsString() {
		return new LString( "function: "+hashCode() );
	}

	public void luaSetTable(VM vm, LValue table, LValue key, LValue val) {
		vm.push( this );
		vm.push( table );
		vm.push( key );
		vm.push( val );
		vm.lua_call( 3, 0 );
	}

	public void luaGetTable(VM vm, LValue table, LValue key) {
		vm.push( this );
		vm.push( table );
		vm.push( key );
		vm.lua_call( 2, 1 );
	}
	
	public LString luaGetType() {
		return TYPE_NAME;
	}
	
}
