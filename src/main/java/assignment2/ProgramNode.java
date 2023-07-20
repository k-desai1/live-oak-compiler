package assignment2;

import java.util.ArrayList;

public class ProgramNode extends ASTNode {
    ArrayList<MethodDeclNode> children;

    public ProgramNode(){
        nodeType = "Program";
    }
}