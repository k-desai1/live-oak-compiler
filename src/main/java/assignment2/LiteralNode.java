package assignment2;

public class LiteralNode extends ASTNode{
    String literal;
    String type; 

    public LiteralNode(String literal){
        nodeType = "Literal";
        type = "String";
        this.literal = literal;
    }
}
