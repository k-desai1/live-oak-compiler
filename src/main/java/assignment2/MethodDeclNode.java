package assignment2;

import java.util.ArrayList;

public class MethodDeclNode extends ASTNode{
    String type;
    String name;
    ArrayList<FormalNode> formals;
    BodyNode body;

    public MethodDeclNode(String type, String name, ArrayList<FormalNode> formals, BodyNode body){
        nodeType = "MethodDecl";
        this.type = type;
        this.name = name;
        this.formals = formals;
        this.body = body;
    }
}
