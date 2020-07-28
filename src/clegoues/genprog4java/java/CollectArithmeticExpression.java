package clegoues.genprog4java.java;

import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.InfixExpression;

import java.util.Set;

public class CollectArithmeticExpression extends ASTVisitor {
    Set<Expression> arithmeticExprs;

    public CollectArithmeticExpression(Set<Expression> arithmeticExprs) {
        this.arithmeticExprs = arithmeticExprs;
    }

    @Override
    public boolean visit(InfixExpression node) {
        InfixExpression.Operator op = node.getOperator();
        if (op == InfixExpression.Operator.PLUS ||
                op == InfixExpression.Operator.MINUS ||
                op == InfixExpression.Operator.TIMES ||
                op == InfixExpression.Operator.DIVIDE ||
                op == InfixExpression.Operator.REMAINDER
        ) {
            arithmeticExprs.add(node);
        }
        return true;
    }
}
