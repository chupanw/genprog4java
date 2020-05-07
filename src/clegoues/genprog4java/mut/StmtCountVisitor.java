package clegoues.genprog4java.mut;

import org.eclipse.jdt.core.dom.*;

public class StmtCountVisitor extends ASTVisitor {
    private int stmtCount = 0;

    public int getStmtCount() {
        return stmtCount;
    }

    @Override
    public boolean visit(AssertStatement node) {
        stmtCount++;
        return super.visit(node);
    }

    @Override
    public boolean visit(ContinueStatement node) {
        stmtCount++;
        return super.visit(node);
    }

    @Override
    public boolean visit(DoStatement node) {
        stmtCount++;
        return super.visit(node);
    }

    @Override
    public boolean visit(EmptyStatement node) {
        stmtCount++;
        return super.visit(node);
    }

    @Override
    public boolean visit(EnhancedForStatement node) {
        stmtCount++;
        return super.visit(node);
    }

    @Override
    public boolean visit(ExpressionStatement node) {
        stmtCount++;
        return super.visit(node);
    }

    @Override
    public boolean visit(ForStatement node) {
        stmtCount++;
        return super.visit(node);
    }

    @Override
    public boolean visit(IfStatement node) {
        stmtCount++;
        return super.visit(node);
    }

    @Override
    public boolean visit(LabeledStatement node) {
        stmtCount++;
        return super.visit(node);
    }

    @Override
    public boolean visit(ReturnStatement node) {
        stmtCount++;
        return super.visit(node);
    }

    @Override
    public boolean visit(SwitchStatement node) {
        stmtCount++;
        return super.visit(node);
    }

    @Override
    public boolean visit(SynchronizedStatement node) {
        stmtCount++;
        return super.visit(node);
    }

    @Override
    public boolean visit(ThrowStatement node) {
        stmtCount++;
        return super.visit(node);
    }

    @Override
    public boolean visit(TryStatement node) {
        stmtCount++;
        return super.visit(node);
    }

    @Override
    public boolean visit(TypeDeclarationStatement node) {
        stmtCount++;
        return super.visit(node);
    }

    @Override
    public boolean visit(VariableDeclarationStatement node) {
        stmtCount++;
        return super.visit(node);
    }

    @Override
    public boolean visit(WhileStatement node) {
        stmtCount++;
        return super.visit(node);
    }

    @Override
    public boolean visit(BreakStatement node) {
        stmtCount++;
        return super.visit(node);
    }
}
