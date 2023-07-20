package assignment2;

import java.util.ArrayList;

public class BlockNode extends ASTNode{
    ArrayList<ASTNode> statements;
    LoopStmtNode loop;
    
    public BlockNode(){
        nodeType = "Block";
    }
}
