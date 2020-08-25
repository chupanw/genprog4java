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


public class UOI extends JavaEditOperation {

    private static final HashMap<Expression, List<Pair>> typeOpCache = new HashMap<>();
    static Random rand = new Random(Configuration.seed);

    String type;
    String op;
    public Expression locationExpr;
    private String variantOption;

    public UOI(JavaLocation location, EditHole source) {
        super(location, source);
        locationExpr = ((ExpHole) this.getHoleCode()).getLocationExp();
        Pair randomTypeOp = generateRandomTypeOpFromCache(locationExpr);
        this.type = randomTypeOp.type;
        this.op = randomTypeOp.op;
    }

    @Override
    public boolean isExpMutation() {
        return true;
    }

    @Override
    public void edit(ASTRewrite rewriter) {
        AST ast = rewriter.getAST();

        Expression mutated;
        switch (this.op) {
            case "PRE_INC":
                PrefixExpression prefix1 = ast.newPrefixExpression();
                prefix1.setOperator(PrefixExpression.Operator.INCREMENT);
                prefix1.setOperand(ast.newSimpleName("original"));
                mutated = prefix1;
                break;
            case "PRE_DEC":
                PrefixExpression prefix2 = ast.newPrefixExpression();
                prefix2.setOperator(PrefixExpression.Operator.DECREMENT);
                prefix2.setOperand(ast.newSimpleName("original"));
                mutated = prefix2;
                break;
            case "POST_INC":
                PostfixExpression postfix1 = ast.newPostfixExpression();
                postfix1.setOperator(PostfixExpression.Operator.INCREMENT);
                postfix1.setOperand(ast.newSimpleName("original"));
                mutated = postfix1;
                break;
            default:
                PostfixExpression postfix2 = ast.newPostfixExpression();
                postfix2.setOperator(PostfixExpression.Operator.DECREMENT);
                postfix2.setOperand(ast.newSimpleName("original"));
                mutated = postfix2;
        }
        MethodDeclaration method = genMethodDeclaration(rewriter, "UOI", mutated, true);
        MethodInvocation mi = genMethodInvocation(rewriter, "UOI", true);
        rewriter.replace(locationExpr, mi, null);
        TypeDeclaration typeDecl = getTypeDeclaration(locationExpr);
        ListRewrite lsr = rewriter.getListRewrite(typeDecl, TypeDeclaration.BODY_DECLARATIONS_PROPERTY);
        lsr.insertLast(method, null);
    }

    private Pair generateRandomTypeOpFromCache(Expression locationExpr) {
        List<Pair> allPairs = genAllPairs();
        if (typeOpCache.containsKey(locationExpr)) {
            List<Pair> existing = typeOpCache.get(locationExpr);
            allPairs.removeAll(existing);
        }
        Pair res = new Pair("java.lang.Integer", "PRE_INC");
        if (allPairs.size() > 0) {
            int idx = rand.nextInt(allPairs.size());
            res = allPairs.get(idx);
        }
        if (typeOpCache.containsKey(locationExpr)) {
            typeOpCache.get(locationExpr).add(res);
        }
        else {
            ArrayList<Pair> l = new ArrayList<>();
            l.add(res);
            typeOpCache.put(locationExpr, l);
        }
        return res;
    }

    private List<Pair> genAllPairs() {
        List<String> allTypes = Arrays.asList(
                "java.lang.Integer",
                "java.lang.Character",
                "java.lang.Short",
                "java.lang.Long",
                "java.lang.Double",
                "java.lang.Float"
        );
        List<String> allOps = Arrays.asList("PRE_INC", "PRE_DEC", "POST_INC", "POST_DEC");
        List<Pair> res = new ArrayList<>();
        for (String t : allTypes) {
            for (String op : allOps) {
                res.add(new Pair(t, op));
            }
        }
        return res;
    }

    @Override
    public void methodEdit(ASTRewrite rewriter, HashMap<ASTNode, List<ASTNode>> nodeStore, RewriteFinalizer finalizer) {
        AST ast = rewriter.getAST();
        Expression locationExprCopy = (Expression) ASTNode.copySubtree(ast, locationExpr);

        Expression mutated = mutate(ast, locationExprCopy);
        MethodDeclaration md = genMethodDeclaration(rewriter, this.getVariantFolder(), mutated, false);
        MethodInvocation mi = genMethodInvocation(rewriter, this.getVariantFolder(), false);

        applyEditAndUpdateNodeStore(rewriter, mi, nodeStore, locationExpr, locationExprCopy);
        finalizer.markVariantMethod(locationExpr, md, true);
    }

