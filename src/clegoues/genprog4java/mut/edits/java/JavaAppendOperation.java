package clegoues.genprog4java.mut.edits.java;

import clegoues.genprog4java.mut.EditHole;
import clegoues.genprog4java.mut.RewriteFinalizer;
import clegoues.genprog4java.mut.holes.java.JavaLocation;
import clegoues.genprog4java.mut.holes.java.StatementHole;
import org.eclipse.jdt.core.dom.*;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;

import java.util.HashMap;
import java.util.List;


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
	public void mergeEdit(ASTRewrite rewriter, HashMap<ASTNode, List<ASTNode>> nodeStore) {
		ASTNode locationNode = ((JavaLocation) this.getLocation()).getCodeElement();
		StatementHole fixHole = (StatementHole) this.getHoleCode();

		ASTNode fixCodeNode = ASTNode.copySubtree(rewriter.getAST(), fixHole.getCode());

		if(locationNode instanceof Statement && fixCodeNode instanceof Statement){
		    if (locationNode.getParent() instanceof Block) {
		    	IfStatement ife = rewriter.getAST().newIfStatement();
		    	ife.setExpression(getNextFieldAccess(ife));
		    	ife.setThenStatement((Statement) ASTNode.copySubtree(rewriter.getAST(), fixCodeNode));
		    	Block surrounding = (Block) locationNode.getParent();
				ListRewrite lw = rewriter.getListRewrite(surrounding, Block.STATEMENTS_PROPERTY);
				lw.insertAfter(ife, locationNode, null);
			}
		    else {
				Block newBlock = locationNode.getAST().newBlock();
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
	}

	@Override
	public void methodEdit(ASTRewrite rewriter, HashMap<ASTNode, List<ASTNode>> nodeStore, RewriteFinalizer finalizer) {
		AST ast = rewriter.getAST();
		ASTNode locationNode = ((JavaLocation) this.getLocation()).getCodeElement();
		StatementHole fixHole = (StatementHole) this.getHoleCode();
		ASTNode locationNodeCopy = ASTNode.copySubtree(ast, locationNode);
		ASTNode fixCodeNodeCopy = ASTNode.copySubtree(ast, fixHole.getCode());
		MethodDeclaration mutatedMethod = getMethodDeclaration(locationNode);

		if(locationNode instanceof Statement && fixCodeNodeCopy instanceof Statement) {
			// new method
			MethodDeclaration vm = ast.newMethodDeclaration();
			Block body = ast.newBlock();
			vm.setName(ast.newSimpleName(this.getVariantFolder()));
			vm.setReturnType2(ast.newPrimitiveType(PrimitiveType.VOID));
			for (Type t : (List<Type>) mutatedMethod.thrownExceptionTypes()) {
				vm.thrownExceptionTypes().add(ASTNode.copySubtree(ast, t));
			}
			vm.setBody(body);
			body.statements().add(locationNodeCopy);
			IfStatement ife = ast.newIfStatement();
			ife.setExpression(getNextFieldAccess(ife));
			Block thenBlock = ast.newBlock();
			thenBlock.statements().add(fixCodeNodeCopy);
			ife.setThenStatement(thenBlock);
			body.statements().add(ife);

			MethodInvocation mi = ast.newMethodInvocation();
			mi.setExpression(ast.newThisExpression());
			mi.setName(ast.newSimpleName(getVariantFolder()));
			ExpressionStatement mis = ast.newExpressionStatement(mi);
			Block block = ast.newBlock();
			block.statements().add(mis);

			applyEditAndUpdateNodeStore(rewriter, block, nodeStore, locationNode, locationNodeCopy);
			finalizer.markVariantMethod(locationNode, vm, false);
			finalizer.checkSpecialStatements((Statement) locationNode, (Statement) fixCodeNodeCopy, nodeStore);
			finalizer.recordVariantCallsite(locationNode, vm, block);
		}
		else {
			throw new RuntimeException("Unexpected APPEND, trying to append " + fixCodeNodeCopy.getClass() + " to " + locationNode.getClass());
		}
	}

	@Override
	public String toString() {
		StatementHole fixHole = (StatementHole) this.getHoleCode();
		return "StmtAppend(" + this.getLocation().getId() + "," + fixHole.getCodeBankId() + ")";
	}
}
