package assignment2;
import java.util.ArrayList;

public class BodyNode extends ASTNode{
    ArrayList<VarDeclNode> varDeclList;
    BlockNode child;
    
    public BodyNode(){
        nodeType = "Body";
    }
}
