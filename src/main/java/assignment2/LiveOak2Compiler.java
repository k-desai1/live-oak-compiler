package assignment2;

import edu.utexas.cs.sam.io.SamTokenizer;
import edu.utexas.cs.sam.io.Tokenizer.TokenType;
import java.util.ArrayList;
import java.util.HashMap;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.text.ParseException;
import java.util.regex.Pattern;

public class LiveOak2Compiler{

	public static HashMap<String, VarDeclNode> variable_map = new HashMap<String, VarDeclNode>();
	public static HashMap<String, MethodDeclNode> method_map = new HashMap<String, MethodDeclNode>();
	public static int var_count = 0;
	public static void main(String[] args) throws Error, Exception{
		String in_file;
		String out_file;
		if (args.length > 1){
			in_file = args[0];
			out_file = args[1];
		}
		else{
			in_file = "/Users/karandesai/SIMPL/hw2/lo2-to-sam-tester-public/src/test/resources/LO-2/ValidPrograms/test_88.lo";
			// in_file = "/Users/karandesai/SIMPL/hw2/lo2-to-sam-tester-public/src/test/resources/LO-2/InvalidPrograms/test_20.lo";
			out_file = "/Users/karandesai/SIMPL/hw1/sam_string_ops_tester-student/h2.sam";
		}

		String pgm = compiler(in_file);
		writeSamfile(pgm, out_file);
	}

	static String compiler(String fileName) throws Error, Exception
	{
		try{
			//returns SaM code for program in file
			SamTokenizer f = new SamTokenizer(fileName, SamTokenizer.TokenizerOptions.PROCESS_STRINGS);
			ProgramNode root = getProgram(f);
			if (!validateTree(root)){
				throw new Error("Failed to compile " + fileName);
			}
			String pgm = processTree(root);
			// System.out.println(pgm);
			return pgm;
		}
		catch (Error e){
			System.err.println("Failed to compile " + fileName);
			throw new Error("Failed to compile " + fileName);		
		}
		catch (Exception e){
			System.err.println("Failed to compile " + fileName);
			throw new Error("Failed to compile " + fileName);
		}
			
	}

	static ProgramNode getProgram(SamTokenizer f) throws ParseException{
		try{
			ProgramNode root = new ProgramNode();
			ArrayList<MethodDeclNode> methods = new ArrayList<MethodDeclNode>();
			root.children = methods;
			while (f.peekAtKind() != TokenType.EOF){
				MethodDeclNode method = getMethod(f);
				if (method != null){
					method.parent = root;
					methods.add(method);
				}
				else{
					return null;
				}
			}
			return root;
		}
		catch(ParseException e){
			throw new ParseException("Parse failure", 0);
		}		
	}

	static MethodDeclNode getMethod(SamTokenizer f) throws ParseException{
		String type = f.getWord();
		TokenType tt = f.peekAtKind();
		if (tt != TokenType.WORD){
			throw new ParseException("bad method definition", 0);
		}
		String name = f.getWord();
		char c = f.getOp(); //eat (
		ArrayList<FormalNode> formals = new ArrayList<FormalNode>();
		while (!f.test(')')){
			if (f.test(',')){
				f.skipToken();
			}
			FormalNode formal = getFormal(f);
			if (formal != null){
				formals.add(formal);
				variable_map.put(name + "." + formal.identifier, new VarDeclNode(formal.identifier, formal.type, name, variable_map.size()+1));
			}
		}
		if (name.equals("main") && formals.size() > 0){
			throw new ParseException("Main should not have formals", 0);
		}
		c = f.getOp(); // eat )
		c = f.getOp(); // eat { from method
		BodyNode body = getBody(f);
		c = f.getOp(); // ear } from method
		MethodDeclNode method = new MethodDeclNode(type, name, formals, body);

		//set scope for declared variables
		for (int i = 0 ; i < body.varDeclList.size() ; i++){
			body.varDeclList.get(i).scope = method.name;
			variable_map.put(method.name + "." + body.varDeclList.get(i).identifier, body.varDeclList.get(i));
		}
		var_count = 0; 
		//set scope for initialized variables
		for (int i = 0 ; i < body.child.statements.size() ; i++){
			ASTNode node = body.child.statements.get(i);
			if (node.nodeType.equals("Stmt")){
				StmtNode stmt = (StmtNode) node;
				if (stmt.var != null){
					stmt.var.scope = method.name;
				}
			}
		}

		method.body.parent = method;
		method_map.put(name, method);
		return method;
	}

	static FormalNode getFormal(SamTokenizer f){
		String type = f.getWord();
		String identifier = f.getWord();
		return new FormalNode(type, identifier);
	}

	static BodyNode getBody(SamTokenizer f) throws ParseException{
		BodyNode body = new BodyNode();
		ArrayList<VarDeclNode> vars = new ArrayList<VarDeclNode>();
		while(!f.test('{')){
			if (f.test('}')){ // missing block
				throw new ParseException("Missing statement block", 0);
			}
			ArrayList<VarDeclNode> new_vars = getVarDecl(f);
			for (int j = 0 ; j < new_vars.size() ; j++){
				vars.add(new_vars.get(j));
			}
		}
		body.varDeclList = vars;
		body.child = getBlock(f);
		body.child.parent = body;

		// add in conditional parents
		setParents(body, body.child);
		return body;
	}

	static ArrayList<VarDeclNode> getVarDecl(SamTokenizer f){
		try{
			ArrayList<VarDeclNode> varDeclList = new ArrayList<VarDeclNode>();
			String type = getType(f);
			char op;
			while (!f.test(';')){
				if (f.test(',')){
					op = f.getOp(); // eat ,
				}
				String identifier = getIdentifier(f);
				VarDeclNode var = new VarDeclNode(identifier, type, null, variable_map.size()+var_count+1);
				varDeclList.add(var);
				var_count++;
			}
			op = f.getOp(); // eat ;
			return varDeclList;
		}
		catch (Exception e){
			System.out.println("Fatal error: could not compile program");
			return null;
		}
	}

	static BlockNode getBlock(SamTokenizer f) throws ParseException{
		BlockNode block = new BlockNode();
		ArrayList<ASTNode> stmtList = new ArrayList<ASTNode>();
		char op = f.getOp(); // eat {
		while (!f.test('}')){
			int line = f.lineNo();
			ASTNode stmt = getStmt(f);
			if (stmt != null){
				if (stmt.nodeType.equals("Stmt")){
					StmtNode st = (StmtNode) stmt;
					st.id = line;
					st.parent = block;
					stmtList.add(st);
				}
				else if (stmt.nodeType.equals("Conditional")){
					ConditionalStmtNode cond = (ConditionalStmtNode) stmt;
					cond.parent = block;
					stmtList.add(cond);
				}
				else{
					LoopStmtNode loop = (LoopStmtNode) stmt;
					loop.parent = block;
					//TODO changed something here
					loop.block.parent = loop;
					stmtList.add(loop);
				}
			}
		}
		block.statements = stmtList;
		op = f.getOp(); // eat }
		return block;
	}

