package clegoues.genprog4java.mut.edits.java;

import clegoues.genprog4java.mut.holes.java.JavaLocation;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;

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

		/*
		 * The outer block prevent cases where the then block of an if statement is replaced.
		 * For example:
		 * 	if (a)
		 * 		// do A	<- inserting a new if here would make the else below follow the wrong if!
		 *  else
		 * 		// do !A
		 */
		Block outerBlock = rewriter.getAST().newBlock();
		IfStatement ife = rewriter.getAST().newIfStatement();

		// condition
		Expression cond = getNextFieldAccessNot(ife);
		// then block
		Block thenBlock = ife.getAST().newBlock();
		thenBlock.statements().add(originalCodeNode);

		ife.setExpression(cond);
		ife.setThenStatement(thenBlock);

		outerBlock.statements().add(ife);

		/* Replace the faulty statement with the empty Block. */
        applyEditAndUpdateNodeStore(rewriter, outerBlock, nodeStore, locationNode, originalCodeNode);
	}

	@Override
	public String toString() {
		return "StmtDelete(" + this.getLocation().getId() + ")";
	}
}

