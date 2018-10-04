package clegoues.genprog4java.mut.edits.java;

import java.util.HashMap;

import org.eclipse.jdt.core.dom.*;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;

import clegoues.genprog4java.mut.holes.java.StatementHole;
import clegoues.genprog4java.mut.holes.java.JavaLocation;
import clegoues.genprog4java.mut.EditHole;

public class JavaReplaceOperation extends JavaEditOperation {
	
	public JavaReplaceOperation(JavaLocation location, EditHole source) {
		super(location, source);
	}
	
	@Override
	public void edit(final ASTRewrite rewriter) {
		ASTNode locationNode = ((JavaLocation) this.getLocation()).getCodeElement(); 
		StatementHole fixHole = (StatementHole) this.getHoleCode();
		ASTNode fixCodeNode =
				 ASTNode.copySubtree(rewriter.getAST(), fixHole.getCode());
		rewriter.replace(locationNode, fixCodeNode, null);
	}

	@Override
	public void mergeEdit(ASTRewrite rewriter, HashMap<ASTNode, ASTNode> nodeStore) {
		ASTNode locationNode = ((JavaLocation) this.getLocation()).getCodeElement();
		StatementHole fixHole = (StatementHole) this.getHoleCode();

		ASTNode fixCodeNode = ASTNode.copySubtree(rewriter.getAST(), fixHole.getCode());
		ASTNode originalCodeNode = ASTNode.copySubtree(rewriter.getAST(), locationNode);

		Block newBlock = rewriter.getAST().newBlock();
		IfStatement ife = newBlock.getAST().newIfStatement();

		// condition
		Expression fa = getNextFieldAccess(ife);
		// then block
		Block thenBlock = ife.getAST().newBlock();
		thenBlock.statements().add(fixCodeNode);
		// else block
		Block elseBlock = ife.getAST().newBlock();
		elseBlock.statements().add(originalCodeNode);

		ife.setExpression(fa);
		ife.setThenStatement(thenBlock);
		ife.setElseStatement(elseBlock);

		newBlock.statements().add(ife);

        applyEditAndUpdateNodeStore(rewriter, newBlock, nodeStore, locationNode, originalCodeNode);
	}

	@Override
	public String toString() {
		StatementHole fixHole = (StatementHole) this.getHoleCode();
		return "StmtReplace(" + this.getLocation().getId() + "," + fixHole.getCodeBankId() + ")";
	}
}
