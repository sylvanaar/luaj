/*******************************************************************************
* Copyright (c) 2010 Luaj.org. All rights reserved.
*
* Permission is hereby granted, free of charge, to any person obtaining a copy
* of this software and associated documentation files (the "Software"), to deal
* in the Software without restriction, including without limitation the rights
* to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
* copies of the Software, and to permit persons to whom the Software is
* furnished to do so, subject to the following conditions:
*
* The above copyright notice and this permission notice shall be included in
* all copies or substantial portions of the Software.
* 
* THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
* IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
* FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
* AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
* LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
* OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
* THE SOFTWARE.
******************************************************************************/
package org.luaj.vm2.lua2java;

import java.io.CharArrayWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.luaj.vm2.Lua;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.ast.Block;
import org.luaj.vm2.ast.Chunk;
import org.luaj.vm2.ast.Exp;
import org.luaj.vm2.ast.FuncArgs;
import org.luaj.vm2.ast.FuncBody;
import org.luaj.vm2.ast.Name;
import org.luaj.vm2.ast.NameResolver;
import org.luaj.vm2.ast.ParList;
import org.luaj.vm2.ast.Stat;
import org.luaj.vm2.ast.TableConstructor;
import org.luaj.vm2.ast.TableField;
import org.luaj.vm2.ast.Visitor;
import org.luaj.vm2.ast.Exp.AnonFuncDef;
import org.luaj.vm2.ast.Exp.BinopExp;
import org.luaj.vm2.ast.Exp.Constant;
import org.luaj.vm2.ast.Exp.FieldExp;
import org.luaj.vm2.ast.Exp.FuncCall;
import org.luaj.vm2.ast.Exp.IndexExp;
import org.luaj.vm2.ast.Exp.MethodCall;
import org.luaj.vm2.ast.Exp.NameExp;
import org.luaj.vm2.ast.Exp.ParensExp;
import org.luaj.vm2.ast.Exp.UnopExp;
import org.luaj.vm2.ast.Exp.VarExp;
import org.luaj.vm2.ast.Exp.VarargsExp;
import org.luaj.vm2.ast.Stat.Assign;
import org.luaj.vm2.ast.Stat.Break;
import org.luaj.vm2.ast.Stat.FuncCallStat;
import org.luaj.vm2.ast.Stat.FuncDef;
import org.luaj.vm2.ast.Stat.GenericFor;
import org.luaj.vm2.ast.Stat.IfThenElse;
import org.luaj.vm2.ast.Stat.LocalAssign;
import org.luaj.vm2.ast.Stat.LocalFuncDef;
import org.luaj.vm2.ast.Stat.NumericFor;
import org.luaj.vm2.ast.Stat.RepeatUntil;
import org.luaj.vm2.ast.Stat.Return;
import org.luaj.vm2.ast.Stat.WhileDo;

public class JavaCodeGen {

	final Chunk chunk;
	final String packagename;
	final String classname;
	Writer writer;

	public JavaCodeGen( Chunk chunk, Writer writer,String packagename, String classname) {
		this.chunk = chunk;
		this.writer = writer;
		this.packagename = packagename;
		this.classname = classname;
		chunk.accept( new NameResolver() );
		chunk.accept( new JavaClassWriterVisitor() );
	}

	class JavaClassWriterVisitor extends Visitor {

		JavaScope javascope = null;
		List<String> constantDeclarations = new ArrayList<String>();
		Map<String,String> stringConstants = new HashMap<String,String>();
		Map<Double,String> numberConstants = new HashMap<Double,String>();
		
		
		String indent = "";
		void addindent() {
			indent+="   ";
		}
		void subindent() {
			indent = indent.substring(3);
		}
		void out(String s) {
			try { 
				writer.write(s); 
			} catch (IOException e) { 
				throw new RuntimeException("write failed: "+e, e); 
			} 
		}
		void outi(String s) {
			out( indent );
			out( s );
		}
		void outl(String s) {
			outi( s );
			out( "\n" );
		}
		void outr(String s) {
			out( s );
			out( "\n" );
		}
		void outb(String s) {
			outl( s );
			addindent();
		}
		void oute(String s) {
			subindent();
			outl( s );
		}
		
