package clegoues.genprog4java.mut.edits.java;

import org.eclipse.jdt.core.dom.*;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;

import clegoues.genprog4java.mut.holes.java.StatementHole;
import clegoues.genprog4java.mut.holes.java.JavaLocation;
import clegoues.genprog4java.mut.EditHole;

import java.util.HashMap;


public class JavaAppendOperation extends JavaEditOperation {


	public JavaAppendOperation(JavaLocation location, EditHole source) {
		super(location,source);
	}
	
	public void edit(final ASTRewrite rewriter) {
		ASTNode locationNode = ((JavaLocation) this.getLocation()).getCodeElement(); 
		StatementHole fixHole = (StatementHole) this.getHoleCode();
		ASTNode fixCodeNode =
			 ASTNode.copySubtree(rewriter.getAST(), fixHole.getCode()); 

		Block newNode = locationNode.getAST().newBlock(); 
		if(locationNode instanceof Statement && fixCodeNode instanceof Statement){
			ASTNode stm1 = (Statement)locationNode;
			ASTNode stm2 = (Statement)fixCodeNode;

			stm1 = ASTNode.copySubtree(locationNode.getAST(), stm1);
			stm2 = ASTNode.copySubtree(fixCodeNode.getAST(), stm2);

			newNode.statements().add(stm1);
			newNode.statements().add(stm2);
			rewriter.replace(locationNode, newNode, null);
		}

	}

	@Override
	public void mergeEdit(ASTRewrite rewriter, HashMap<ASTNode, ASTNode> nodeStore) {
		ASTNode locationNode = ((JavaLocation) this.getLocation()).getCodeElement();
		StatementHole fixHole = (StatementHole) this.getHoleCode();

		ASTNode fixCodeNode = ASTNode.copySubtree(rewriter.getAST(), fixHole.getCode());

		Block newBlock = locationNode.getAST().newBlock();
		if(locationNode instanceof Statement && fixCodeNode instanceof Statement){
			ASTNode stm1 = (Statement)locationNode;
			ASTNode stm2 = (Statement)fixCodeNode;

			stm1 = ASTNode.copySubtree(locationNode.getAST(), stm1);
			stm2 = ASTNode.copySubtree(fixCodeNode.getAST(), stm2);

			IfStatement ife = newBlock.getAST().newIfStatement();

			// condition
			Expression fa = getNextFieldAccess(ife);
			// then block
			Block thenBlock = ife.getAST().newBlock();
			thenBlock.statements().add(stm2);

			ife.setExpression(fa);
			ife.setThenStatement(thenBlock);


			newBlock.statements().add(stm1);
			newBlock.statements().add(ife);

            applyEditAndUpdateNodeStore(rewriter, newBlock, nodeStore, locationNode, stm1);
		}
	}

	@Override
	public String toString() {
		StatementHole fixHole = (StatementHole) this.getHoleCode();
		return "StmtAppend(" + this.getLocation().getId() + "," + fixHole.getCodeBankId() + ")";
	}
}
