package clegoues.genprog4java.mut;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.Type;

public class MyParameter {
    private Type type;
    private SimpleName name;
    private AST target;
    private boolean isVarargs;
    MyParameter(Type t, SimpleName n, AST target, boolean isVarargs) {
        this.type = t;
        this.name = n;
        this.target = target;
        this.isVarargs = isVarargs;
    }

    public Type getType() {
        if (isVarargs) {
            return target.newArrayType((Type) ASTNode.copySubtree(target, type));
        }
        else {
            return (Type) ASTNode.copySubtree(target, type);
        }
    }

    public SimpleName getName() {
        return (SimpleName) ASTNode.copySubtree(target, name);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof MyParameter) {
            MyParameter that = (MyParameter) obj;
            return this.type.equals(that.type) && this.name.equals(that.name);
        }
        else {
            return false;
        }
    }
}
