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

/**
 * Absolute value insertion
 *
 * To avoid equivalent mutants, we replace an expression with its negation
 */
public class ABS extends JavaEditOperation {

    private static final HashMap<Expression, List<String>> typeCache = new HashMap<>();
    static Random rand = new Random(Configuration.seed);

    /**
     * Integer, Character, Short, Long, Double, Float
     */
    String type;
    Expression locationExpr;

    public ABS(JavaLocation location, EditHole source) {
        super(location, source);
        this.locationExpr = ((ExpHole) this.getHoleCode()).getLocationExp();
        this.type = generateRandomTypeFromCache(locationExpr);
    }

    private String generateRandomTypeFromCache(Expression locationExpr) {
        List<String> allTypes = new ArrayList<>(Arrays.asList(
                "java.lang.Integer",
                "java.lang.Character",
                "java.lang.Short",
                "java.lang.Long",
                "java.lang.Double",
                "java.lang.Float"
        ));
        if (typeCache.containsKey(locationExpr)) {
            List<String> existing = typeCache.get(locationExpr);
            allTypes.removeAll(existing);
        }
        String res = "java.lang.Integer";
        if (allTypes.size() > 0) {
            int idx = rand.nextInt(allTypes.size());
            res = allTypes.get(idx);
        }
        if (typeCache.containsKey(locationExpr)) {
            typeCache.get(locationExpr).add(res);
        }
        else {
            ArrayList<String> l = new ArrayList<>();
            l.add(res);
            typeCache.put(locationExpr, l);
        }
        return res;
    }

    @Override
    public boolean isExpMutation() {
        return true;
    }

    @Override
    public void edit(ASTRewrite rewriter) {
        AST ast = rewriter.getAST();
        PrefixExpression exp = ast.newPrefixExpression();
        exp.setOperator(PrefixExpression.Operator.MINUS);
        exp.setOperand((Expression) ASTNode.copySubtree(ast, locationExpr));
        ParenthesizedExpression pe = ast.newParenthesizedExpression();
        pe.setExpression(exp);

        MethodDeclaration method = genMethodDeclaration(rewriter, "ABS", pe, true);
        MethodInvocation mi = genMethodInvocation(rewriter, "UOI", true);

        rewriter.replace(locationExpr, mi, null);
        TypeDeclaration typeDecl = getTypeDeclaration(locationExpr);
        ListRewrite lsr = rewriter.getListRewrite(typeDecl, TypeDeclaration.BODY_DECLARATIONS_PROPERTY);
        lsr.insertLast(method, null);
    }

    @Override
    public void methodEdit(ASTRewrite rewriter, HashMap<ASTNode, List<ASTNode>> nodeStore, RewriteFinalizer finalizer) {
        AST ast = rewriter.getAST();
        Expression locationExprCopy = (Expression) ASTNode.copySubtree(rewriter.getAST(), locationExpr);

        ConditionalExpression cond = ast.newConditionalExpression();
        cond.setExpression(getNextFieldAccess(cond));

        PrefixExpression exp = ast.newPrefixExpression();
        exp.setOperator(PrefixExpression.Operator.MINUS);
        exp.setOperand((Expression) ASTNode.copySubtree(ast, locationExpr));
        cond.setThenExpression(exp);
        cond.setElseExpression(locationExprCopy);

        ParenthesizedExpression mutated = ast.newParenthesizedExpression();
        mutated.setExpression(cond);

        MethodDeclaration md = genMethodDeclaration(rewriter, this.getVariantFolder(), mutated, false);
        MethodInvocation mi = genMethodInvocation(rewriter, this.getVariantFolder(), false);

        applyEditAndUpdateNodeStore(rewriter, mi, nodeStore, locationExpr, locationExprCopy, finalizer);
        finalizer.markVariantMethod(locationExpr, md, true);
    }

    private MethodDeclaration genMethodDeclaration(ASTRewrite rewriter, String methodName, Expression returnExpression, boolean addParameter) {
        AST ast = rewriter.getAST();
        MethodDeclaration method = ast.newMethodDeclaration();
        Block body = ast.newBlock();
        method.setBody(body);
        method.setName(ast.newSimpleName(methodName));
        method.setReturnType2(ast.newSimpleType(ast.newName(this.type)));
        MethodDeclaration mutatedMethod = getMethodDeclaration(locationExpr);
        if ((mutatedMethod.getModifiers() & Modifier.STATIC) != 0) {
            method.modifiers().add(ast.newModifier(Modifier.ModifierKeyword.STATIC_KEYWORD));
        }
        if (addParameter) {
            SingleVariableDeclaration p1 = ast.newSingleVariableDeclaration();
            p1.setType(ast.newSimpleType(ast.newName(this.type)));
            p1.setName(ast.newSimpleName("original"));
            method.parameters().add(p1);
        }
        ReturnStatement ret = ast.newReturnStatement();
        ret.setExpression(returnExpression);
        body.statements().add(ret);

        return method;
    }

    private MethodInvocation genMethodInvocation(ASTRewrite rewriter, String methodName, boolean addArgument) {
        AST ast = rewriter.getAST();
        MethodInvocation mi = ast.newMethodInvocation();
        mi.setName(ast.newSimpleName(methodName));
        if (addArgument) {
            mi.arguments().add(ASTNode.copySubtree(ast, locationExpr));
        }
        return mi;
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

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("ABS(" + this.getLocation().getId() + "): ");
        sb.append("(" + locationExpr.toString() + ")");
        sb.append(" -> ");
        sb.append("(" + "-" + locationExpr.toString() + " of type " + this.type + ")");
        return sb.toString();
    }
}