		public void visit(Chunk chunk) {
			if ( packagename != null )
				outl("package "+packagename+";");
			outl("import org.luaj.vm2.*;");
			outl("import org.luaj.vm2.lib.*;");
			outb("public class "+classname+" extends VarArgFunction {");
			outl("public Varargs invoke(Varargs arg) {");
			addindent();
			javascope = JavaScope.newJavaScope( chunk );
			writeBodyBlock(chunk.block);
			oute("}");
			for ( String s : constantDeclarations ) 
				outl( s );
			subindent();
			outi("}");
		}

		void writeBodyBlock(Block block) {
			if ( javascope.needsbinoptmp )
				outl( "LuaValue $b;" );
			super.visit(block);
			if ( !endsInReturn(block) )
				outl( "return NONE;" );
		}
		
		public void visit(Block block) {
			outb("{");
			super.visit(block);
			oute("}");
		}

		private boolean endsInReturn(Block block) {
			int n = block.stats.size();
			if ( n<=0 ) return false;
			Stat s = block.stats.get(n-1);
			return s instanceof Return || s instanceof Break;
		}
		
		public void visit(Stat.Return s) {
			int n = s.nreturns();
			switch ( n ) {
			case 0: outl( "return NONE;" ); break;
			case 1: outl( "return "+eval(s.values.get(0))+";" ); break;
			default: outl( "return "+eval(s.values)+";" ); break;
			}
		}

		public void visit(AnonFuncDef def) {
			super.visit(def);
		}

		public void visit(LocalAssign stat) {
			int n = stat.names.size();
			int m = stat.values!=null? stat.values.size(): 0;
			if ( n == 1 && m<=1 ) {
				Name name = stat.names.get(0);
				String javaname = javascope.getJavaName(name.variable);
				String value = "NIL";
				if ( m > 0 ) {
					Exp val= stat.values.get(0);
					value = eval(val) + (val.isvarargexp()? ".arg1()": "");
				}
				singleLocalDeclareAssign(name,value);
			} else {
				for ( Name name : stat.names )
					singleLocalDeclareAssign(name,null);
				multiAssign(stat.names, stat.values);
			}
		}
		public void visit(Assign stat) {
			multiAssign(stat.vars, stat.exps);
		}
		private void multiAssign(List varsOrNames, List<Exp> exps) {
			int n = varsOrNames.size();
			int m = exps != null? exps.size(): 0;
			boolean varlist = m>0 && exps.get(m-1).isvarargexp() && n>m;
			if ( n<=m ) {
				for ( int i=1; i<=n; i++ )
					singleAssign( varsOrNames.get(i-1), eval(exps.get(i-1)) );
				for ( int i=n+1; i<=m; i++ )
					outl( eval(exps.get(i-1))+";" );
			} else {
				outb( "{" );
				for ( int i=1; i<=m; i++ ) {
					boolean vtype = varlist && (i==m);
					Exp e = exps.get(i-1);
					String decl = (vtype? "Varargs": "LuaValue")+" $"+i;
					String valu = eval(e)+(e.isvarexp() && !vtype? ".arg1()": "");
					outl( decl+"="+valu+";" );
				}
				for ( int i=1; i<=n; i++ ) {
					String valu;
					if ( i < m ) valu = "$"+i;
					else if ( i==m ) valu = (varlist? "$"+i+".arg1()": "$"+i);
					else if ( varlist ) valu = "$"+m+".arg("+(i-m+1)+")";
					else valu = "NIL";
					singleAssign( varsOrNames.get(i-1), valu );
				}
				oute( "}" );
			}
		}
		private void singleAssign(final Object varOrName, final String valu) {
			Visitor v = new Visitor() {
				public void visit(FieldExp exp) {
					outl(eval(exp.lhs)+".set("+evalStringConstant(exp.name.name)+","+valu+");");
				}
				public void visit(IndexExp exp) {
					outl(eval(exp.lhs)+".set("+eval(exp.exp)+","+valu+");");
				}
				public void visit(NameExp exp) {
					if ( exp.name.variable.isLocal() )
						singleLocalAssign(exp.name, valu);
					else
						outl( "env.set("+evalStringConstant(exp.name.name)+","+valu+");");
				}
			};
			if ( varOrName instanceof VarExp )
				((VarExp)varOrName).accept(v);
			else if ( varOrName instanceof Name )
				singleLocalAssign((Name) varOrName, valu);
			else
				throw new IllegalStateException("can't assign to "+varOrName.getClass());
		}
		private void singleLocalAssign(Name name, String valu) {
			outi( javascope.getJavaName(name.variable) );
			if ( name.variable.isupvalue )
				out( "[0]" );
			outr( " = "+valu+";");
		}
		private void singleLocalDeclareAssign(Name name, String value) {
			String javaname = javascope.getJavaName(name.variable);
			if ( name.variable.isupvalue )
				outl( "final LuaValue[] "+javaname+" = {"+value+"};" );
			else
				outl( "LuaValue "+javaname+(value!=null? " = "+value: "")+";" );
		}
		public void visit(Break breakstat) {
			// TODO: wrap in do {} while(false), or add label as nec
			outl( "break;" );
		}

