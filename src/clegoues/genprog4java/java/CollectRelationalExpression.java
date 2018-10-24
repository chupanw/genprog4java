package clegoues.genprog4java.java;

import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.InfixExpression;

import java.util.Set;

public class CollectRelationalExpression extends ASTVisitor {
    Set<Expression> relationalExprs;

    public CollectRelationalExpression(Set<Expression> relationalExprs) {
        this.relationalExprs = relationalExprs;
    }

    @Override
    public boolean visit(InfixExpression node) {
        InfixExpression.Operator op = node.getOperator();
        if (op == InfixExpression.Operator.LESS ||
                op == InfixExpression.Operator.LESS_EQUALS ||
                op == InfixExpression.Operator.GREATER ||
                op == InfixExpression.Operator.GREATER_EQUALS) {
            relationalExprs.add(node);
        }
        return true;
    }
}
