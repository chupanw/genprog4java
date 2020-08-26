package clegoues.genprog4java.mut.edits.java;

import clegoues.genprog4java.mut.EditHole;
import clegoues.genprog4java.mut.holes.java.ExpChoiceHole;
import clegoues.genprog4java.mut.holes.java.ExpHole;
import clegoues.genprog4java.mut.holes.java.JavaLocation;
import org.eclipse.jdt.core.dom.*;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;

import java.util.HashMap;
import java.util.List;

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
	public void mergeEdit(ASTRewrite rewriter, HashMap<ASTNode, List<ASTNode>> nodeStore) {
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

		ParenthesizedExpression pe = rewriter.getAST().newParenthesizedExpression();
		pe.setExpression(ternaryExp);

		applyEditAndUpdateNodeStore(rewriter, pe, nodeStore, locationExp, newLocationExp, null);
	}

	@Override
	public String toString() {
		// FIXME: this is lazy
		return "ExpressionRemove(" + this.getLocation().getId() + ")";
	}
	
}