	static ASTNode getStmt(SamTokenizer f) throws ParseException{
		if (f.test(';')){
			f.getOp(); // eat ;
			return null;
		}
		else if (f.test("if")){
			ConditionalStmtNode cond = new ConditionalStmtNode();
			int id = f.lineNo();
			f.getWord(); // eat 'if'
			f.getOp(); // eat '('
			cond.condition = getExpr(f);
			cond.condition.parent = cond;
			f.getOp(); // eat ')'
			cond.if_block = getBlock(f);
			cond.if_block.parent = cond;
			f.getWord(); // eat 'else'
			cond.else_block = getBlock(f);
			cond.else_block.parent = cond;
			cond.id = id;
			return cond;
		}
		else if (f.test("while")){
			LoopStmtNode loop = new LoopStmtNode();
			int id = f.lineNo();
			f.getWord(); // eat 'while'
			f.getOp(); // eat '('
			loop.condition = getExpr(f);
			loop.condition.parent = loop;
			f.getOp(); // eat ')'
			loop.block = getBlock(f);
			loop.id = id;
			loop.block.loop = loop;
			return loop;
		}
		else if (f.test("break")){
			StmtNode stmt = new StmtNode(null, null);
			stmt.value = f.getWord();
			char c = f.getOp(); // eat ;
			return stmt;
		}
		else if (f.test("return")){
			f.getWord(); // eat 'return'
			StmtNode stmt = new StmtNode(null, null);
			ExprNode expr = new ExprNode();
			expr = getExpr(f);
			stmt.expr = expr;
			expr.parent = stmt;
			stmt.value = "return";
			char c = f.getOp(); // eat ;
			return stmt;
		}
		else{
			VarNode var = null;
			StmtNode stmt = null;
			var = getVar(f);
			char c = f.getOp(); // eat =
			ExprNode expr = new ExprNode();
			expr = getExpr(f);
			stmt = new StmtNode(var, expr);
			expr.parent = stmt;
			var.parent = stmt;
			c = f.getOp(); // eat ;
			if (c != ';'){
				throw new Error("invalid expression");
			}
			return stmt;
		}
	}

	static ExprNode getExpr(SamTokenizer f) throws ParseException{
		try{
			ExprNode expr = new ExprNode();
			if (f.peekAtKind() == TokenType.WORD){ // I think this is var
				String word = f.getWord();
				if (method_map.containsKey(word) || f.test('(')){
					MethodNode method = getMethod(word, f);
					expr.child = method;
					method.parent = expr;
				}
				else if (word.equals("true") || word.equals("false")){
					expr.child = new BoolNode(word);
				}
				else{
					VarNode var = new VarNode(word);
					expr.child = var;
				}
				expr.child.parent = expr;
				return expr;
			}
			else if (f.peekAtKind() == TokenType.STRING){
				expr.child = getLiteral(f);
				expr.child.parent = expr;
				//expr.parent = expr;
				return expr;
			}
			else if (f.peekAtKind() == TokenType.INTEGER){
				expr.child = getNum(f);
				expr.child.parent = expr;
				return expr;
			}
			else if (f.peekAtKind() == TokenType.OPERATOR){
				char c = f.getOp(); // eat the (
				if (c != '('){
					throw new ParseException("Invalid experession", 0);
				}
				TokenType tt = f.peekAtKind(); // peek one more time to check for more parens or unary op
				if (tt == TokenType.OPERATOR){
					if (f.test('!') || f.test('~')){ // unary op
						UnopNode unop = new UnopNode();
						unop.op = f.getOp();
						unop.child = getExpr(f);
						unop.child.parent = unop;
						expr.child = unop;
						unop.parent = expr;
						f.getOp(); //eat )
						return expr;
					}
					else {
						ExprNode child = getExpr(f);
						expr.child = child;
						expr.child.parent = expr;
						c = f.getOp(); // check next token
						if (c == ')'){ 
							return expr;
						}
						else if (c == '?'){ // ternary choice op
							TeropNode terop = new TeropNode();
							terop.id = f.lineNo();
							expr.child = terop;
							terop.parent = expr;
							terop.condition = child;
							terop.t = getExpr(f);
							terop.t.parent = terop;
							c = f.getOp(); // eat : 
							terop.f = getExpr(f);
							terop.f.parent = terop;
							c = f.getOp(); // eat )
							return expr;
						}
						else if (c == '+' || c == '-' || c == '*' || c == '/' || c == '%' || c == '|' 
						|| c == '&' || c == '=' || c == '<' || c == '>'){ // binary op
							BinopNode binop = new BinopNode();
							expr.child = binop;
							binop.parent = expr;
							binop.op = c;
							binop.left = child;
							binop.left.parent = binop;
							binop.right = getExpr(f);
							binop.right.parent = binop;
							c = f.getOp(); // eat )
							return expr;
						}
						else{
							throw new ParseException("Invalid expression", f.lineNo());
						}
					} 
				}
				else{
					ExprNode child = getExpr(f);
					expr.child = child;
					expr.child.parent = expr;
					c = f.getOp(); // check next token
					if (c == ')'){ 
						return expr;
					}
					else if (c == '?'){ // ternary choice op
						TeropNode terop = new TeropNode();
						terop.id = f.lineNo();
						expr.child = terop;
						terop.parent = expr;
						terop.condition = child;
						terop.t = getExpr(f);
						terop.t.parent = terop;
						c = f.getOp(); // eat : 
						terop.f = getExpr(f);
						terop.f.parent = terop;
						c = f.getOp(); // eat )
						return expr;
					}
					else if (c == '+' || c == '-' || c == '*' || c == '/' || c == '%' || c == '|' 
							|| c == '&' || c == '=' || c == '<' || c == '>'){ // binary op
						BinopNode binop = new BinopNode();
						expr.child = binop;
						binop.parent = expr;
						binop.op = c;
						binop.left = child;
						binop.left.parent = binop;
						binop.right = getExpr(f);
						binop.right.parent = binop;
						c = f.getOp(); // eat )
						return expr;
					}
					else{
						throw new ParseException("Invalid expression", f.lineNo());
					}
				}
			}
			else {
				throw new ParseException("Invalid expression", f.lineNo());
			}
		}
		catch (ParseException e){
			throw new ParseException("Invalid expression", 0);
		}
	}

	static MethodNode getMethod(String name, SamTokenizer f) throws ParseException{
		char c = f.getOp(); // eat (
		ArrayList<ASTNode> actuals = new ArrayList<ASTNode>();
		MethodNode method = new MethodNode(null, null);
		while (!f.test(')')){
			if (f.test(',')){
				f.skipToken();
			}
			ASTNode actual = getExpr(f);
			actual.parent = method;
			actuals.add(actual);
		}
		method.name = name;
		method.actuals = actuals;

		
		c = f.getOp(); // eat )
		return method;
	}

