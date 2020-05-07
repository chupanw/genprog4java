package clegoues.genprog4java.mut;

import org.eclipse.jdt.core.dom.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class VariableDeclarationCollector extends ASTVisitor {
    Map<MyParameter, Expression> vars = new HashMap<>();
    AST ast;

    VariableDeclarationCollector(AST ast) {
        this.ast = ast;
    }

    @Override
    public boolean visit(SingleVariableDeclaration node) {
        throw new RuntimeException("Unexpected SingleVariableDeclaration");
    }

    @Override
    public boolean visit(VariableDeclarationExpression node) {
        Type t = node.getType();
        for (VariableDeclarationFragment f : (List<VariableDeclarationFragment>) node.fragments()) {
            assert f.getInitializer() != null : "null initializer in loop?";
            vars.put(new MyParameter(t, f.getName(), ast, false), f.getInitializer());
        }
        return super.visit(node);
    }

    @Override
    public boolean visit(VariableDeclarationStatement node) {
        Type t = node.getType();
        for (VariableDeclarationFragment f : (List<VariableDeclarationFragment>) node.fragments()) {
            assert f.getInitializer() != null : "null initializer in loop?";
            vars.put(new MyParameter(t, f.getName(), ast, false), f.getInitializer());
        }
        return super.visit(node);
    }
}
