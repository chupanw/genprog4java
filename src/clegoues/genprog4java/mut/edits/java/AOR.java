package clegoues.genprog4java.mut.edits.java;

import clegoues.genprog4java.main.Configuration;
import clegoues.genprog4java.mut.EditHole;
import clegoues.genprog4java.mut.RewriteFinalizer;
import clegoues.genprog4java.mut.holes.java.ExpHole;
import clegoues.genprog4java.mut.holes.java.JavaLocation;
import org.eclipse.jdt.core.dom.*;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;

import java.util.*;

import static org.eclipse.jdt.core.dom.InfixExpression.Operator.*;

/**
 * Arithmetic Operation Replacement
 *
 * Switch between +, -, *, /, %
 */
public class AOR extends JavaEditOperation {
    /**
     * java.lang.Integer
     * java.lang.Short
     * java.lang.Character
     * java.lang.Long
     * java.lang.Float
     * java.lang.Double
     */
    String type;
    InfixExpression.Operator op;
    public InfixExpression locationExpr;
    static Random rand = new Random(Configuration.seed);

    public AOR(JavaLocation location, EditHole source) {
        super(location, source);
        this.type = randomType();
        locationExpr = (InfixExpression) ((ExpHole)this.getHoleCode()).getLocationExp();
        this.op = randomOp(locationExpr.getOperator());
    }

    @Override
    public boolean isExpMutation() {
        return true;
    }

    @Override
    public void edit(ASTRewrite rewriter) {
        AST ast = rewriter.getAST();
        InfixExpression infix = ast.newInfixExpression();
        infix.setLeftOperand(ast.newSimpleName("left"));
        infix.setRightOperand(ast.newSimpleName("right"));
        infix.setOperator(this.op);

        MethodDeclaration method = genMethodDeclaration(rewriter, "AOR", infix, true);
        MethodInvocation mi = genMethodInvocation(rewriter, "AOR", true);
        rewriter.replace(locationExpr, mi, null);
        TypeDeclaration typeDecl = getTypeDeclaration(locationExpr);
        ListRewrite lsr = rewriter.getListRewrite(typeDecl, TypeDeclaration.BODY_DECLARATIONS_PROPERTY);
        lsr.insertLast(method, null);
    }

    private MethodDeclaration genMethodDeclaration(ASTRewrite rewriter, String methodName, Expression returnExpression, boolean addParameters) {
        AST ast = rewriter.getAST();
        MethodDeclaration method = ast.newMethodDeclaration();
        Block body = ast.newBlock();
        method.setBody(body);
        method.setName(ast.newSimpleName(methodName));
        method.setReturnType2(ast.newSimpleType(ast.newName(this.type)));
        if (addParameters) {
            SingleVariableDeclaration p1 = ast.newSingleVariableDeclaration();
            SingleVariableDeclaration p2 = ast.newSingleVariableDeclaration();
            p1.setType(ast.newSimpleType(ast.newName(this.type)));
            p1.setName(ast.newSimpleName("left"));
            p2.setType(ast.newSimpleType(ast.newName(this.type)));
            p2.setName(ast.newSimpleName("right"));
            method.parameters().add(p1);
            method.parameters().add(p2);
        }
        ReturnStatement ret = ast.newReturnStatement();
        ret.setExpression(returnExpression);
        body.statements().add(ret);

        return method;
    }

    private MethodInvocation genMethodInvocation(ASTRewrite rewriter, String methodName, boolean addArguments) {
        AST ast = rewriter.getAST();
        MethodInvocation mi = ast.newMethodInvocation();
        mi.setName(ast.newSimpleName(methodName));
        if (addArguments) {
            mi.arguments().add((Expression) ASTNode.copySubtree(ast, locationExpr.getLeftOperand()));
            mi.arguments().add((Expression) ASTNode.copySubtree(ast, locationExpr.getRightOperand()));
        }
        return mi;
    }


    @Override
    public void methodEdit(ASTRewrite rewriter, HashMap<ASTNode, List<ASTNode>> nodeStore, RewriteFinalizer finalizer) {
        AST ast = rewriter.getAST();
        InfixExpression locationExprCopy = (InfixExpression) ASTNode.copySubtree(ast, locationExpr);

        Expression newExpr = mutate(ast, locationExprCopy);
        MethodDeclaration md = genMethodDeclaration(rewriter, this.getVariantFolder(), newExpr, false);

        MethodInvocation mi = genMethodInvocation(rewriter, this.getVariantFolder(), false);

        applyEditAndUpdateNodeStore(rewriter, mi, nodeStore, locationExpr, locationExprCopy);
        finalizer.markVariantMethod(locationExpr, md, true);
    }

    private InfixExpression.Operator randomOp(InfixExpression.Operator original) {
        ArrayList<InfixExpression.Operator> all = new ArrayList(Arrays.asList(
                PLUS, MINUS, TIMES, DIVIDE, REMAINDER
        ));
        all.remove(original);
        int idx = rand.nextInt(all.size());
        return all.get(idx);
    }

    private String randomType() {
        List<String> all = Arrays.asList(
                "java.lang.Integer",
                "java.lang.Character",
                "java.lang.Short",
                "java.lang.Long",
                "java.lang.Double",
                "java.lang.Float"
        );
        int idx = rand.nextInt(all.size());
        return all.get(idx);
    }


    private Expression mutate(AST ast, InfixExpression original) {
        Set<InfixExpression.Operator> all = new HashSet<>(Arrays.asList(
                PLUS, MINUS, TIMES, DIVIDE, REMAINDER
        ));
        all.remove(original.getOperator());
        Expression otherwise = original;
        for (InfixExpression.Operator op : all) {
            ConditionalExpression cond = ast.newConditionalExpression();
            cond.setExpression(getNextFieldAccess(ast, opToString(op)));
            InfixExpression then = ast.newInfixExpression();
            then.setLeftOperand((Expression) ASTNode.copySubtree(ast, original.getLeftOperand()));
            then.setRightOperand((Expression) ASTNode.copySubtree(ast, original.getRightOperand()));
            then.setOperator(op);
            cond.setThenExpression(then);
            cond.setElseExpression(otherwise);
            ParenthesizedExpression pe = ast.newParenthesizedExpression();
            pe.setExpression(cond);
            otherwise = pe;
        }
        return otherwise;
    }

    private String opToString(InfixExpression.Operator op) {
        switch (op.toString()) {
            case "+":
                return "PLUS";
            case "-":
                return "MINUS";
            case "*":
                return "TIMES";
            case "/":
                return "DIVIDE";
            case "%":
                return "REMAINDER";
            default:
                throw new RuntimeException("Unexpected Op in AOR: " + op);
        }
    }


    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("AOR(" + this.getLocation().getId() + "): ");
        sb.append("(" + locationExpr.toString() + ")");
        sb.append(" -> ");
        sb.append("(" + this.op + " of " + this.type + ")");
        return sb.toString();
    }

    private TypeDeclaration getTypeDeclaration(ASTNode n) {
        if (n != null) {
            if (n instanceof TypeDeclaration) {
                return (TypeDeclaration) n;
            }
            else {
                return getTypeDeclaration(n.getParent());
            }
        }
        else {
            throw new RuntimeException("No surrounding type declaration");
        }
    }
}
