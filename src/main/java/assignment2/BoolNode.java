package assignment2;

public class BoolNode extends ASTNode{
    int value;
    String type;

    public BoolNode(String bool){
        nodeType = "Bool";
        type = "bool";
        if (bool.equals("true")){
            this.value = 1;
        }
        else{
            this.value = 0;
        }
    }
}