	static String getType(SamTokenizer f){
		try{
			String type = f.getWord();
			if (!type.equals("int") && !type.equals("String") && !type.equals("bool")){
				throw new ParseException("unrecognized data type for variable", f.lineNo());
			}
			else{
				return type;
			}
		}
		catch (Exception e){
			System.out.println("Fatal error: could not compile program");
			return "STOP\n";
		}
	}

	static LiteralNode getLiteral(SamTokenizer f){
		try{
			if (f.peekAtKind() == TokenType.STRING){
				return new LiteralNode(getString(f));
			}
			else if (f.peekAtKind() == TokenType.WORD){
				return new LiteralNode(f.getWord());
			}
			else{
				throw new ParseException("Invalid literal", f.lineNo());
			}
		}
		catch (Exception e){
			System.out.println("Fatal error: could not compile program");
			return null;
		}
	}

	static VarNode getVar(SamTokenizer f){
		try{
			String identifier = getIdentifier(f);
			return new VarNode(identifier);
		}
		catch (Exception e){
			System.out.println("Fatal error: could not compile program");
			return null;
		}
	}

	static NumNode getNum(SamTokenizer f){
		try{
			int num = f.getInt();
			String str = String.valueOf(num);
			Pattern p = Pattern.compile("([0-9])+");
			if (!p.matcher(str).matches()) {
				throw new ParseException("Invalid String", f.lineNo());
			}
			else{
				return new NumNode(num);
			}
		}
		catch (Exception e){
			System.out.println("Fatal error: could not compile program");
			return null;
		}
	}

	static String getString(SamTokenizer f){
		try{
			String str = f.getString();
			Pattern p = Pattern.compile("^\\p{ASCII}*$");
			if (!p.matcher(str).matches()) {
				throw new ParseException("Invalid String", f.lineNo());
			}
			else{
				return str;
			}
		}
		catch (Exception e){
			System.out.println("Fatal error: could not compile program");
			return "STOP\n";
		}
	}

	static String getIdentifier(SamTokenizer f){
		try{
			String str = f.getWord();
			if (str.equals("return")){
				return null;
			}
			else{
				return str;
			}
		}
		catch (Exception e){
			System.out.println("Fatal error: could not compile program");
			return "STOP\n";
		}
	}

	static String processTree(ProgramNode node){
		String pgm = "";
		pgm += "ADDSP " + variable_map.size() + "\n";
		pgm += "JUMP main\n";
		for (int i = 0 ; i < node.children.size() ; i++){
			MethodDeclNode method = node.children.get(i);
			pgm += processMethodDecl(method) + "\n";
		}
		pgm += "end:\n";
		pgm += "ADDSP -" + variable_map.size() + "\n";
		pgm += "PUSHABS 0\n";
		pgm += "STOP";
		return pgm;
	}

	static String processMethodDecl(MethodDeclNode node){
		String result = "";
		result += node.name + ":\n";
		result += processBody(node.body);

		return result;
	}

	static String processBody(BodyNode node){
		String result = "";
		result += processBlock(node.child);

		return result;
	}

	static String processBlock(BlockNode node){
		String result = "";
		for(int i = 0 ; i < node.statements.size() ; i++){
			result += processStmt(node.statements.get(i));
			//System.out.print(result);
		}

		return result;
	}

	static String processStmt(ASTNode node){
		try{
			String result = "";
			if (node.nodeType.equals("Stmt")){
				StmtNode stmt = (StmtNode) node;
				if (stmt.value == null){
					result += processExpr(stmt.expr);
					result += setVar(stmt.var);
				}
				else if (stmt.value.equals("return")){
					String method_name = getMethodName(stmt);
					if (!method_name.equals("main")){
						result += processExpr(stmt.expr);
						result += "SWAP\nJUMPIND\n";
					}
					else{
						result += processExpr(stmt.expr);
						result += "STOREABS 0\n";
						result += "JUMP end\n";
					}
				}
				else if (stmt.value.equals("break")){
					BlockNode block = (BlockNode)stmt.parent;
					result += "JUMP endloop" + getParentLoop(block) + "\n";
				}
				else{
					throw new ParseException("Unknown statement type", -1);
				}
			}
			else if (node.nodeType.equals("Loop")){
				LoopStmtNode loop = (LoopStmtNode) node;
				result += processLoop(loop);
			}
			else if (node.nodeType.equals("Conditional")){
				ConditionalStmtNode cond = (ConditionalStmtNode) node;
				result += processCond(cond);
			}
			else{
				throw new ParseException("Uknown statement type", -1);
			}
			return result;
		}
		catch (Exception e){
			e.printStackTrace();
			return null;
		}
	}

	static String processCond(ConditionalStmtNode cond){
		String result = "";
		result += processExpr(cond.condition) + "PUSHIMM 1\nSUB\n";
		result += "JUMPC else" + cond.id + "\n"; 
		result += "if" + cond.id + ":\n";
		result += processBlock(cond.if_block);
		result += "JUMP ifend" + cond.id + "\n";
		result += "else" + cond.id + ":\n";
		result += processBlock(cond.else_block);
		result += "ifend" + cond.id + ":\n";

		return result;
	}

	static String processLoop(LoopStmtNode loop){
		String result = "";
		result += "loop" + loop.id + ":\n";
		result += processExpr(loop.condition) + "PUSHIMM 1\nSUB\n";
		result += "JUMPC endloop" + loop.id + "\n";
		result += processBlock(loop.block);
		result += "JUMP loop" + loop.id + "\n";
		result += "endloop" + loop.id + ":\n";

		return result;
	}

