package clegoues.genprog4java.mut.edits.java;

import java.util.HashMap;

import clegoues.genprog4java.mut.holes.java.ExpHole;
import org.eclipse.jdt.core.dom.*;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;

import clegoues.genprog4java.mut.EditHole;
import clegoues.genprog4java.mut.Mutation;
import clegoues.genprog4java.mut.holes.java.ExpChoiceHole;
import clegoues.genprog4java.mut.holes.java.JavaLocation;

public class ExpressionModRem extends ExpressionReplacer {

	public ExpressionModRem(JavaLocation location, EditHole source) {
		super(location, source);
	}
	@Override
	public void edit(final ASTRewrite rewriter) {
		ExpChoiceHole thisHole = (ExpChoiceHole) this.getHoleCode();
		InfixExpression oldExp = (InfixExpression) thisHole.getCode();
		int whichSide = thisHole.getChoice();
		Expression newCondition;
		switch(whichSide) {
		case 0: newCondition = (Expression) rewriter.createCopyTarget(oldExp.getLeftOperand());
		break;
		case 1:
		default:
			newCondition = (Expression) rewriter.createCopyTarget(oldExp.getRightOperand());
			break;
		}
		this.replaceExp(rewriter, newCondition);
	}

	@Override
	public void mergeEdit(ASTRewrite rewriter, HashMap<ASTNode, ASTNode> nodeStore) {
		ExpChoiceHole thisHole = (ExpChoiceHole) this.getHoleCode();
		InfixExpression oldExp = (InfixExpression) thisHole.getCode();

	    Expression locationExp = ((ExpHole) this.getHoleCode()).getLocationExp();
		ConditionalExpression ternaryExp = rewriter.getAST().newConditionalExpression();
		Expression newLocationExp = (Expression) ASTNode.copySubtree(rewriter.getAST(), locationExp);

		int whichSide = thisHole.getChoice();
		Expression newCondition;
		switch(whichSide) {
			case 0: newCondition = (Expression) rewriter.createCopyTarget(oldExp.getLeftOperand());
				break;
			case 1:
			default:
				newCondition = (Expression) rewriter.createCopyTarget(oldExp.getRightOperand());
				break;
		}

		ternaryExp.setExpression(getNextFieldAccess(ternaryExp));
		ternaryExp.setThenExpression(newCondition);
		ternaryExp.setElseExpression(newLocationExp);

		applyEditAndUpdateNodeStore(rewriter, ternaryExp, nodeStore, locationExp, newLocationExp);
	}

	@Override
	public String toString() {
		// FIXME: this is lazy
		return "ExpressionRemove(" + this.getLocation().getId() + ")";
	}
	
}

