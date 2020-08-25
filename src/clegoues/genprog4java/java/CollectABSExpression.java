package clegoues.genprog4java.java;

import org.eclipse.jdt.core.dom.*;

import java.util.Set;

/**
 * Should we include method invocation and super method invocation?
 */
public class CollectABSExpression extends ASTVisitor {
    Set<Expression> expressions;

    public CollectABSExpression(Set<Expression> expressions) {
        this.expressions = expressions;
    }

    @Override
    public boolean visit(ArrayAccess node) {
        expressions.add(node);
        return true;
    }

    @Override
    public boolean visit(FieldAccess node) {
        expressions.add(node);
        return true;
    }

    @Override
    public boolean visit(InfixExpression node) {
        expressions.add(node);
        return true;
    }

    @Override
    public boolean visit(ConditionalExpression node) {
        expressions.add(node);
        return true;
    }

    @Override
    public boolean visit(SimpleName node) {
        expressions.add(node);
        return true;
    }

    @Override
    public boolean visit(QualifiedName node) {
        expressions.add(node);
        return true;
    }

    @Override
    public boolean visit(NumberLiteral node) {
        expressions.add(node);
        return true;
    }

    @Override
    public boolean visit(ParenthesizedExpression node) {
        expressions.add(node);
        return true;
    }

    @Override
    public boolean visit(PostfixExpression node) {
        expressions.add(node);
        return true;
    }

    @Override
    public boolean visit(PrefixExpression node) {
        expressions.add(node);
        return true;
    }

    @Override
    public boolean visit(SuperFieldAccess node) {
        expressions.add(node);
        return true;
    }
}