	static String processExpr(ASTNode node){
		if (node.nodeType == "Expr"){
			ExprNode expr = (ExprNode) node;
			return processExpr(expr.child);
		}
		else if (node.nodeType == "Unop"){
			UnopNode unop = (UnopNode) node;
			return processUnop(unop);
		}
		else if (node.nodeType == "Binop"){
			BinopNode binop = (BinopNode) node;
			return processBinop(binop);
		}
		else if (node.nodeType == "Var"){
			VarNode var = (VarNode) node;
			String scope = getMethodName(node);
			var.scope = scope;
			return readVar(var);
		}
		else if (node.nodeType == "VarDecl"){
			VarDeclNode var = (VarDeclNode) node;
			return readVar(var);
		}
		else if (node.nodeType == "Num"){
			NumNode num = (NumNode) node;
			return processNum(num);
		}
		else if (node.nodeType == "Bool"){
			BoolNode bool = (BoolNode) node;
			return processBool(bool);
		}
		else if (node.nodeType == "Method"){
			MethodNode method = (MethodNode) node;
			return processMethod(method);
		}
		else if (node.nodeType == "Terop"){
			TeropNode terop = (TeropNode) node;
			return processTerop(terop);
		}
		else{
			LiteralNode lit = (LiteralNode) node;
			return processLiteral(lit);
		}
	}
	static String processMethod(MethodNode method){
		String result = "";
		for (int i = 0 ; i < method.actuals.size(); i++){
			result += processExpr(method.actuals.get(i));
			String identifier = method_map.get(method.name).formals.get(i).identifier;
			VarNode var = new VarNode(identifier);
			var.scope = method.name;
			result += setVar(var);
		}
		result += "JSR " + method.name + "\n";
		
		return result;
	}
	static String processUnop(UnopNode node){
		try{
			String result = "";
			ASTNode n = stripExpr(node.child);
			if (n.nodeType.equals("Num")){
				ASTNode op = getOperand(n);
				if (node.op == '~'){
					result += processExpr(op);
					result += "PUSHIMM -1\nTIMES\n";
				}
				else{
					throw new Exception("unhandle unop");
				}
			}
			else if (n.nodeType.equals("Bool")){
				ASTNode op = getOperand(n);
				if (node.op == '!'){
					result += processExpr(op);
					result += "NOT\n";
				}
			}
			else if (n.nodeType.equals("Literal")){
				LiteralNode lit = (LiteralNode) n;
				if (node.op == '~'){
					result += processExpr(lit);
					result += str_rev(lit.id);
				}
			}
			else if (n.nodeType.equals("Binop")){
				BinopNode b = (BinopNode) n;
				result += processBinop(b);
				if (node.op == '!'){
					result += "NOT\n";
				}
			}
			else if (n.nodeType.equals("Var")){
				VarNode v = (VarNode)n;
				String method = getMethodName(n);
				VarDeclNode var = variable_map.get(method+"."+v.identifier);
				if (var.type.equals("int")){
					if (node.op == '!'){
						result += processExpr(var);
						result += "NOT\n";
					}
					else{
						throw new Exception("unhandle unop");
					}
				}
				else if(var.type.equalsIgnoreCase("String")){
					if (node.op == '~'){
						result += processExpr(var);
						result += str_rev(var.id);
					}
				}
				
			}
			else{
				throw new Exception("unhandle unop");
			}
			return result;
		}
		catch (Exception e){
			e.printStackTrace();
			return null;
		}
	}

	static String processTerop(TeropNode terop){
		try{
			String result = "";
			ExprNode cond = (ExprNode)terop.condition;
			while (true){
				if (cond.child.nodeType.equals("Expr")){
					cond.child.parent = cond.parent;
					cond = (ExprNode)cond.child;
				}
				else{
					break;
				}
			}
			result += processExpr(cond);
			result += "PUSHIMM 1\nSUB\nJUMPC f" + terop.id + "\n";
			result += "t" + terop.id + ":\n" + processExpr(terop.t);
			result += "JUMP teropend" + terop.id + "\n";
			result += "f" + terop.id + ":\n" + processExpr(terop.f);
			result += "teropend" + terop.id + ":\n";

			return result;				
		}
		catch (Exception e){
			e.printStackTrace();
			return null;
		}
		
	}

	static String processBinop(BinopNode binop){
		try{
			String result = "";
			ASTNode left = stripExpr(binop.left);
			ASTNode right = stripExpr(binop.right);
			if (left.nodeType.equals("Binop") && right.nodeType.equals("Binop")){
				result += processBinop((BinopNode)left);
				result += processBinop((BinopNode)right);
			}
			else if (left.nodeType.equals("Binop")){
				if (right.nodeType.equals("Unop")){
					result += processBinop((BinopNode)left);
					result += processUnop((UnopNode)right);
				}
				else{
					result += processBinop((BinopNode)left);
					result += processExpr(right);
				}
			}
			else if (right.nodeType.equals("Binop")){
				result += processExpr(left);
				result += processBinop((BinopNode)right);
			}
			else if (left.nodeType.equals("Method") && right.nodeType.equals("Method")){
				result += processMethod((MethodNode)left);
				result += processMethod((MethodNode)right);
			}
			else if (left.nodeType.equals("Method")){
				result += processMethod((MethodNode)left);
				result += processExpr(right);
			}
			else if (right.nodeType.equals("Method")){
				result += processExpr(left);
				result += processMethod((MethodNode)right);
			}
			else if (left.nodeType.equals("Unop") && right.nodeType.equals("Unop")){
				result += processUnop((UnopNode) left);
				result += processUnop((UnopNode) right);
			}
			else if (left.nodeType.equals("Unop")){
				result += processUnop((UnopNode) left);
				result += processExpr(right);
			}
			else if (right.nodeType.equals("Unop")){
				result += processExpr(left);
				result += processUnop((UnopNode) right);
			}
			else{ // assumming we're just dealing with nums / literals
				result += processExpr(left) + processExpr(right);
			}
			ASTNode op1 = getOperand(left);
			ASTNode op2 = getOperand(right);
			result += processOp(binop.op, op1, op2);
			return result;
		}
		catch (Exception e){
			e.printStackTrace();
			return null;
		}
		
	}

	static String getType(ASTNode op){
		try{
			if (op.nodeType.equals("Num")){
				return "int";
			}
			else if (op.nodeType.equals("Literal")){
				return "String";
			}
			else if (op.nodeType.equals("Bool")){
				return "bool";
			}
			else if (op.nodeType.equals("Var")){
				VarNode var = (VarNode) op;
				if (var.scope == null){
					var.scope = getMethodName(var);
				}
				return variable_map.get(var.scope + "." + var.identifier).type;
			}
			else if (op.nodeType.equals("Method")){
				MethodNode method = (MethodNode) op;
				return method_map.get(method.name).type;
			}
			else if (op.nodeType.equals("Binop")){
				BinopNode binop = (BinopNode) op;
				return getBinopType(binop);
			}
			else if (op.nodeType.equals("Unop")){
				UnopNode unop = (UnopNode) op;
				return checkOp(unop.child);
			}
			else{
				throw new ParseException("Unknown operand type", -1);
			}
		}
		catch (Exception e){
			System.out.println(e.getMessage());
			return null;
		}
	}

	static void updateVar(VarNode var, ExprNode expr){
		ASTNode val = getOperand(expr);
		if (val.nodeType.equals("Literal")){
			LiteralNode lit = (LiteralNode) val;
			var.value = lit.literal;
		}
		else if (val.nodeType.equals("Num")){
			NumNode num = (NumNode) val;
			var.value = num.num;
		}
	}

	static ASTNode getOperand(ASTNode node){
		if (node.nodeType == "Expr"){
			ExprNode expr = (ExprNode) node;
			return getOperand(expr.child);
		}
		else if (node.nodeType == "Var"){
			VarNode var = (VarNode) node;
			return var;
		}
		else if (node.nodeType == "Num"){
			NumNode num = (NumNode) node;
			return num;
		}
		else if (node.nodeType == "Bool"){
			BoolNode bool = (BoolNode) node;
			return bool;
		}
		else if (node.nodeType == "Method"){
			MethodNode method = (MethodNode) node;
			return method;
		}
		else if (node.nodeType == "Binop"){
			BinopNode binop = (BinopNode) node;
			return binop;
		}
		else if (node.nodeType == "Unop"){
			UnopNode unop = (UnopNode) node;
			return unop;
		}
		else{
			LiteralNode lit = (LiteralNode) node;
			return lit;
		}
	}

