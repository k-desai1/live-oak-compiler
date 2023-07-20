package assignment2;

public class BinopNode extends ASTNode{
    char op;
    ASTNode left;
    ASTNode right;
    String type; //resulting type

    public BinopNode(){
        nodeType = "Binop";
    }
}
