package clegoues.genprog4java.mut.edits.java;

import clegoues.genprog4java.mut.EditHole;
import clegoues.genprog4java.mut.RewriteFinalizer;
import clegoues.genprog4java.mut.holes.java.ExpHole;
import clegoues.genprog4java.mut.holes.java.JavaLocation;
import org.eclipse.jdt.core.dom.*;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;

import java.util.HashMap;
import java.util.List;

import static org.eclipse.jdt.core.dom.InfixExpression.Operator.CONDITIONAL_AND;
import static org.eclipse.jdt.core.dom.InfixExpression.Operator.CONDITIONAL_OR;

/**
 * Logical Connector Replacement
 *
 * Switch between && and ||
 */
public class LCR extends JavaEditOperation {

    InfixExpression locationExpr = (InfixExpression) ((ExpHole)this.getHoleCode()).getLocationExp();
    Expression newExpr = mutate(locationExpr.getAST(), (InfixExpression) ASTNode.copySubtree(locationExpr.getAST(), locationExpr), false);

    public LCR(JavaLocation location, EditHole source) {
        super(location, source);
    }

    @Override
    public boolean isExpMutation() {
        return true;
    }

    @Override
    public void edit(ASTRewrite rewriter) {
        rewriter.replace(locationExpr, newExpr, null);
    }

    @Override
    public void methodEdit(ASTRewrite rewriter, HashMap<ASTNode, List<ASTNode>> nodeStore, RewriteFinalizer finalizer) {
        AST ast = rewriter.getAST();
        InfixExpression locationExprCopy = (InfixExpression) ASTNode.copySubtree(ast, locationExpr);

        MethodDeclaration vm = ast.newMethodDeclaration();
        Block body = ast.newBlock();
        vm.setBody(body);
        vm.setName(ast.newSimpleName(this.getVariantFolder()));
        vm.setReturnType2(ast.newPrimitiveType((PrimitiveType.BOOLEAN)));

        Expression mutated = mutate(ast, locationExprCopy, true);
        newExpr = mutated;
        ReturnStatement ret = ast.newReturnStatement();
        ret.setExpression(mutated);
        body.statements().add(ret);

        MethodInvocation mi = ast.newMethodInvocation();
        mi.setExpression(ast.newThisExpression());
        mi.setName(ast.newSimpleName(getVariantFolder()));

        applyEditAndUpdateNodeStore(rewriter, mi, nodeStore, locationExpr, locationExprCopy);
        finalizer.markVariantMethod(locationExpr, vm, true);
    }

    private Expression mutate(AST ast, InfixExpression original, boolean useOptions) {
        Expression otherwise = original;
        InfixExpression.Operator thenOp = CONDITIONAL_AND;
        String thenOpName = "AND";
        if (original.getOperator() == CONDITIONAL_AND) {
            thenOp = CONDITIONAL_OR;
            thenOpName = "OR";
        }

        ConditionalExpression cond = ast.newConditionalExpression();
        if (useOptions) {
            cond.setExpression(getNextFieldAccess(ast, thenOpName));
        } else {
            cond.setExpression(ast.newBooleanLiteral(false));
        }
        InfixExpression then = ast.newInfixExpression();
        then.setLeftOperand((Expression) ASTNode.copySubtree(ast, original.getLeftOperand()));
        then.setRightOperand((Expression) ASTNode.copySubtree(ast, original.getRightOperand()));
        then.setOperator(thenOp);
        cond.setThenExpression(then);
        cond.setElseExpression(otherwise);
        ParenthesizedExpression pe = ast.newParenthesizedExpression();
        pe.setExpression(cond);
        return pe;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("LCR(" + this.getLocation().getId() + "): ");
        sb.append("(" + locationExpr.toString() + ")");
        sb.append(" -> ");
        sb.append("(" + newExpr.toString() + ")");
        return sb.toString();
    }
}
