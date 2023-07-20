package assignment2;

public class LoopStmtNode extends ASTNode{
    ASTNode condition;
    BlockNode block;
    
    public LoopStmtNode(){
        nodeType = "Loop";
    }
}