	static String readVar(ASTNode var){
		if(var.nodeType.equals("Var")){
			VarNode v = (VarNode) var;
			if (v.scope == null){
				v.scope = getMethodName(v);
			}
			return "PUSHOFF " + (variable_map.get(v.scope + "." + v.identifier).location-1) + "\n";
		}
		else{
			VarDeclNode v = (VarDeclNode) var;
			return "PUSHOFF " + (variable_map.get(v.scope + "." + v.identifier).location-1) + "\n";
		}	
	}

	static String setVar(VarNode var){
		if (var.scope == null){
			var.scope = getMethodName(var);
		}
		return "STOREOFF " + (variable_map.get(var.scope + "." + var.identifier).location-1) + "\n";
	}

	static String processNum(NumNode num){
		return "PUSHIMM " + num.num + "\n";
	}

	static String processLiteral(LiteralNode lit){
		return "PUSHIMMSTR \"" + lit.literal + "\"\n";
	}

	static String processBool(BoolNode bool){
		return "PUSHIMM " + bool.value + "\n";
	}

	static String processOp(char op, ASTNode op1, ASTNode op2){
		try{
			String result = "";
			String op1_type = checkOp(stripExpr(op1));
			String op2_type = checkOp(stripExpr(op2));
			if (op1_type.equals("int") && op2_type.equals("int")){
				if (op == '\0'){ // null op
					return "";
				}
				else if (op == '+'){
					return "ADD\n";
				}
				else if (op == '-'){
					return "SUB\n";
				}
				else if (op == '*'){
					return "TIMES\n";
				}
				else if (op == '/'){
					return "DIV\n";
				}
				else if (op == '%'){
					return "MOD\n";
				}
				else if (op == '<'){
					return "LESS\n";
				}
				else if (op == '>'){
					return "GREATER\n";
				}
				else if (op == '%'){
					return "MOD\n";
				}
				else if (op == '='){
					return "EQUAL\n";
				}
				else if (op == '|'){
					return "OR\n";
				}
				else if (op == '&'){
					return "AND\n";
				}
				else{
					throw new ParseException("Unrecognized op " + op, -1);
				}
			}
			else if (op1_type.equals("bool") && op2_type.equals("bool")){
				if (op == '='){
					return "EQUAL\n";
				}
				else if (op == '|'){
					return "OR\n";
				}
				else if (op == '&'){
					return "AND\n";
				}
				else{
					throw new ParseException("Unrecognized op " + op, -1);
				}
			}
			else if (op1_type.equals("bool") && op2_type.equals("String")){
				throw new Error("Failed to compile");
			}
			else{
				if (op == '*'){
					result += str_repeat(op1.id);
					return result;
				}
				else if (op == '+'){
					result += str_concat(op1.id);
					return result;
				}
				else if (op == '='){
					result += str_cmp(op1.id);
					result += "PUSHIMM 0\nEQUAL\n";
					return result;
				}
				else if (op == '>'){
					result += str_cmp(op1.id);
					result += "PUSHIMM -1\nEQUAL\n";
					return result;
				}
				else if (op == '<'){
					result += str_cmp(op1.id);
					result += "PUSHIMM 1\nEQUAL\n";
					return result;
				}
				else{
					throw new ParseException("unknown string operand", 0);
				}
			}
		}
		catch (Exception e){
			e.printStackTrace();
			return null;
		}
	}
	
	static void writeSamfile(String pgm, String f){
		try{
			BufferedWriter bw = new BufferedWriter(new FileWriter(f));
			bw.write(pgm);
			bw.close();
		}
		catch (Exception e){
			e.printStackTrace();
		}
	}

	static boolean validateTree(ProgramNode root){
		ArrayList<MethodDeclNode> methods = root.children;
		for (int i = 0 ; i < methods.size(); i++){
			if (!hasReturn(methods.get(i))){
				return false;
			}
			if (!validReturn(methods.get(i))){
				return false;
			}
			if (!validMethodCalls(methods.get(i))){
				return false;
			}
		}
		return true;
	}

	static boolean validMethodCalls(MethodDeclNode method){
		BodyNode body = method.body;
		BlockNode block = body.child;
		for (int i = 0 ; i < block.statements.size() ; i++){
			ASTNode node = block.statements.get(i);
			MethodNode m = findMethodCall(node);
			if (m == null){
				continue;
			}
			if (m.actuals.size() != method_map.get(m.name).formals.size()){
				return false;
			}
			for (int j = 0 ; j < m.actuals.size() ; j++){
				String t1 = getType(stripExpr(m.actuals.get(j)));
				String t2 = method_map.get(m.name).formals.get(j).type;
				if (!t1.equalsIgnoreCase(t2)){
					return false;
				}
			}
		}
		return true;
	}

	static MethodNode findMethodCall(ASTNode node){
		if (node.nodeType.equals("Method")){
			MethodNode method = (MethodNode) node;
			return method;
		}
		else if(node.nodeType.equals("Stmt")){
			StmtNode stmt = (StmtNode) node;
			return findMethodCall(stmt.expr);
		}
		else if (node.nodeType.equals("Expr")){
			ExprNode expr = (ExprNode) node;
			return findMethodCall(expr.child);
		}
		else{
			return null;
		}
	}

	static boolean hasReturn(MethodDeclNode method){
		BodyNode body = method.body;
		BlockNode block = body.child;
		for (int i = 0 ; i < block.statements.size() ; i++){
			ASTNode node = block.statements.get(i);
			if (node.nodeType.equals("Stmt")){
				StmtNode stmt = (StmtNode) node;
				if (stmt.value != null){
					if (stmt.value.equals("return")){
						return true;
					}
				}
			}
		}
		return false;
	}

	static boolean validReturn(MethodDeclNode method){
		BodyNode body = method.body;
		BlockNode block = body.child;
		for (int i = 0 ; i < block.statements.size() ; i++){
			ASTNode node = block.statements.get(i);
			if (node.nodeType.equals("Stmt")){
				StmtNode stmt = (StmtNode) node;
				if (stmt.value != null){
					if (stmt.value.equals("return")){
						ASTNode op = stmt.expr.child;
						op = stripExpr(op);
						String type = "";
						if (op.nodeType.equals("Bool")){
							type = "bool";
						}
						else if (op.nodeType.equals("Num")){
							type = "int";
						}
						else if (op.nodeType.equals("Literal")){
							type = "String";
						}
						else if (op.nodeType.equals("Method")){
							MethodNode m = (MethodNode) op;
							type = method_map.get(m.name).type;
						}
						else if (op.nodeType.equals("Var")){
							VarNode var = (VarNode) op;
							String scope = getMethodName(var);
							type = variable_map.get(scope + "." + var.identifier).type;
						}
						else if (op.nodeType.equals("Binop")){
							BinopNode binop = (BinopNode) op;
							if (binop.op == '|' || binop.op == '&' || binop.op == '=' || binop.op == '>' || binop.op == '<'){
								type = "bool";
							}
							else if (numOp(binop.op)){
								type = getBinopType(binop);
							}
							else{
								type = "";
							}
						}
						else if (op.nodeType.equals("Terop")){
							TeropNode terop = (TeropNode) op;
							String op1 = checkOp(stripExpr(terop.t));
							String op2 = checkOp(stripExpr(terop.f));
							if (method.type.equals(op1) && method.type.equals(op2)){
								return true;
							}
						}
						else if (op.nodeType.equals("Unop")){
							UnopNode unop = (UnopNode) op;
							String op1 = checkOp(unop.child);
							if (method.type.equals(op1)){
								return true;
							}
						}
						if (method.type.equals(type)){
							return true;
						}
					}
				}
			}
		}
		return false;
	}

