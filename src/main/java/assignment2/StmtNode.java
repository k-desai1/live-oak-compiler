package assignment2;

public class StmtNode extends ASTNode {
    VarNode var;
    ExprNode expr;
    String value; // to store break, return;

    public StmtNode(VarNode var, ExprNode expr){
        nodeType = "Stmt";
        this.var = var;
        this.expr = expr;
    }
}
