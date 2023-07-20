package assignment2;

public class NumNode extends ASTNode{
    int num;
    String type;

    public NumNode(int num){
        nodeType = "Num";
        type = "int";
        this.num = num;
    }
}
