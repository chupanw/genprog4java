package clegoues.genprog4java.java;

import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.InfixExpression;

import java.util.Set;

public class CollectLogicalConnectorExpression extends ASTVisitor {
    Set<Expression> logicalConnectorExprs;

    public CollectLogicalConnectorExpression(Set<Expression> logicalConnectorExprs) {
        this.logicalConnectorExprs = logicalConnectorExprs;
    }

    @Override
    public boolean visit(InfixExpression node) {
        InfixExpression.Operator op = node.getOperator();
        if (op == InfixExpression.Operator.CONDITIONAL_AND || op == InfixExpression.Operator.CONDITIONAL_OR) {
            logicalConnectorExprs.add(node);
        }
        return true;
    }
}