    private MethodDeclaration genMethodDeclaration(ASTRewrite rewriter, String methodName, Expression returnExpression, boolean addParameters) {
        AST ast = rewriter.getAST();
        MethodDeclaration method = ast.newMethodDeclaration();
        Block body = ast.newBlock();
        method.setBody(body);
        method.setName(ast.newSimpleName(methodName));
        method.setReturnType2(ast.newSimpleType(ast.newName(this.type)));
        if (addParameters) {
            // GenProg mode, use parameter to find the correct type
            SingleVariableDeclaration p1 = ast.newSingleVariableDeclaration();
            p1.setType(ast.newSimpleType(ast.newName(this.type)));
            p1.setName(ast.newSimpleName("original"));
            method.parameters().add(p1);
            // add the mutated expression to catch corner cases like (-a)++
            PostfixExpression dummy = ast.newPostfixExpression();
            dummy.setOperator(PostfixExpression.Operator.INCREMENT);
            dummy.setOperand(((Expression) ASTNode.copySubtree(ast, locationExpr)));
            body.statements().add(ast.newExpressionStatement(dummy));
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
            mi.arguments().add(ASTNode.copySubtree(ast, locationExpr));
        }
        return mi;
    }

    private Expression mutate(AST ast, Expression original) {
        Expression otherwise = original;
        String[] optionNames = new String[]{
                "PRE_INC", "PRE_DEC", "POST_INC", "POST_DEC"
        };
        for (int i = 0; i < 4; i++) {
            ConditionalExpression cond = ast.newConditionalExpression();
            cond.setExpression(getNextFieldAccess(ast, optionNames[i]));
            switch (i) {
                case 0:
                    PrefixExpression prefix = ast.newPrefixExpression();
                    prefix.setOperator(PrefixExpression.Operator.INCREMENT);
                    ParenthesizedExpression pe1 = ast.newParenthesizedExpression();
                    pe1.setExpression((Expression) ASTNode.copySubtree(ast, original));
                    prefix.setOperand(pe1);
                    cond.setThenExpression(prefix);
                    break;
                case 1:
                    PrefixExpression prefix2 = ast.newPrefixExpression();
                    prefix2.setOperator(PrefixExpression.Operator.DECREMENT);
                    ParenthesizedExpression pe2 = ast.newParenthesizedExpression();
                    pe2.setExpression((Expression) ASTNode.copySubtree(ast, original));
                    prefix2.setOperand(pe2);
                    cond.setThenExpression(prefix2);
                    break;
                case 2:
                    PostfixExpression postfix = ast.newPostfixExpression();
                    postfix.setOperator(PostfixExpression.Operator.INCREMENT);
                    ParenthesizedExpression pe3 = ast.newParenthesizedExpression();
                    pe3.setExpression((Expression) ASTNode.copySubtree(ast, original));
                    postfix.setOperand(pe3);
                    cond.setThenExpression(postfix);
                    break;
                default:
                    PostfixExpression postfix2 = ast.newPostfixExpression();
                    postfix2.setOperator(PostfixExpression.Operator.DECREMENT);
                    ParenthesizedExpression pe4 = ast.newParenthesizedExpression();
                    pe4.setExpression((Expression) ASTNode.copySubtree(ast, original));
                    postfix2.setOperand(pe4);
                    cond.setThenExpression(postfix2);
                    break;
            }
            cond.setElseExpression(otherwise);
            ParenthesizedExpression pe = ast.newParenthesizedExpression();
            pe.setExpression(cond);
            otherwise = pe;
        }
        return otherwise;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("UOI(" + this.getLocation().getId() + "): ");
        sb.append("(" + locationExpr.toString() + ")");
        sb.append(" -> ");
        sb.append("(" + this.op + " " + locationExpr.toString() + ")");
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
        return this.variantOption;
    }

    @Override
    public void setVariantOption(String optionName) {
        this.variantOption = optionName;
    }

    @Override
    public String getVariantOptionSuffix() {
        return this.op;
    }

    @Override
    public void setVariantFolder(String f) {
        super.setVariantFolder(f);
        this.variantOption = getVariantFolder() + "_" + getVariantOptionSuffix();
    }
}

class Pair {
    String type;
    String op;

    Pair(String type, String op) {
        this.type = type;
        this.op = op;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Pair) {
            Pair that = (Pair) obj;
            return this.type.equals(that.type) && this.op.equals(that.op);
        }
        return false;
    }
}
