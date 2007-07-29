package lua.compile;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import lua.io.Proto;
import lua.value.LBoolean;
import lua.value.LNil;
import lua.value.LNumber;
import lua.value.LString;
import lua.value.LValue;

public class DumpState {

	/** mark for precompiled code (`<esc>Lua') */
	public static final String LUA_SIGNATURE	= "\033Lua";

	/** for header of binary files -- this is Lua 5.1 */
	public static final int LUAC_VERSION		= 0x51;

	/** for header of binary files -- this is the official format */
	public static final int LUAC_FORMAT		= 0;

	/** size of header of binary files */
	public static final int LUAC_HEADERSIZE		= 12;

	/** expected lua header bytes */
	private static final byte[] LUAC_HEADER_SIGNATURE = { '\033', 'L', 'u', 'a' };

	// header fields
	private static final int IS_LITTLE_ENDIAN = 0;
	private static final int SIZEOF_INT = 4;
	private static final int SIZEOF_SIZET = 4;
	private static final int SIZEOF_INSTRUCTION = 4;
	private static final int SIZEOF_LUA_NUMBER = 8;
	private static final int IS_NUMBER_INTEGRAL = 0;

	Compiler L;
	DataOutputStream writer;
	byte[] data;
	boolean strip;
	int status;

	public DumpState(Compiler L, OutputStream w, byte[] data, boolean strip) {
		this.L = L;
		this.writer = new DataOutputStream( w );
		this.data = data;
		this.strip = strip;
		this.status = 0;
	}

	//		#define DumpMem(b,n,size,D)	DumpBlock(b,(n)*(size),D)
//		#define DumpVar(x,D)	 	DumpMem(&x,1,sizeof(x),D)
//
	void dumpBlock(final byte[] b, int size) throws IOException {
		writer.write(b, 0, size);
	}

	void dumpChar(int b) throws IOException {
		writer.write( b );
	}

	void dumpInt(int x) throws IOException {
		writer.writeInt(x);
	}
	
	void dumpString(LString s) throws IOException {
		byte[] bytes = s.luaAsString().getBytes(); // TODO: UTF-8 convert here
		writer.write( bytes.length );
		writer.write( bytes );
	}
	
	void dumpNumber(double d) throws IOException {
		long l = Double.doubleToLongBits(d);
		writer.writeLong(l);
	}

	void dumpCode( final Proto f ) throws IOException {
		dumpInt( f.sizecode );
		for ( int i=0; i<f.sizecode; i++ )
			dumpInt( f.code[i] );
	}
	
	void dumpConstants(final Proto f) throws IOException {
		int i,n=f.sizek;
		 dumpInt(n);
		 for (i=0; i<n; i++)
		 {
		  final LValue o = f.k[i];
		  if ( o == LNil.NIL ) {
			  // do nothing
		  } else if ( o instanceof LBoolean ) {
			dumpChar(o.luaAsBoolean()? 1: 0);
		  } else if ( o instanceof LNumber ) {
			dumpNumber( o.luaAsDouble() );
		  } else if ( o instanceof LString ) {
			dumpString( (LString) o );
		  } else {
			  throw new IllegalArgumentException("bad type for "+o);
		  }
		 }
		 n=f.sizep;
		 dumpInt(n);
		 for (i=0; i<n; i++) 
			 dumpFunction(f.p[i], f.source);
		}
	
	void dumpDebug( final Proto f ) throws IOException {
		 int i,n;
		 n= (strip) ? 0 : f.lineinfo.length;
		 for ( i=0; i<n; i++) 
			 dumpInt(f.lineinfo[i]);
		 n= (strip) ? 0 : f.locvars.length;
		 dumpInt(n);
		 for (i=0; i<n; i++)
		 {
		  dumpString(f.locvars[i].varname);
		  dumpInt(f.locvars[i].startpc);
		  dumpInt(f.locvars[i].endpc);
		 }
		 n= (strip) ? 0 : f.sizeupvalues;
		 dumpInt(n);
		 for (i=0; i<n; i++) dumpString(f.upvalues[i]);	
	}
	
	void dumpFunction(final Proto f, final LString string) throws IOException {
		dumpString((f.source.equals(string) || strip) ? null : f.source);
		dumpInt(f.linedefined);
		dumpInt(f.lastlinedefined);
		dumpChar(f.nups);
		dumpChar(f.numparams);
		dumpChar(f.is_vararg? 1: 0);
		dumpChar(f.maxstacksize);
		dumpCode(f);
		dumpConstants(f);
		dumpDebug(f);
	}

	void dumpHeader() throws IOException {
		writer.write( LUAC_HEADER_SIGNATURE );
		writer.write( LUAC_VERSION );
		writer.write( LUAC_FORMAT );
		writer.write( IS_LITTLE_ENDIAN );
		writer.write( SIZEOF_INT );
		writer.write( SIZEOF_SIZET );
		writer.write( SIZEOF_INSTRUCTION );
		writer.write( SIZEOF_LUA_NUMBER );
		writer.write( IS_NUMBER_INTEGRAL );
	}

	/*
	** dump Lua function as precompiled chunk
	*/
	int dump( Compiler L, Proto f, OutputStream w, byte[] data, boolean strip ) throws IOException {
		DumpState D = new DumpState(L,w,data,strip);
		D.dumpHeader();
		D.dumpFunction(f,null);
		return D.status;
	}
}
