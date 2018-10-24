package clegoues.genprog4java.mut.edits.java;

import clegoues.genprog4java.mut.EditHole;
import clegoues.genprog4java.mut.holes.java.ExpHole;
import clegoues.genprog4java.mut.holes.java.JavaLocation;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ConditionalExpression;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;

import java.util.HashMap;

/**
 * Replace relational operators with their boundary counterpart
 *
 *  >  to >=
 *  >= to >
 *  <  to <=
 *  <= to <
 */
public class BoundarySwitcher extends JavaEditOperation {

    InfixExpression locationExpr = (InfixExpression) ((ExpHole)this.getHoleCode()).getLocationExp();
    Expression newExpr = switchRelationalOp(locationExpr);


    public BoundarySwitcher(JavaLocation location, EditHole source) {
        super(location, source);
    }

    @Override
    public void edit(ASTRewrite rewriter) {
        rewriter.replace(locationExpr, newExpr, null);
    }

    @Override
    public void mergeEdit(ASTRewrite rewriter, HashMap<ASTNode, ASTNode> nodeStore) {
        InfixExpression locationExprCopy = (InfixExpression) ASTNode.copySubtree(rewriter.getAST(), locationExpr);

        ConditionalExpression ife = rewriter.getAST().newConditionalExpression();
        ife.setExpression(getNextFieldAccess(ife));
        ife.setThenExpression(newExpr);
        ife.setElseExpression(locationExprCopy);
        applyEditAndUpdateNodeStore(rewriter, ife, nodeStore, locationExpr, locationExprCopy);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("BoundarySwitcher(" + this.getLocation().getId() + ": ");
        sb.append("(" + locationExpr.toString() + ")");
        sb.append(" -> ");
        sb.append("(" + newExpr.toString() + ")");
        return sb.toString();
    }

    InfixExpression switchRelationalOp(InfixExpression e) {
        InfixExpression newExpr = e.getAST().newInfixExpression();
        newExpr.setLeftOperand((Expression)ASTNode.copySubtree(e.getAST(), e.getLeftOperand()));
        newExpr.setRightOperand((Expression)ASTNode.copySubtree(e.getAST(), e.getRightOperand()));
        switch (e.getOperator().toString()) {
            case "<":
                newExpr.setOperator(InfixExpression.Operator.LESS_EQUALS);
                break;
            case "<=":
                newExpr.setOperator(InfixExpression.Operator.LESS);
                break;
            case ">":
                newExpr.setOperator(InfixExpression.Operator.GREATER_EQUALS);
                break;
            case ">=":
                newExpr.setOperator(InfixExpression.Operator.GREATER);
                break;
            default:
                throw new RuntimeException("Unexpected infix operator: " + e.getOperator().toString());
        }
        return newExpr;
    }
}
