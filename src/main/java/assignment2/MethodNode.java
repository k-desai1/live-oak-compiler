package assignment2;

import java.util.ArrayList;

public class MethodNode extends ASTNode{
    String name;
    ArrayList<ASTNode> actuals;

    public MethodNode(String name, ArrayList<ASTNode> actuals){
        nodeType = "Method";
        this.name = name;
        this.actuals = actuals;
    }
}