		private Writer pushWriter() {
			Writer x = writer;
			writer = new CharArrayWriter();
			return x;
		}
		
		private String popWriter(Writer x) {
			Writer c = writer;
			writer = x;
			return c.toString();
		}
		
		public String eval(List<Exp> values) {
			int n = values.size();
			switch ( n ) {
			case 0: return "NONE";
			case 1: return eval(values.get(0));
			default: 
				Writer x = pushWriter();
				out( n<=3? "varargsOf(": "varargsOf(new LuaValue[] {" );
				for ( int i=0; i<n; i++ ) {
					if ( i>0 ) out( "," );
					out(eval(values.get(i)));
				}
				out( n<=3? ")": "})" );
				return popWriter(x);
			}
		}
		
		public String eval(Exp exp) {
			Writer x = pushWriter();
			exp.accept(this);
			return popWriter(x);
		}
		
		public void visit(BinopExp exp) {
			switch ( exp.op ) {
			case Lua.OP_AND:
			case Lua.OP_OR:
				out(exp.op==Lua.OP_AND? "(!($b=": "(($b=");
				exp.lhs.accept(this);
				out(").toboolean()?$b:");
				exp.rhs.accept(this);
				out( ")" );
				return;
				
			}
			exp.lhs.accept(this);
			switch ( exp.op ) {
			case Lua.OP_ADD: out(".add"); break;
			case Lua.OP_SUB: out(".sub"); break;
			case Lua.OP_MUL: out(".mul"); break;
			case Lua.OP_DIV: out(".div"); break;
			case Lua.OP_POW: out(".pow"); break;
			case Lua.OP_MOD: out(".mod"); break;
			case Lua.OP_GT: out(".gt"); break;
			case Lua.OP_GE: out(".ge"); break;
			case Lua.OP_LT: out(".lt"); break;
			case Lua.OP_LE: out(".le"); break;
			case Lua.OP_EQ: out(".eq"); break;
			case Lua.OP_NEQ: out(".neq"); break;
			case Lua.OP_CONCAT: out(".concat"); break;
			default: throw new IllegalStateException("unknown bin op:"+exp.op);
			}
			out("(");
			exp.rhs.accept(this);
			out(")");
		}

		public void visit(UnopExp exp) {
			exp.rhs.accept(this);
			switch ( exp.op ) {
			case Lua.OP_NOT: out(".not()"); break;
			case Lua.OP_LEN: out(".len()"); break;
			case Lua.OP_UNM: out(".neg()"); break;
			}
		}

		public void visit(Constant exp) {
			switch ( exp.value.type() ) {
			case LuaValue.TSTRING: {
				// TODO: non-UTF8 data
				out( evalStringConstant(exp.value.tojstring()) );
				break;
			}
			case LuaValue.TNIL:
				out("NIL");
				break;
			case LuaValue.TBOOLEAN:
				out(exp.value.toboolean()? "TRUE": "FALSE");
				break;
			case LuaValue.TNUMBER: {
				out( evalNumberConstant(exp.value.todouble()) );
				break;
			}
			default:
				throw new IllegalStateException("unknown constant type: "+exp.value.typename());
			}
		}

