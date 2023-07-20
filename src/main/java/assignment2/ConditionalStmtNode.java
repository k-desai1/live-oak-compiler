package assignment2;

public class ConditionalStmtNode extends ASTNode{
    ASTNode condition;
    BlockNode if_block;
    BlockNode else_block;
    
    public ConditionalStmtNode(){
        nodeType = "Conditional";
    }
}
