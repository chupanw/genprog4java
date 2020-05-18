package clegoues.genprog4java.mut.edits.java;

import clegoues.genprog4java.mut.EditHole;
import clegoues.genprog4java.mut.holes.java.JavaLocation;
import clegoues.genprog4java.mut.holes.java.StatementHole;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;

import java.util.HashMap;
import java.util.List;

public class JavaSwapOperation extends JavaEditOperation {
	
	public JavaSwapOperation(JavaLocation location, EditHole source) {
		super(location, source);
	}
	@Override
	public void edit(final ASTRewrite rewriter) {
		ASTNode locationNode = ((JavaLocation) this.getLocation()).getCodeElement(); 
		StatementHole fixCode = (StatementHole) this.getHoleCode(); 
		ASTNode fixCodeNode =
			 ASTNode.copySubtree(rewriter.getAST(), fixCode.getCode()); 
		rewriter.replace(locationNode, fixCodeNode, null);
		rewriter.replace(fixCodeNode, ASTNode
				.copySubtree(locationNode.getAST(), locationNode), null); 
	}

	@Override
	public void mergeEdit(ASTRewrite rewriter, HashMap<ASTNode, List<ASTNode>> nodeStore) {
		ASTNode locationNode = ((JavaLocation) this.getLocation()).getCodeElement();
		StatementHole fixCode = (StatementHole) this.getHoleCode();
		ASTNode fixCodeNode =
				ASTNode.copySubtree(rewriter.getAST(), fixCode.getCode());

		Block newBlock1 = rewriter.getAST().newBlock();
		IfStatement ife1 = newBlock1.getAST().newIfStatement();
		newBlock1.statements().add(ife1);
		// condition
		Expression fa1 = getNextFieldAccess(ife1);
		ife1.setExpression(fa1);
		// then block
		Block thenBlock1 = ife1.getAST().newBlock();
		thenBlock1.statements().add(ASTNode.copySubtree(rewriter.getAST(), fixCodeNode));
		ife1.setThenStatement(thenBlock1);
		// else block
		Block elseBlock1 = ife1.getAST().newBlock();
		ASTNode origLocationNode1 = ASTNode.copySubtree(rewriter.getAST(), locationNode);
		elseBlock1.statements().add(origLocationNode1);
		ife1.setElseStatement(elseBlock1);
		// assemble
		applyEditAndUpdateNodeStore(rewriter, newBlock1, nodeStore, locationNode, origLocationNode1);


		Block newBlock2 = rewriter.getAST().newBlock();
		IfStatement ife2 = newBlock2.getAST().newIfStatement();
		newBlock2.statements().add(ife2);
		// condition
		ife2.setExpression((Expression) ASTNode.copySubtree(ife2.getAST(), fa1));
		// then block
		Block thenBlock2 = ife2.getAST().newBlock();
		thenBlock2.statements().add(ASTNode.copySubtree(rewriter.getAST(), locationNode));
		ife2.setThenStatement(thenBlock2);
		// else block
		Block elseBlock2 = ife2.getAST().newBlock();
		ASTNode origLocationNode2 = ASTNode.copySubtree(rewriter.getAST(), fixCodeNode);
		elseBlock2.statements().add(origLocationNode2);
		ife2.setElseStatement(elseBlock2);
		// assemble
		applyEditAndUpdateNodeStore(rewriter, newBlock2, nodeStore, fixCode.getCode(), origLocationNode2);
	}

	@Override
	public String toString() {
		StatementHole fixHole = (StatementHole) this.getHoleCode();
		return "StmtSwap(" + this.getLocation().getId() + "," + fixHole.getCodeBankId() + ")";
	}
}