		private String evalStringConstant(String str) {
			// TODO: quoting, data pooling
			if ( stringConstants.containsKey(str) )
				return stringConstants.get(str);
			String declvalue = quotedStringInitializer(str.getBytes());
			String javaname = javascope.createConstantName(str);
			constantDeclarations.add( "static final LuaValue "+javaname+" = valueOf("+declvalue+");" );
			stringConstants.put(str,javaname);
			return javaname;
		}
		
		private String evalNumberConstant(double value) {
			if ( value == 0 ) return "ZERO";
			if ( value == -1 ) return "MINUSONE";
			if ( value == 1 ) return "ONE";
			if ( numberConstants.containsKey(value) )
				return numberConstants.get(value);
			int ivalue = (int) value;
			String declvalue = value==ivalue? String.valueOf(ivalue): String.valueOf(value);
			String javaname = javascope.createConstantName(declvalue);
			constantDeclarations.add( "static final LuaValue "+javaname+" = valueOf("+declvalue+");" );
			numberConstants.put(value,javaname);
			return javaname;
		}
		
		public void visit(FieldExp exp) {
			exp.lhs.accept(this);
			out(".get("+evalStringConstant(exp.name.name)+")");
		}

		public void visit(IndexExp exp) {
			exp.lhs.accept(this);
			out(".get(");
			exp.exp.accept(this);
			out(")");
		}

		public void visit(NameExp exp) {
			if ( exp.name.variable.isLocal() ) {
				out( javascope.getJavaName(exp.name.variable) );
				if ( exp.name.variable.isupvalue )
					out( "[0]" );
			} else {
				out( "env.get("+evalStringConstant(exp.name.name)+")");
			}
		}

		public void visit(ParensExp exp) {
			exp.exp.accept(this);
			out(".arg1()");
		}

		public void visit(VarargsExp exp) {
			out( "arg" );
		}

		public void visit(MethodCall exp) {
			int n = exp.args.exps != null? exp.args.exps.size(): 0;
			exp.lhs.accept(this);
			switch ( n ) {
			case 0: 
				out(".method("+evalStringConstant(exp.name)+")"); 
				break;
			case 1: case 2: case 3: 
				out(".method("+evalStringConstant(exp.name)+",");
				exp.args.accept(this);
				out(")");
				break;
			default:
				out(".invokemethod("+evalStringConstant(exp.name)+",varargsOf(new LuaValue[]{");
				exp.args.accept(this);
				out("})).arg1()");
				break;
			}
		}
		
		public void visit(FuncCall exp) {
			int n = exp.args.exps != null? exp.args.exps.size(): 0;
			exp.lhs.accept(this);
			switch ( n ) {
			case 0: case 1: case 2: case 3: 
				out(".call(");
				exp.args.accept(this);
				out(")");
				break;
			default:
				out(".invoke("+eval(exp.args.exps)+").arg1()");
				break;
			}
		}

		public void visit(FuncArgs args) {
			if ( args.exps != null ) {
				int n = args.exps.size();
				for ( int i=0; i<n; i++ ) {
					if ( i > 0 ) out(",");
					Exp e = args.exps.get(i);
					out( eval( e ) );
					// TODO: arg1(), varargs
				}
			}
		}

		public void visit(FuncBody body) {
			javascope = javascope.pushJavaScope(body);
			int n = javascope.nreturns;
			int m = body.parlist.names!=null? body.parlist.names.size(): 0;
			if ( n>=0 && n<=1 && m<=3 && ! body.parlist.isvararg ) {
				switch ( m ) {
				case 0: 
					outr("new ZeroArgFunction() {");
					addindent();
					outb("public LuaValue call() {");
					break;
				case 1: 
					outr("new OneArgFunction() {");
					addindent();
					outb("public LuaValue call("
							+declareArg(body.parlist.names.get(0))+") {");
					assignArg(body.parlist.names.get(0));
					break;
				case 2: 
					outr("new TwoArgFunction() {");
					addindent();
					outb("public LuaValue call("
							+declareArg(body.parlist.names.get(0))+","
							+declareArg(body.parlist.names.get(1))+") {");
					assignArg(body.parlist.names.get(0));
					assignArg(body.parlist.names.get(1));
					break;
				case 3: 
					outr("new ThreeArgFunction() {");
					addindent();
					outb("public LuaValue call("
							+declareArg(body.parlist.names.get(0))+","
							+declareArg(body.parlist.names.get(1))+","
							+declareArg(body.parlist.names.get(2))+") {");
					assignArg(body.parlist.names.get(0));
					assignArg(body.parlist.names.get(1));
					assignArg(body.parlist.names.get(2));
					break;
				}
			} else {
				outr("new VarArgFunction() {");
				addindent();
				outb("public Varargs invoke(Varargs arg) {");
				for ( int i=0; i<m; i++ ) {
					Name name = body.parlist.names.get(i);
					String argname = javascope.getJavaName(name.variable);
					String value = i>0? "arg.arg("+(i+1)+")": "arg.arg1()";
					if ( name.variable.isupvalue )
						outl( "final LuaValue[] "+argname+" = {"+value+"};" );
					else
						outl( "LuaValue "+argname+" = "+value+";" );
				}
				if ( body.parlist.isvararg && m > 0 ) 
					outl( "arg = arg.arglist("+(m+1)+");" );
			}
			writeBodyBlock(body.block);
			oute("}");
			subindent();
			outi("}");
			javascope = javascope.popJavaScope();
		}

