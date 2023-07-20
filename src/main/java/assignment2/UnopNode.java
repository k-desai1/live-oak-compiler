package assignment2;

public class UnopNode extends ASTNode{
    char op;
    ASTNode child;
    public UnopNode(){
        nodeType = "Unop";
    }
}
