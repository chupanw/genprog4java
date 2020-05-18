package clegoues.genprog4java.mut.edits.java;

import clegoues.genprog4java.mut.EditHole;
import clegoues.genprog4java.mut.holes.java.ExpHole;
import clegoues.genprog4java.mut.holes.java.JavaLocation;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ConditionalExpression;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ParenthesizedExpression;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;

import java.util.HashMap;
import java.util.List;

public class ExpressionModRep extends ExpressionReplacer {
	
	public ExpressionModRep(JavaLocation location, EditHole source) {
		super(location, source);
	}

	@Override
	public void mergeEdit(ASTRewrite rewriter, HashMap<ASTNode, List<ASTNode>> nodeStore) {
		Expression locationExp = ((ExpHole) this.getHoleCode()).getLocationExp();
		Expression newExp = (Expression) ASTNode.copySubtree(rewriter.getAST(), ((ExpHole) this.getHoleCode()).getCode());

		ConditionalExpression ternaryExp = rewriter.getAST().newConditionalExpression();
		Expression newLocationExp = (Expression) ASTNode.copySubtree(rewriter.getAST(), locationExp);

		ternaryExp.setExpression(getNextFieldAccess(ternaryExp));
		ternaryExp.setThenExpression(newExp);
		ternaryExp.setElseExpression(newLocationExp);

		ParenthesizedExpression pe = rewriter.getAST().newParenthesizedExpression();
		pe.setExpression(ternaryExp);

		applyEditAndUpdateNodeStore(rewriter, pe, nodeStore, locationExp, newLocationExp);
	}

	@Override
	public String toString() {
		ExpHole thisHole = (ExpHole) this.getHoleCode();
		Expression locationExp = (Expression) thisHole.getLocationExp();
		Expression newExpCode = (Expression) thisHole.getCode();

		String retval = "ExpressionReplace(" + this.getLocation().getId() + ": ";
		retval += "(" + locationExp.toString() + ") -->";
		retval +=  "(" + newExpCode.toString() + "))";
		return retval;
	}
	
}