		private String declareArg(Name name) {
			String argname = javascope.getJavaName(name.variable);
			return "LuaValue "+argname+(name.variable.isupvalue? "$0": "");
		}
		
		private void assignArg(Name name) {
			if ( name.variable.isupvalue ) {
				String argname = javascope.getJavaName(name.variable);
				outl( "final LuaValue[] "+argname+" = {"+argname+"$0};" );
			}
		}
		
		public void visit(FuncCallStat stat) {
			outl(eval(stat.funccall)+";");
		}

		public void visit(FuncDef stat) {
			super.visit(stat);
		}

		public void visit(LocalFuncDef stat) {
			String javaname = javascope.getJavaName(stat.name.variable);
			if ( stat.name.variable.isupvalue ) 
				outi("final LuaValue[] "+javaname+" = {");
			else
				outi("LuaValue "+javaname+" = ");
			super.visit(stat);
			outr( stat.name.variable.isupvalue? "};": ";" );
		}

		public void visit(NumericFor stat) {
			String javaname = javascope.getJavaName(stat.name.variable);
			outi("for ( LuaValue "
					+javaname+"="+eval(stat.initial)+", "
					+javaname+"$limit="+eval(stat.limit));
			String stepname = "ONE";
			if ( stat.step!=null ) 
				out(", "+(stepname=javaname+"$step")+"="+eval(stat.step));
			outr( "; "
					+javaname+".testfor_b("+javaname+"$limit,"+stepname+"); "
					+javaname+"="+javaname+".add("+stepname+") ) {" );
			addindent();
			super.visit(stat.block);
			oute( "}" );
		}

		private Name tmpJavaVar(String s) {
			Name n = new Name(s);
			n.variable = javascope.define(s);
			return n;
		}
		
		public void visit(GenericFor stat) {
			Name f = tmpJavaVar("f");
			Name s = tmpJavaVar("s");
			Name var = tmpJavaVar("var");
			Name v = tmpJavaVar("v");
			String javaf = javascope.getJavaName(f.variable);
			String javas = javascope.getJavaName(s.variable);
			String javavar = javascope.getJavaName(var.variable);
			String javav = javascope.getJavaName(v.variable);
			outl("LuaValue "+javaf+","+javas+","+javavar+";");
			outl("Varargs "+javav+";");
			List<Name> fsvar = new ArrayList<Name>();
			fsvar.add(f);
			fsvar.add(s);
			fsvar.add(var);
			multiAssign(fsvar, stat.exps);
			
			outb("while (true) {");
			outl( javav+" = "+javaf+".invoke(varargsOf("+javas+","+javavar+"));");
			outl( "if (("+javavar+"="+javav+".arg1()).isnil()) break;");
			singleLocalDeclareAssign(stat.names.get(0),javavar);
			for ( int i=1, n=stat.names.size(); i<n; i++ )
				singleLocalDeclareAssign(stat.names.get(i),javav+".arg("+(i+1)+")");
			super.visit(stat.block);
			oute("}");
		}

		public void visit(ParList pars) {
			super.visit(pars);
		}

