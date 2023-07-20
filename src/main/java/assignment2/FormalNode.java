package assignment2;

public class FormalNode extends ASTNode{
    String type;
    String identifier;

    public FormalNode(String type, String identifier){
        nodeType = "Formal";
        this.type = type;
        this.identifier = identifier;
    }
}
