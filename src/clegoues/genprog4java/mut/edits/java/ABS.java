package clegoues.genprog4java.mut.edits.java;

import clegoues.genprog4java.mut.EditHole;
import clegoues.genprog4java.mut.RewriteFinalizer;
import clegoues.genprog4java.mut.holes.java.ExpHole;
import clegoues.genprog4java.mut.holes.java.JavaLocation;
import org.eclipse.jdt.core.dom.*;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;

import java.util.HashMap;
import java.util.List;

/**
 * Absolute value insertion
 *
 * To avoid equivalent mutants, we replace an expression with its negation
 */
public class ABS extends JavaEditOperation {

    Expression locationExpr = ((ExpHole) this.getHoleCode()).getLocationExp();

    public ABS(JavaLocation location, EditHole source) {
        super(location, source);
    }

    @Override
    public boolean isExpMutation() {
        return true;
    }

    @Override
    public void edit(ASTRewrite rewriter) {
        AST ast = rewriter.getAST();
        PrefixExpression exp = ast.newPrefixExpression();
        exp.setOperator(PrefixExpression.Operator.MINUS);
        exp.setOperand((Expression) ASTNode.copySubtree(ast, locationExpr));
        ParenthesizedExpression pe = ast.newParenthesizedExpression();
        pe.setExpression(exp);
        rewriter.replace(locationExpr, pe, null);
    }

    @Override
    public void methodEdit(ASTRewrite rewriter, HashMap<ASTNode, List<ASTNode>> nodeStore, RewriteFinalizer finalizer) {
        AST ast = rewriter.getAST();
        Expression locationExprCopy = (Expression) ASTNode.copySubtree(rewriter.getAST(), locationExpr);

        ConditionalExpression cond = ast.newConditionalExpression();
        cond.setExpression(getNextFieldAccess(cond));

        PrefixExpression exp = ast.newPrefixExpression();
        exp.setOperator(PrefixExpression.Operator.MINUS);
        exp.setOperand((Expression) ASTNode.copySubtree(ast, locationExpr));
        cond.setThenExpression(exp);
        cond.setElseExpression(locationExprCopy);

        ParenthesizedExpression mutated = ast.newParenthesizedExpression();
        mutated.setExpression(cond);

        applyEditAndUpdateNodeStore(rewriter, mutated, nodeStore, locationExpr, locationExprCopy);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("ABS(" + this.getLocation().getId() + "): ");
        sb.append("(" + locationExpr.toString() + ")");
        sb.append(" -> ");
        sb.append("(" + "-" + locationExpr.toString() + ")");
        return sb.toString();
    }
}
