package assignment2;

public class VarDeclNode extends ASTNode {
    String identifier;
    String type;
    String scope;
    int location;

    public VarDeclNode(String identifier, String type, String scope, int location){
        nodeType = "VarDecl";
        this.identifier = identifier;
        this.type = type;
        this.scope = scope;
        this.location = location;
    }

    public VarDeclNode(){
        
    }

    public String toString(){
        return identifier;
    }
}
