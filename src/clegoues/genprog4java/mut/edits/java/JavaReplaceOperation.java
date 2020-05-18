package clegoues.genprog4java.mut.edits.java;

import clegoues.genprog4java.mut.EditHole;
import clegoues.genprog4java.mut.RewriteFinalizer;
import clegoues.genprog4java.mut.holes.java.JavaLocation;
import clegoues.genprog4java.mut.holes.java.StatementHole;
import org.eclipse.jdt.core.dom.*;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;

import java.util.HashMap;
import java.util.List;

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
	public void mergeEdit(ASTRewrite rewriter, HashMap<ASTNode, List<ASTNode>> nodeStore) {
		ASTNode locationNode = ((JavaLocation) this.getLocation()).getCodeElement();
		StatementHole fixHole = (StatementHole) this.getHoleCode();

		ASTNode fixCodeNode = ASTNode.copySubtree(rewriter.getAST(), fixHole.getCode());
		ASTNode originalCodeNode = ASTNode.copySubtree(rewriter.getAST(), locationNode);

		IfStatement ife = rewriter.getAST().newIfStatement();

		// condition
		Expression fa = getNextFieldAccess(ife);
		// then block
		Block thenBlock = rewriter.getAST().newBlock();
		thenBlock.statements().add(fixCodeNode);
		// else block
		Block elseBlock = rewriter.getAST().newBlock();
		elseBlock.statements().add(originalCodeNode);

		ife.setExpression(fa);
		ife.setThenStatement(thenBlock);
		ife.setElseStatement(elseBlock);

		ASTNode replacement = ife;
		if (locationNode.getParent() instanceof MethodDeclaration) {
			// we have reached the method body level, cannot go higher
			replacement = rewriter.getAST().newBlock();
			((Block) replacement).statements().add(ife);
		}

        applyEditAndUpdateNodeStore(rewriter, replacement, nodeStore, locationNode, originalCodeNode);
	}

	@Override
	public void methodEdit(ASTRewrite rewriter, HashMap<ASTNode, List<ASTNode>> nodeStore, RewriteFinalizer finalizer) {
	    AST ast = rewriter.getAST();
		ASTNode locationNode = ((JavaLocation) this.getLocation()).getCodeElement();
		StatementHole fixHole = (StatementHole) this.getHoleCode();
		ASTNode fixCodeNodeCopy = ASTNode.copySubtree(ast, fixHole.getCode());
		ASTNode locationNodeCopy = ASTNode.copySubtree(ast, locationNode);
		MethodDeclaration mutatedMethod = getMethodDeclaration(locationNode);

		MethodDeclaration vm = ast.newMethodDeclaration();
		vm.setReturnType2(ast.newPrimitiveType(PrimitiveType.VOID));
		vm.setName(ast.newSimpleName(getVariantFolder()));
		for (Type t : (List<Type>) mutatedMethod.thrownExceptionTypes()) {
			vm.thrownExceptionTypes().add(ASTNode.copySubtree(ast, t));
		}
		Block body = ast.newBlock();
		vm.setBody(body);
		IfStatement ife = ast.newIfStatement();
		ife.setExpression(getNextFieldAccess(ife));
		Block thenBlock = ast.newBlock();
		thenBlock.statements().add(fixCodeNodeCopy);
		ife.setThenStatement(thenBlock);
		Block elseBlock = ast.newBlock();
		elseBlock.statements().add(locationNodeCopy);
		ife.setElseStatement(elseBlock);
		body.statements().add(ife);

		MethodInvocation mi = ast.newMethodInvocation();
		mi.setExpression(ast.newThisExpression());
		mi.setName(ast.newSimpleName(getVariantFolder()));
		ExpressionStatement mis = ast.newExpressionStatement(mi);

		Block block = ast.newBlock();
		block.statements().add(mis);

		applyEditAndUpdateNodeStore(rewriter, block, nodeStore, locationNode, locationNodeCopy);
		finalizer.markVariantMethod(locationNode, vm);
		finalizer.checkSpecialStatements((Statement) locationNode, (Statement) fixCodeNodeCopy, nodeStore);
		finalizer.recordVariantCallsite(vm, block);
	}

	@Override
	public String toString() {
		StatementHole fixHole = (StatementHole) this.getHoleCode();
		return "StmtReplace(" + this.getLocation().getId() + "," + fixHole.getCodeBankId() + ")";
	}
}
