package assignment2;

public class VarNode extends VarDeclNode{
    Object value;

    public VarNode(String identifier){
        nodeType = "Var";
        this.identifier = identifier;
    }
}
