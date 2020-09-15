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
     * int
     * short
     * long
     * float
     * double
     */
    public String type;
    InfixExpression.Operator op;
    public InfixExpression locationExpr;
    private String variationOption;
    static Random rand = new Random(Configuration.seed);

    public static final HashMap<ASTNode, List<TypeOpPair>> typeOpCache = new HashMap<>();

    public AOR(JavaLocation location, EditHole source) {
        super(location, source);
        locationExpr = (InfixExpression) ((ExpHole)this.getHoleCode()).getLocationExp();
        TypeOpPair randomPair = generateRandomTypeOpFromCache(locationExpr);
        this.type = randomPair.type;
        this.op = randomPair.op;
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
        MethodDeclaration mutatedMethod = getMethodDeclaration(locationExpr);
        if ((mutatedMethod.getModifiers() & Modifier.STATIC) != 0) {
            method.modifiers().add(ast.newModifier(Modifier.ModifierKeyword.STATIC_KEYWORD));
        }
        method.setReturnType2(ast.newPrimitiveType(type2Code(this.type)));
        // meta-program generation relies on this to find the right type
        if (addParameters) {
            SingleVariableDeclaration p1 = ast.newSingleVariableDeclaration();
            SingleVariableDeclaration p2 = ast.newSingleVariableDeclaration();
            p1.setType(ast.newPrimitiveType(type2Code(this.type)));
            p1.setName(ast.newSimpleName("left"));
            p2.setType(ast.newPrimitiveType(type2Code(this.type)));
            p2.setName(ast.newSimpleName("right"));
            method.parameters().add(p1);
            method.parameters().add(p2);
        }
        ReturnStatement ret = ast.newReturnStatement();
        ret.setExpression(returnExpression);
        body.statements().add(ret);

        return method;
    }

    private MethodInvocation genConditionMethodCall(AST ast) {
        MethodInvocation mi = ast.newMethodInvocation();
        mi.setName(ast.newSimpleName("cond_" + this.getVariantFolder()));
        return mi;
    }

    private void genConditionMethod(ASTRewrite rewriter, AST ast) {
        MethodDeclaration m = ast.newMethodDeclaration();
        m.setName(ast.newSimpleName("cond_" + this.getVariantFolder()));
        m.setReturnType2(ast.newPrimitiveType(PrimitiveType.BOOLEAN));
        m.modifiers().add(ast.newModifier(Modifier.ModifierKeyword.STATIC_KEYWORD));
        Block body = ast.newBlock();
        m.setBody(body);

        ArrayList<InfixExpression.Operator> all = new ArrayList<>(Arrays.asList(PLUS, MINUS, TIMES, DIVIDE, REMAINDER));
        all.remove(locationExpr.getOperator());
        Expression cond = getNextFieldAccess(ast, opToString(all.get(0)));
        for (int i = 1; i < all.size(); i++) {
            InfixExpression c = ast.newInfixExpression();
            c.setOperator(CONDITIONAL_OR);
            c.setLeftOperand(getNextFieldAccess(ast, opToString(all.get(i))));
            c.setRightOperand(cond);
            cond = c;
        }
        ReturnStatement ret = ast.newReturnStatement();
        ret.setExpression(cond);
        body.statements().add(ret);

        TypeDeclaration classDecl = RewriteFinalizer.getTypeDeclaration(locationExpr);
        ListRewrite lsr = rewriter.getListRewrite(classDecl, TypeDeclaration.BODY_DECLARATIONS_PROPERTY);
        lsr.insertLast(m, null);
    }

    private PrimitiveType.Code type2Code(String t) {
        switch (t) {
            case "int": return PrimitiveType.INT;
            case "short": return PrimitiveType.SHORT;
            case "long": return PrimitiveType.LONG;
            case "float": return PrimitiveType.FLOAT;
            case "double": return PrimitiveType.DOUBLE;
            default: throw new RuntimeException("Unexpected primitive type: " + t);
        }
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

        Expression newExpr = mutate(ast);
        MethodDeclaration md = genMethodDeclaration(rewriter, this.getVariantFolder(), newExpr, false);
        genConditionMethod(rewriter, ast);

        ConditionalExpression ite = ast.newConditionalExpression();
        ite.setExpression(genConditionMethodCall(ast));
        ite.setThenExpression(genMethodInvocation(rewriter, this.getVariantFolder(), false));
        ite.setElseExpression(locationExprCopy);
        ParenthesizedExpression pe = ast.newParenthesizedExpression();
        pe.setExpression(ite);

        applyEditAndUpdateNodeStore(rewriter, pe, nodeStore, locationExpr, locationExprCopy, finalizer);
        finalizer.markVariantMethod(locationExpr, md, true);
    }

    private TypeOpPair generateRandomTypeOpFromCache(InfixExpression locationExpr) {
        List<TypeOpPair> allPairs = genAllPairs(locationExpr);
        if (typeOpCache.containsKey(locationExpr)) {
            List<TypeOpPair> existing = typeOpCache.get(locationExpr);
            allPairs.removeAll(existing);
        }
        TypeOpPair res = new TypeOpPair("int", PLUS);
        if (allPairs.size() > 0) {
            int idx = rand.nextInt(allPairs.size());
            res = allPairs.get(idx);
        }
        if (typeOpCache.containsKey(locationExpr)) {
            typeOpCache.get(locationExpr).add(res);
        }
        else {
            ArrayList<TypeOpPair> l = new ArrayList<>();
            l.add(res);
            typeOpCache.put(locationExpr, l);
        }
        return res;
    }

    private List<TypeOpPair> genAllPairs(InfixExpression locationExpr) {
        List<String> allTypes = Arrays.asList("int", "short", "long", "double", "float");
        List<InfixExpression.Operator> allOps = new ArrayList<>(Arrays.asList(PLUS, MINUS, TIMES, DIVIDE, REMAINDER));
        allOps.remove(locationExpr.getOperator());
        List<TypeOpPair> res = new ArrayList<>();
        for (String t : allTypes) {
            for (InfixExpression.Operator op : allOps) {
                res.add(new TypeOpPair(t, op));
            }
        }
        return res;
    }


    private Expression mutate(AST ast) {
        InfixExpression copy = (InfixExpression) ASTNode.copySubtree(ast, locationExpr);
        Set<InfixExpression.Operator> all = new HashSet<>(Arrays.asList(
                PLUS, MINUS, TIMES, DIVIDE, REMAINDER
        ));
        all.remove(locationExpr.getOperator());

        ParenthesizedExpression pe = ast.newParenthesizedExpression();
        pe.setExpression(copy);
        CastExpression cast = ast.newCastExpression();
        cast.setType(ast.newPrimitiveType(type2Code(this.type)));
        cast.setExpression(pe);
        Expression otherwise = cast;

        for (InfixExpression.Operator op : all) {
            ConditionalExpression cond = ast.newConditionalExpression();
            cond.setExpression(getNextFieldAccess(ast, opToString(op)));
            InfixExpression then = ast.newInfixExpression();

            CastExpression leftCast = ast.newCastExpression();
            ParenthesizedExpression peLeft = ast.newParenthesizedExpression();
            peLeft.setExpression((Expression) ASTNode.copySubtree(ast, locationExpr.getLeftOperand()));
            leftCast.setType(ast.newPrimitiveType(type2Code(this.type)));
            leftCast.setExpression(peLeft);
            then.setLeftOperand(leftCast);

            CastExpression rightCast = ast.newCastExpression();
            ParenthesizedExpression peRight = ast.newParenthesizedExpression();
            peRight.setExpression((Expression) ASTNode.copySubtree(ast, locationExpr.getRightOperand()));
            rightCast.setType(ast.newPrimitiveType(type2Code(this.type)));
            rightCast.setExpression(peRight);
            then.setRightOperand(rightCast);

            for (Object o : locationExpr.extendedOperands()) {
                then.extendedOperands().add(ASTNode.copySubtree(ast, (ASTNode) o));
            }

            then.setOperator(op);
            cond.setThenExpression(then);
            cond.setElseExpression(otherwise);
            ParenthesizedExpression peCondExp = ast.newParenthesizedExpression();
            peCondExp.setExpression(cond);
            otherwise = peCondExp;
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


    @Override
    public String getVariantOption() {
        return variationOption;
    }

    @Override
    public void setVariantOption(String optionName) {
        this.variationOption = optionName;
    }

    @Override
    public String getVariantOptionSuffix() {
        return opToString(this.op);
    }

    @Override
    public void setVariantFolder(String f) {
        super.setVariantFolder(f);
        this.variationOption = getVariantFolder() + "_" + getVariantOptionSuffix();
    }
}

class TypeOpPair {
    String type;
    InfixExpression.Operator op;

    TypeOpPair(String type, InfixExpression.Operator op) {
        this.type = type;
        this.op = op;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof TypeOpPair) {
            TypeOpPair that = (TypeOpPair) obj;
            return this.type.equals(that.type) && this.op == that.op;
        }
        return false;
    }
}