package clegoues.genprog4java.mut.edits.java;

import clegoues.genprog4java.main.Configuration;
import clegoues.genprog4java.mut.EditHole;
import clegoues.genprog4java.mut.RewriteFinalizer;
import clegoues.genprog4java.mut.holes.java.ExpHole;
import clegoues.genprog4java.mut.holes.java.JavaLocation;
import org.eclipse.jdt.core.dom.*;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;

import java.util.*;

import static org.eclipse.jdt.core.dom.InfixExpression.Operator.*;

/**
 * Relational Operator Replacement
 *
 * Switch between <, <=, !=, ==, >, >=
 *
 * Only valid for generating meta-programs that encode all variants
 */
public class ROR extends JavaEditOperation {

    public InfixExpression locationExpr;
    InfixExpression.Operator op;    // used only in plain GenProg
    private String variantOption;

    public static final HashMap<InfixExpression, List<InfixExpression.Operator>> opCache = new HashMap<>();
    final static Random rand = new Random(Configuration.seed);

    public boolean onlyEquals = false;

    public ROR(JavaLocation location, EditHole source) {
        super(location, source);
        this.locationExpr = (InfixExpression) ((ExpHole)this.getHoleCode()).getLocationExp();
        this.op = generateRandomOpFromCache(locationExpr);
    }

    @Override
    public boolean isExpMutation() {
        return true;
    }

    @Override
    public void edit(ASTRewrite rewriter) {
        AST ast = rewriter.getAST();
        InfixExpression replacement = ast.newInfixExpression();
        replacement.setOperator(this.op);
        replacement.setLeftOperand((Expression) ASTNode.copySubtree(ast, locationExpr.getLeftOperand()));
        replacement.setRightOperand((Expression) ASTNode.copySubtree(ast, locationExpr.getRightOperand()));
        rewriter.replace(locationExpr, replacement, null);
    }

    @Override
    public void methodEdit(ASTRewrite rewriter, HashMap<ASTNode, List<ASTNode>> nodeStore, RewriteFinalizer finalizer) {
        AST ast = rewriter.getAST();
        InfixExpression locationExprCopy = (InfixExpression) ASTNode.copySubtree(ast, locationExpr);

        MethodDeclaration vm = ast.newMethodDeclaration();
        Block body = ast.newBlock();
        vm.setBody(body);
        MethodDeclaration mutatedMethod = getMethodDeclaration(locationExpr);
        if ((mutatedMethod.getModifiers() & Modifier.STATIC) != 0) {
            vm.modifiers().add(ast.newModifier(Modifier.ModifierKeyword.STATIC_KEYWORD));
        }
        vm.setName(ast.newSimpleName(this.getVariantFolder()));
        vm.setReturnType2(ast.newPrimitiveType((PrimitiveType.BOOLEAN)));

        Expression mutated = mutate(ast, locationExprCopy);
        ReturnStatement ret = ast.newReturnStatement();
        ret.setExpression(mutated);
        body.statements().add(ret);

        MethodInvocation mi = ast.newMethodInvocation();
//        mi.setExpression(ast.newThisExpression());
        mi.setName(ast.newSimpleName(getVariantFolder()));

        applyEditAndUpdateNodeStore(rewriter, mi, nodeStore, locationExpr, locationExprCopy, finalizer);
        finalizer.markVariantMethod(locationExpr, vm, true);
    }

    private Expression mutate(AST ast, InfixExpression original) {
        Set<InfixExpression.Operator> all;
        if (onlyEquals) {
            all = new HashSet<>(Arrays.asList(EQUALS, NOT_EQUALS));
        } else {
            all = new HashSet<>(Arrays.asList(
                    LESS, LESS_EQUALS, NOT_EQUALS, EQUALS, GREATER, GREATER_EQUALS
            ));
        }
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

    private InfixExpression.Operator generateRandomOpFromCache(InfixExpression locationExpr) {
        List<InfixExpression.Operator> allOps = new ArrayList<>(Arrays.asList(
                LESS, LESS_EQUALS, NOT_EQUALS, EQUALS, GREATER, GREATER_EQUALS
        ));
        if (opCache.containsKey(locationExpr)) {
            List<InfixExpression.Operator> existing = opCache.get(locationExpr);
            allOps.removeAll(existing);
        }
        InfixExpression.Operator res = LESS;
        if (allOps.size() > 0) {
            int idx = rand.nextInt(allOps.size());
            res = allOps.get(idx);
        }
        if (opCache.containsKey(locationExpr)) {
            opCache.get(locationExpr).add(res);
        }
        else {
            ArrayList<InfixExpression.Operator> l = new ArrayList<>();
            l.add(res);
            opCache.put(locationExpr, l);
        }
        return res;
    }

    private String opToString(InfixExpression.Operator op) {
        switch (op.toString()) {
            case "<":
                return "LESS";
            case "<=":
                return "LESS_EQUALS";
            case "!=":
                return "NOT_EQUALS";
            case "==":
                return "EQUALS";
            case ">":
                return "GREATER";
            case ">=":
                return "GREATER_EQUALS";
            default:
                throw new RuntimeException("Unexpected Op in ROR: " + op);
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
        return opToString(this.op);
    }

    @Override
    public void setVariantFolder(String f) {
        super.setVariantFolder(f);
        this.variantOption = getVariantFolder() + "_" + getVariantOptionSuffix();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("ROR(" + this.getLocation().getId() + "): ");
        sb.append("(" + locationExpr.toString() + ")");
        sb.append(" -> ");
        sb.append("(" + locationExpr.getLeftOperand() + this.op + locationExpr.getRightOperand() + ")");
        return sb.toString();
    }
}