	static String checkOp(ASTNode node){
		while(node.nodeType.equals("Expr")){
			ExprNode e = (ExprNode) node;
			node = e.child;
		}
		if (node.nodeType.equals("Bool")){
			return "bool";
		}
		else if (node.nodeType.equals("Num")){
			return "int";
		}
		else if (node.nodeType.equals("Literal")){
			return "String";
		}
		else if (node.nodeType.equals("Method")){
			MethodNode m = (MethodNode) node;
			return method_map.get(m.name).type;
		}
		else if (node.nodeType.equals("Unop")){
			UnopNode unop = (UnopNode) node;
			char op = unop.op;
			return checkOp(stripExpr(unop.child));
		}
		else if (node.nodeType.equals("Binop")){
			BinopNode binop = (BinopNode) node;
			return getBinopType(binop);
		}
		else if (node.nodeType.equals("Var")){
			VarNode var = (VarNode) node;
			String scope = getMethodName(var);
			return variable_map.get(scope + "." + var.identifier).type;
		}
		return "";
	}

	static boolean validName(String name){
		Pattern p = Pattern.compile("[a-zA-Z]([a-zA-Z0-9’_’])*");
		if (!p.matcher(name).matches()) {
			return false;
		}
		else{
			return true;
		}
	}

	static void setParents(BodyNode body, ASTNode node){
		if (node.nodeType.equals("Block")){
			BlockNode block = (BlockNode) node;
			block.parent = body;
			for (int i = 0 ; i < block.statements.size() ; i++){
				ASTNode stmt = block.statements.get(i);
				setParents(body, stmt);
			}
		}
		else if (node.nodeType.equals("Conditional")){
			ConditionalStmtNode cond = (ConditionalStmtNode) node;
			setParents(body, cond.if_block);
			setParents(body, cond.else_block);
		}
		else {
			return;
		}
	}

	static boolean numOp(char op){
		if (op == '+' || op == '-' || op == '*' || op == '/' || op == '%'){
			return true;
		}
		else{
			return false;
		}
	}

	static String getMethodName(ASTNode node){
		if (node.nodeType.equals("MethodDecl")){
			MethodDeclNode method = (MethodDeclNode) node;
			return method.name;
		}
		else{
			return getMethodName(node.parent);
		}
	}

	static ASTNode stripExpr(ASTNode node){
		if (!node.nodeType.equals("Expr")){
			return node;
		}
		else{
			ExprNode expr = (ExprNode)node;
			expr.child.parent = node.parent;
			return stripExpr(expr.child);
		}
	}

	static int getParentLoop(ASTNode node){
		if (node.nodeType.equals("Loop")){
			LoopStmtNode loop = (LoopStmtNode) node;
			return loop.id;
		}
		else{
			return getParentLoop(node.parent);
		}
	}

	static String getBinopType(BinopNode node){
		String left_type, right_type;
		ASTNode left = stripExpr(node.left);
		ASTNode right = stripExpr(node.right);
		//TODO I made this change since last test build
		if (node.op == '|' || node.op == '&' || node.op == '=' || node.op == '<' || node.op == '>'){
			node.type = "bool";
			return "bool";
		}
		else if (left.nodeType.equals("Binop") && right.nodeType.equals("Binop")){
			left_type = getBinopType((BinopNode)left);
			right_type = getBinopType((BinopNode)right);
		}
		else if (left.nodeType.equals("Binop")){
			left_type = getBinopType((BinopNode)left);
			right_type = checkOp(right);
		}
		else if (right.nodeType.equals("Binop")){
			left_type = checkOp(left);
			right_type = getBinopType((BinopNode)right);
		}
		else{
			left_type = checkOp(left);
			right_type = checkOp(right);
		}

		if (left_type.equals("String") || right_type.equals("String")){
			node.type = "String";
			return "String";
		}
		else if (left_type.equals("bool") || right_type.equals("bool")){
			node.type = "bool";
			return "bool";
		}
		else{
			node.type = "int";
			return "int";
		}
	}




	// string operations

