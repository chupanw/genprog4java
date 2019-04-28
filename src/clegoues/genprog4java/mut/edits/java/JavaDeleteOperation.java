package clegoues.genprog4java.mut.edits.java;

import org.eclipse.jdt.core.dom.*;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;

import clegoues.genprog4java.mut.holes.java.JavaLocation;

import java.util.HashMap;

public class JavaDeleteOperation extends JavaEditOperation {
	

	public JavaDeleteOperation(JavaLocation location) {
		super(location);
	}
	@Override
	public void edit(final ASTRewrite rewriter) {
		ASTNode locationNode = ((JavaLocation) this.getLocation()).getCodeElement(); 
		
		  Block emptyBlock = (Block) rewriter.getAST().createInstance(Block.class);

	        /* Replace the faulty statement with the empty Block. */
	        rewriter.replace(locationNode, emptyBlock, null);
	        
			
	}

	@Override
	public void mergeEdit(ASTRewrite rewriter, HashMap<ASTNode, ASTNode> nodeStore) {
		ASTNode locationNode = ((JavaLocation) this.getLocation()).getCodeElement();

		ASTNode originalCodeNode = ASTNode.copySubtree(rewriter.getAST(), locationNode);

		IfStatement ife = rewriter.getAST().newIfStatement();

		// condition
		Expression cond = getNextFieldAccessNot(ife);
		// then block
		Block thenBlock = ife.getAST().newBlock();
		thenBlock.statements().add(originalCodeNode);

		ife.setExpression(cond);
		ife.setThenStatement(thenBlock);

		/* Replace the faulty statement with the empty Block. */
        applyEditAndUpdateNodeStore(rewriter, ife, nodeStore, locationNode, originalCodeNode);
	}

	@Override
	public String toString() {
		return "StmtDelete(" + this.getLocation().getId() + ")";
	}
}