		public void visit(IfThenElse stat) {
			outb( "if ( "+eval(stat.ifexp)+".toboolean() ) {");
			super.visit(stat.ifblock);
			if ( stat.elseifblocks != null ) 
				for ( int i=0, n=stat.elseifblocks.size(); i<n; i++ ) {
					subindent();
					outl( "} else if ( "+eval(stat.elseifexps.get(i))+".toboolean() ) {");
					addindent();
					super.visit(stat.elseifblocks.get(i));
				}
			if ( stat.elseblock != null ) {
				subindent();
				outl( "} else {");
				addindent();
				super.visit( stat.elseblock );
			}
			oute( "}" );
		}

		public void visit(RepeatUntil stat) {
			outb( "do {");
			super.visit(stat.block);
			oute( "} while (!"+eval(stat.exp)+".toboolean());" );
		}
		
		public void visit(TableConstructor table) {
			if ( table.fields == null ) {
				out("LuaValue.tableOf()");
			} else {
				int n = table.fields.size();
				out("LuaValue.tableOf(new LuaValue[]{");
				for ( int i=0; i<n; i++ ) {
					TableField f = table.fields.get(i);
					if ( f.name == null && f.index == null )
						continue;
					if ( f.name != null )
						out( evalStringConstant(f.name)+"," );
					else
						out( eval(f.index)+"," );
					out( eval(f.rhs)+"," );
				}
				out("},new LuaValue[] {");
				for ( int i=0; i<n; i++ ) {
					TableField f = table.fields.get(i);
					if ( f.name == null && f.index == null )
						out( eval(f.rhs)+"," );
				}
				out("})");
			}
		}

		public void visit(WhileDo stat) {
			outb( "while ("+eval(stat.exp)+") {");
			super.visit(stat.block);
			oute( "}" );
		}

		public void visitExps(List<Exp> exps) {
			super.visitExps(exps);
		}

		public void visitNames(List<Name> names) {
			super.visitNames(names);
		}

		public void visitVars(List<VarExp> vars) {
			super.visitVars(vars);
		}		
	}

	private static String quotedStringInitializer(byte[] bytes) {
		int n = bytes.length;
		StringBuffer sb = new StringBuffer(n+2);		
		
		// check for characters beyond ascii 128
		for ( int i=0; i<n; i++ )
			if (bytes[i]<0) {
				sb.append( "new byte[]{" );
				for ( int j=0; j<n; j++ ) {
					if ( j>0 ) sb.append(",");
					byte b = bytes[j];
					switch ( b ) {
						case '\n': sb.append( "'\\n'" ); break; 
						case '\r': sb.append( "'\\r'" ); break; 
						case '\t': sb.append( "'\\t'" ); break; 
						case '\\': sb.append( "'\\\\'" ); break;
						default:
							if ( b >= ' ' ) {
								sb.append( '\'');
								sb.append( (char) b );
								sb.append( '\'');
							} else {
								sb.append( String.valueOf((int)b) );
							}
						break;
					}					
				}
				sb.append( "}" );
				return sb.toString();
			}

		sb.append('"');
		for ( int i=0; i<n; i++ ) {
			byte b = bytes[i];
			switch ( b ) {
				case '\b': sb.append( "\\b" ); break; 
				case '\f': sb.append( "\\f" ); break; 
				case '\n': sb.append( "\\n" ); break; 
				case '\r': sb.append( "\\r" ); break; 
				case '\t': sb.append( "\\t" ); break;
				case '"':  sb.append( "\\\"" ); break;
				case '\\': sb.append( "\\\\" ); break;
				default:
					if ( b >= ' ' ) {
						sb.append( (char) b ); break;
					} else {
						// convert from UTF-8
						int u = 0xff & (int) b;
						if ( u>=0xc0 && i+1<n ) {
							if ( u>=0xe0 && i+2<n ) {
								u = ((u & 0xf) << 12) | ((0x3f & bytes[i+1]) << 6) | (0x3f & bytes[i+2]);
								i+= 2;
							} else {
								u = ((u & 0x1f) << 6) | (0x3f & bytes[++i]);
							}
						}
						sb.append( "\\u" );
						sb.append( Integer.toHexString(0x10000+u).substring(1) );
					}
			}
		}
		sb.append('"');
		return sb.toString();
	}
	
}