	static String str_cmp(int i){
		String result =  "strcmp"+ i + ":\n";

		result+="DUP\n";
		result+="STOREABS 3002\n"; // string 2 address 
		result+="SWAP\n";
		result+="DUP\n";
		result+="STOREABS 3001\n"; // string 1 address
		result+="strcmplength1" + i + ":\n";
		result+="DUP\n";
		result+="PUSHIMM 1\n";
		result+="SUB\n";
		result+="strcmpiterate1" + i + ":\n";
        result+="PUSHIMM 1\n";
        result+="ADD\n";
        result+="DUP\n";
        result+="PUSHIND\n";
        result+="PUSHIMMCH '\0'\n";
        result+="EQUAL\n";
        result+="PUSHIMM 1\n";
        result+="SUB\n";
        result+="JUMPC strcmpiterate1"+i+"\n";
		result+="SWAP\n";
		result+="SUB\n";
		result+="STOREABS 3003\n"; // length of string 2
		result+="strcmplength2" + i + ":\n";
		result+="DUP\n";
		result+="PUSHIMM 1\n";
		result+="SUB\n";
    	result+="strcmpiterate2" + i + ":\n";
        result+="PUSHIMM 1\n";
        result+="ADD\n";
        result+="DUP\n";
        result+="PUSHIND\n";
        result+="PUSHIMMCH '\0'\n";
        result+="EQUAL\n";
        result+="PUSHIMM 1\n";
        result+="SUB\n";
        result+="JUMPC strcmpiterate2"+i+"\n";
		result+="SWAP\n";
		result+="SUB\n";
		result+="STOREABS 3004\n"; // length of string 1
		result+="PUSHABS 3004\n";
		result+="PUSHABS 3003\n";
		result+="EQUAL \n";
		result+="JUMPC strcmpiterate3"+i+"\n";

		result+="PUSHABS 3004\n";
		result+="PUSHABS 3003\n";
		result+="CMP\n";
		result+="PUSHIMM 1\n";
		result+="ADD\n";
		result+="JUMPC first_length"+i+"\n";
		result+="PUSHABS 3004\n";
		result+="STOREABS 3006\n";
		result+="PUSHABS 3003\n";
		result+="STOREABS 3008\n"; // smaller string length
		result+="PUSHABS 3001\n";
		result+="STOREABS 3009\n"; // smaller string address

		result+="JUMP fill"+i+"\n";

		result+="first_length" + i + ":\n";
		result+="PUSHABS 3003\n";
		result+="STOREABS 3006\n";
		result+="PUSHABS 3004\n";
		result+="STOREABS 3008\n";// smaller string length
		result+="PUSHABS 3002\n";
		result+="STOREABS 3009\n"; // smaller string address

		result+="fill" + i + ":\n";
		result+="PUSHABS 3006\n";
		result+="PUSHIMM 1\n";
		result+="ADD\n";
		result+="MALLOC\n";
		result+="STOREABS 3007\n"; // new string address
		result+="PUSHIMM -1\n";
		result+="STOREABS 3005\n"; // counter
		result+="strcmpiterate4" + i + ":\n";
        result+="PUSHABS 3005\n";
        result+="PUSHIMM 1\n";
        result+="ADD\n";
        result+="STOREABS 3005\n";
        result+="PUSHABS 3005\n";
        result+="PUSHIMM 1\n";
        result+="ADD\n";
        result+="PUSHABS 3008\n";
        result+="GREATER\n";
        result+="JUMPC pad"+i+"\n";
        result+="PUSHABS 3005\n";
        result+="PUSHABS 3009\n";
        result+="ADD\n";
        result+="PUSHIND\n";
        result+="PUSHABS 3007\n";
        result+="PUSHABS 3005\n";
        result+="ADD\n";
        result+="SWAP\n";
        result+="STOREIND\n";
        result+="JUMP strcmpiterate4"+i+"\n";
        result+="pad" + i + ":\n";
		result+="PUSHABS 3005\n";
		result+="PUSHIMM 1\n";
		result+="ADD\n";
		result+="PUSHABS 3006\n";
		result+="GREATER\n";
		result+="JUMPC prep"+i+"\n";
		result+="PUSHIMMCH ' '\n";
		result+="PUSHABS 3007\n";
		result+="PUSHABS 3005\n";
		result+="ADD\n";
		result+="SWAP\n";
		result+="STOREIND\n";
		result+="PUSHABS 3005\n";
		result+="PUSHIMM 1\n";
		result+="ADD\n";
		result+="STOREABS 3005\n";
		result+="JUMP pad"+i+"\n";
        
		result+="prep" + i + ":\n";
		result+="PUSHABS 3005\n";
		result+="PUSHABS 3007\n";
		result+="ADD\n";
		result+="PUSHIMMCH '\0'\n";
		result+="STOREIND\n";
		result+="PUSHABS 3008\n";
		result+="PUSHABS 3003\n";
		result+="EQUAL\n";
		result+="JUMPC set1"+i+"\n";
		result+="PUSHABS 3007\n";
		result+="STOREABS 3002\n";
		result+="JUMP start"+i+"\n";

		result+="set1" + i + ":\n";
		result+="PUSHABS 3007\n";
		result+="STOREABS 3001\n";
		result+="JUMP start"+i+"\n";

		result+="strcmpiterate3" + i + ":\n";
		result+="PUSHABS 3003\n";
		result+="STOREABS 3006\n";

		result+="start" + i + ":\n";
		result+="PUSHIMM -1\n";
		result+="STOREABS 3005\n"; // counter
		result+="PUSHIMM 1\n";
		result+="loop" + i + ":\n";
        result+="ADDSP -1\n";
        result+="PUSHABS 3005\n";
        result+="PUSHIMM 1\n";
        result+="ADD\n";
        result+="STOREABS 3005\n";
        result+="PUSHABS 3005\n";
        result+="PUSHABS 3006\n";
        result+="EQUAL\n";
        result+="JUMPC finalpre"+i+"\n";
        result+="PUSHABS 3005\n";
        result+="PUSHABS 3001\n";
        result+="ADD\n";
        result+="PUSHIND\n";
        result+="PUSHABS 3005\n";
        result+="PUSHABS 3002\n";
        result+="ADD\n";
        result+="PUSHIND\n";
        result+="CMP\n";
        result+="DUP\n";
        result+="PUSHIMM 0\n";
        result+="EQUAL\n";
        result+="JUMPC loop"+i+"\n";
		result+="finalpre" + i + ":\n";
		result+="PUSHSP\n";
		result+="PUSHIMM 0\n";
		result+="GREATER\n";
		result+="JUMPC finalstrcmp"+i+"\n";
		result+="PUSHIMM 0\n";
		result+="finalstrcmp" + i + ":\n";

		return result;
	}

	static String str_concat(int i){
		String result = "strconcat"+ i + ":\n";
		result+="DUP\n";
		result+="STOREABS 3000\n"; // string 2 address
		result+="SWAP\n";
		result+="DUP\n";
		result+="STOREABS 3001\n"; // string 1 address
		result+="length1" + i + ":\n";
		result+="DUP\n";
		result+="PUSHIMM 1\n";
		result+="SUB\n";
		result+="strconcatiterate1" + i + ":\n";
        result+="PUSHIMM 1\n";
        result+="ADD \n";
        result+="DUP\n";
        result+="PUSHIND\n";
        result+="PUSHIMMCH '\0'\n";
		result+="EQUAL\n";
        result+="PUSHIMM 1\n";
        result+="SUB\n";
        result+="JUMPC strconcatiterate1"+i+"\n";
		result+="SWAP\n";
		result+="SUB\n";
		result+="SWAP\n";
		result+="length2" + i + ":\n";
		result+="DUP\n";
		result+="PUSHIMM 1\n";
		result+="SUB\n";
		result+="strconcatiterate2" + i + ":\n";
        result+="PUSHIMM 1\n";
        result+="ADD\n";
        result+="DUP\n";
        result+="PUSHIND\n";
        result+="PUSHIMMCH '\0'\n";
        result+="EQUAL\n";
        result+="PUSHIMM 1\n";
        result+="SUB\n";
        result+="JUMPC strconcatiterate2"+i+"\n";
		result+="SWAP\n";
		result+="SUB\n";
		// allocate memory for both strings
		result+="ADD\n";
		result+="PUSHIMM 1\n";
		result+="ADD\n";
		result+="MALLOC\n";
		result+="DUP\n";
		result+="STOREABS 3002\n"; // concat string address 
		result+="DUP\n";
		result+="STOREABS 3003\n"; // current pointer
		result+="PUSHABS 3001\n";
		result+="strconcatiterate3" + i + ":\n";
		result+="PUSHIND\n";
		result+="DUP\n";
		result+="PUSHIMMCH '\0'\n";
		result+="EQUAL\n";
		result+="JUMPC end1"+i+"\n";
		result+="STOREIND\n";
		result+="PUSHABS 3003\n";
		result+="PUSHIMM 1\n";
		result+="ADD\n";
		result+="DUP\n";
		result+="STOREABS 3003\n";
		result+="PUSHABS 3001\n";
		result+="PUSHIMM 1\n";
		result+="ADD\n";
		result+="DUP\n";
		result+="STOREABS 3001\n";
		result+="JUMP strconcatiterate3"+i+"\n";
		result+="end1" + i + ":\n";
		result+="ADDSP -1\n";
		result+="PUSHABS 3000\n";
		result+="strconcatiterate4" + i + ":\n";
		result+="PUSHIND\n";
		result+="DUP\n";
		result+="PUSHIMMCH '\0'\n";
		result+="EQUAL\n";
		result+="JUMPC finalstrconcat"+i+"\n";
		result+="STOREIND\n";
		result+="PUSHABS 3003\n";
		result+="PUSHIMM 1\n";
		result+="ADD\n";
		result+="DUP\n";
		result+="STOREABS 3003\n";
		result+="PUSHABS 3000\n";
		result+="PUSHIMM 1\n";
		result+="ADD\n";
		result+="DUP\n";
		result+="STOREABS 3000\n";
		result+="JUMP strconcatiterate4"+i+"\n";
		result+="finalstrconcat" + i + ":\n";;
		result+="ADDSP -1\n";
		result+="PUSHIMMCH '\0'\n";
		result+="STOREIND\n";
		result+="PUSHABS 3002\n";

		return result;
	}

