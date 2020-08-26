package clegoues.genprog4java.mut.edits.java;

import clegoues.genprog4java.mut.RewriteFinalizer;
import clegoues.genprog4java.mut.holes.java.JavaLocation;
import org.eclipse.jdt.core.dom.*;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;

import java.util.HashMap;
import java.util.List;

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
	public void mergeEdit(ASTRewrite rewriter, HashMap<ASTNode, List<ASTNode>> nodeStore) {
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
        applyEditAndUpdateNodeStore(rewriter, outerBlock, nodeStore, locationNode, originalCodeNode, null);
	}

	@Override
	public void methodEdit(ASTRewrite rewriter, HashMap<ASTNode, List<ASTNode>> nodeStore, RewriteFinalizer finalizer) {
		AST ast = rewriter.getAST();
		ASTNode locationNode = ((JavaLocation) this.getLocation()).getCodeElement();
		ASTNode locationNodeCopy = ASTNode.copySubtree(ast, locationNode);
		MethodDeclaration mutatedMethod = getMethodDeclaration(locationNode);

		MethodDeclaration vm = ast.newMethodDeclaration();
		vm.setReturnType2(ast.newPrimitiveType(PrimitiveType.VOID));
		if ((mutatedMethod.getModifiers() & Modifier.STATIC) != 0) {
			vm.modifiers().add(ast.newModifier(Modifier.ModifierKeyword.STATIC_KEYWORD));
		}
		vm.setName(ast.newSimpleName(getVariantFolder()));
		for (Type t : (List<Type>) mutatedMethod.thrownExceptionTypes()) {
			vm.thrownExceptionTypes().add(ASTNode.copySubtree(ast, t));
		}
		Block body = ast.newBlock();
		vm.setBody(body);
		IfStatement ife = ast.newIfStatement();
		Block thenBlock = ast.newBlock();
		thenBlock.statements().add(locationNodeCopy);
		ife.setExpression(getNextFieldAccessNot(ife));
		ife.setThenStatement(thenBlock);
		body.statements().add(ife);

		MethodInvocation mi = ast.newMethodInvocation();
//		mi.setExpression(ast.newThisExpression());
		mi.setName(ast.newSimpleName(getVariantFolder()));
		ExpressionStatement mis = ast.newExpressionStatement(mi);

		Block block = ast.newBlock();
		block.statements().add(mis);

		// Note: the order of the following calls matters!
		applyEditAndUpdateNodeStore(rewriter, block, nodeStore, locationNode, locationNodeCopy, finalizer);
		finalizer.markVariantMethod(locationNode, vm, false);
		finalizer.checkSpecialStatements((Statement) locationNode, null, vm, nodeStore);
		finalizer.recordVariantCallsite(locationNode, vm, block);
	}

	@Override
	public String toString() {
		return "StmtDelete(" + this.getLocation().getId() + ")";
	}
}