	static String str_repeat(int i){
		String result = "strrepeat"+ i + ":\n";
		result+="SWAP\n";
		result+="DUP\n";
		result+="STOREABS 3000\n"; // string address
		result+="PUSHIMM 0\n";
		result+="STOREABS 3004\n"; // counter for new string
		result+="PUSHIMM 1\n";
		result+="MALLOC\n";
		result+="STOREABS 3002\n"; // new string address default of 1 spot
		result+="length" + i + ":\n";
		result+="DUP\n";
		result+="PUSHIMM 1\n";
		result+="SUB\n";
		result+="strrepeatiterate" + i + ":\n";
        result+="PUSHIMM 1\n";
        result+="ADD\n"; 
        result+="DUP\n";
        result+="PUSHIND\n";
        result+="PUSHIMMCH '\0'\n";
        result+="EQUAL\n";
        result+="PUSHIMM 1\n";
        result+="SUB\n";
        result+="JUMPC strrepeatiterate"+i+"\n";
		result+="SWAP\n";
		result+="SUB\n";

		result+="alloc" + i + ":\n";;
		result+="DUP\n";
		result+="STOREABS 3001\n"; // string length
		result+="TIMES\n";
		result+="DUP\n";
		result+="STOREABS 3005\n"; // new string length
		result+="PUSHIMM 1\n";
		result+="LESS\n";
		result+="JUMPC finalstrrepeat"+i+"\n";
		result+="PUSHABS 3005\n";
		result+="PUSHIMM 1\n";
		result+="ADD\n";
		result+="MALLOC\n";
		result+="STOREABS 3002\n"; // new string address


		result+="copy" + i + ":\n";
    	result+="restart" + i + ":\n";
        result+="PUSHIMM 0\n";
        result+="STOREABS 3003\n"; // counter for original string
        result+="strrepeatiterate2" + i + ":\n";
		result+="PUSHABS 3003\n";
		result+="PUSHABS 3001\n";
		result+="EQUAL\n";
		result+="JUMPC restart"+i+"\n"; // reset counter to 0
		result+="PUSHABS 3004\n";
		result+="PUSHABS 3005\n";
		result+="EQUAL\n";
		result+="JUMPC finalstrrepeat"+i+"\n"; // end loop
		result+="PUSHABS 3000\n";
		result+="PUSHABS 3003\n";
		result+="ADD\n";
		result+="PUSHIND\n";
		result+="PUSHABS 3004\n";
		result+="PUSHABS 3002\n";
		result+="ADD\n";
		result+="SWAP\n";
		result+="STOREIND\n";
		result+="PUSHABS 3003\n";
		result+="PUSHIMM 1\n";
		result+="ADD\n";
		result+="STOREABS 3003\n";
		result+="PUSHABS 3004\n";
		result+="PUSHIMM 1\n";
		result+="ADD\n";
		result+="STOREABS 3004\n";
		result+="JUMP strrepeatiterate2"+i+"\n";
		result+="finalstrrepeat" + i + ":\n";
		result+="PUSHABS 3004\n";
		result+="PUSHABS 3002\n";
		result+="ADD\n";
		result+="PUSHIMMCH '\0'\n";
		result+="STOREIND\n";
		result+="PUSHABS 3002\n";
		return result;
	}

	static String str_rev(int i){
		String result = "strrev"+ i + ":\n";
		result += "DUP\n";
		result += "STOREABS 3001\n"; // starting memory address
		result += "DUP\n";
		result += "STOREABS 3000\n"; // current pointer
		result += "DUP\n";
		result += "strreviterate1" + i + ":\n";
    	result += "PUSHIND\n";
    	result += "DUP\n";
    	result += "PUSHIMMCH '\0'\n";
    	result += "EQUAL\n";
    	result += "JUMPC alloc"+i+"\n";
    	result += "PUSHABS 3000\n";
    	result += "PUSHIMM 1\n";
    	result += "ADD\n";
    	result += "DUP\n";
    	result += "STOREABS 3000\n";
    	result += "JUMP strreviterate1"+i+"\n";
		result += "alloc" + i + ":\n";
    	result += "ADDSP -1\n";
    	result += "PUSHABS 3000\n";
    	result += "PUSHABS 3001\n";
    	result += "SUB\n";
    	result += "DUP\n";
    	result += "STOREABS 3002\n"; // length
    	result += "PUSHIMM 1\n";
    	result += "ADD\n";
    	result += "MALLOC\n";
    	result += "DUP\n";
    	result += "STOREABS 3001\n"; // starting memory address
		result += "DUP\n";
		result += "STOREABS 3000\n"; // current pointer
		result += "PUSHABS 3002\n";
		result += "PUSHIMM 0\n";
		result += "EQUAL\n";
		result += "JUMPC finalstrrev"+i+"\n";
		result += "write" + i + ":\n";
		result += "PUSHIMM 1\n";
		result += "PUSHABS 3002\n";
		result += "EQUAL\n";
		result += "JUMPC push"+i+"\n";
		result += "PUSHABS 3002\n";
		result += "PUSHIMM 1\n";
		result += "SUB\n";
		result += "STOREABS 3002\n";
		result += "SWAP\n";
		result += "STOREIND\n";
		result += "PUSHABS 3000\n";
		result += "PUSHIMM 1\n";
		result += "ADD\n";
		result += "DUP\n";
		result += "STOREABS 3000\n";
		result += "JUMP write"+i+"\n";
		result += "push" + i + ":\n";
		result += "SWAP\n";
		result += "STOREIND\n";
		result += "PUSHABS 3000\n";
		result += "PUSHIMM 1\n";
		result += "ADD\n";
		result += "finalstrrev" + i + ":\n";
		result += "PUSHIMMCH '\0'\n";
		result += "STOREIND \n";
		result += "ADDSP -1\n";
		result += "PUSHABS 3001\n";

		return result;
	}
}
