package clegoues.genprog4java.mut;

import org.eclipse.jdt.core.dom.*;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;

import java.util.*;

/**
 * Finalize rewrite of the meta-program. In particular:
 *
 * - Add synchronized keyword to the original mutated method
 * - Write all variant methods to class
 * - Create fields for local variables
 * - Replace all used local variables with fields
 * - Initialize fields in the beginning of method and after each variable declaration
 * - Rewrite return, break, continue in the variant methods
 * - Reset fields for return, break, and continue at the beginning of the first method of a variant method chain
 * - Add checks for return, break, and continue
 */
public class RewriteFinalizer extends ASTVisitor {

    private HashSet<MethodDeclaration> needsReturnCheck = new HashSet<>();
    private HashSet<MethodDeclaration> needsBreakCheck = new HashSet<>();
    private HashSet<MethodDeclaration> needsContinueCheck = new HashSet<>();
    private HashMap<MethodDeclaration, Block> variant2Callsite = new HashMap<>();
    private HashMap<MethodDeclaration, HashSet<MethodDeclaration>> method2Variants = new HashMap<>();

    private ASTRewrite rewriter;
    private AST ast;

    public RewriteFinalizer(ASTRewrite rewriter) {
        this.rewriter = rewriter;
        this.ast = rewriter.getAST();
    }

    public void finalizeEdits() {
        for (HashMap.Entry<MethodDeclaration, HashSet<MethodDeclaration>> entry : method2Variants.entrySet()) {
            MethodDeclaration mutatedMethod = entry.getKey();

            addSynchronized(mutatedMethod);

            writeVariantMethods(mutatedMethod);

            VarNamesCollector collector = new VarNamesCollector(mutatedMethod);
            collector.analyze();

            writeFieldsToClass(mutatedMethod, collector);

            VarToFieldVisitor var2field = new VarToFieldVisitor(collector, rewriter);
            changeVars2Fields(mutatedMethod, collector, var2field);

            FieldInitVisitor fiv = new FieldInitVisitor(collector, rewriter, var2field);
            initFields(fiv, mutatedMethod);

            rewriteBreakContinueReturnInVariantMethods(mutatedMethod, collector, var2field);

            initBreakContinueReturnFields(mutatedMethod, collector);

            addChecksToVariantCallSites(mutatedMethod, collector);
        }
    }

    private void initFields(FieldInitVisitor fiv, MethodDeclaration mutatedMethod) {
        fiv.initParameterFields();
        mutatedMethod.accept(fiv);
        for (MethodDeclaration m : method2Variants.get(mutatedMethod)) {
            m.accept(fiv);
        }
    }

    private void initBreakContinueReturnFields(MethodDeclaration mutatedMethod, VarNamesCollector collector) {
        for (MethodDeclaration m : method2Variants.get(mutatedMethod)) {
            ListRewrite lsr = rewriter.getListRewrite(m.getBody(), Block.STATEMENTS_PROPERTY);
            if (needsBreakCheck.contains(m)) {
                FieldAccess fa = ast.newFieldAccess();
                fa.setExpression(ast.newThisExpression());
                fa.setName(ast.newSimpleName(collector.hasBreakFieldName));
                Assignment assign = ast.newAssignment();
                assign.setLeftHandSide(fa);
                assign.setRightHandSide(ast.newBooleanLiteral(false));
                ExpressionStatement assignStmt = ast.newExpressionStatement(assign);
                lsr.insertFirst(assignStmt, null);
            }
            if (needsContinueCheck.contains(m)) {
                FieldAccess fa = ast.newFieldAccess();
                fa.setExpression(ast.newThisExpression());
                fa.setName(ast.newSimpleName(collector.hasContinueFieldName));
                Assignment assign = ast.newAssignment();
                assign.setLeftHandSide(fa);
                assign.setRightHandSide(ast.newBooleanLiteral(false));
                ExpressionStatement assignStmt = ast.newExpressionStatement(assign);
                lsr.insertFirst(assignStmt, null);
            }
            if (needsReturnCheck.contains(m)) {
                FieldAccess fa = ast.newFieldAccess();
                fa.setExpression(ast.newThisExpression());
                fa.setName(ast.newSimpleName(collector.hasReturnFieldName));
                Assignment assign = ast.newAssignment();
                assign.setLeftHandSide(fa);
                assign.setRightHandSide(ast.newBooleanLiteral(false));
                ExpressionStatement assignStmt = ast.newExpressionStatement(assign);
                lsr.insertFirst(assignStmt, null);
            }
        }
    }

    private void addChecksToVariantCallSites(MethodDeclaration mutatedMethod, VarNamesCollector collector) {
        for (MethodDeclaration m : method2Variants.get(mutatedMethod)) {
            if (needsBreakCheck.contains(m)) {
                Block b = variant2Callsite.get(m);
                IfStatement ifStmt = ast.newIfStatement();
                FieldAccess fa = ast.newFieldAccess();
                fa.setExpression(ast.newThisExpression());
                fa.setName(ast.newSimpleName(collector.hasBreakFieldName));
                ifStmt.setExpression(fa);
                ifStmt.setThenStatement(ast.newBreakStatement());
                b.statements().add(ifStmt);
            }
            if (needsContinueCheck.contains(m)) {
                Block b = variant2Callsite.get(m);
                IfStatement ifStmt = ast.newIfStatement();
                FieldAccess fa = ast.newFieldAccess();
                fa.setExpression(ast.newThisExpression());
                fa.setName(ast.newSimpleName(collector.hasContinueFieldName));
                ifStmt.setExpression(fa);
                ifStmt.setThenStatement(ast.newContinueStatement());
                b.statements().add(ifStmt);
            }
            if (needsReturnCheck.contains(m)) {
                Block b = variant2Callsite.get(m);
                IfStatement ifStmt = ast.newIfStatement();
                FieldAccess fa = ast.newFieldAccess();
                fa.setExpression(ast.newThisExpression());
                fa.setName(ast.newSimpleName(collector.hasReturnFieldName));
                ifStmt.setExpression(fa);
                ReturnStatement retStmt = ast.newReturnStatement();
                if (hasReturnValue(mutatedMethod)) {
                    FieldAccess retValFA = ast.newFieldAccess();
                    retValFA.setExpression(ast.newThisExpression());
                    retValFA.setName(ast.newSimpleName(collector.returnValueFieldName));
                    retStmt.setExpression(retValFA);
                }
                ifStmt.setThenStatement(retStmt);
                b.statements().add(ifStmt);
            }
        }
    }

    private void rewriteBreakContinueReturnInVariantMethods(MethodDeclaration md, VarNamesCollector collector, VarToFieldVisitor var2field) {
        VariantBreakContinueReturnVisitor v = new VariantBreakContinueReturnVisitor(collector, rewriter, var2field);
        for (MethodDeclaration m : method2Variants.get(md)) {
            m.accept(v);
        }
    }

    private void changeVars2Fields(MethodDeclaration md, VarNamesCollector collector, VarToFieldVisitor v) {
        md.accept(v);
        for (MethodDeclaration m : method2Variants.get(md)) {
            m.accept(v);
        }
    }

    private void addSynchronized(MethodDeclaration md) {
        if (!md.isConstructor()) {
            ListRewrite lr = rewriter.getListRewrite(md, MethodDeclaration.MODIFIERS2_PROPERTY);
            lr.insertLast(ast.newModifier(Modifier.ModifierKeyword.SYNCHRONIZED_KEYWORD), null);
        }
    }

    private void writeVariantMethods(MethodDeclaration md) {
        TypeDeclaration classDecl = getTypeDeclaration(md);
        for (MethodDeclaration m : method2Variants.get(md)) {
            ListRewrite lr = rewriter.getListRewrite(classDecl, TypeDeclaration.BODY_DECLARATIONS_PROPERTY);
            lr.insertLast(m, null);
        }
    }

    public void checkSpecialStatements(Statement locationNode, Statement fixCodeNode, HashMap<ASTNode, List<ASTNode>> nodeStore) {
        CheckBreakContinueReturnVisitor locationNodeCheck = new CheckBreakContinueReturnVisitor();
        CheckBreakContinueReturnVisitor fixCodeNodeCheck = new CheckBreakContinueReturnVisitor();
        locationNode.accept(locationNodeCheck);
        if (fixCodeNode != null)
            fixCodeNode.accept(fixCodeNodeCheck);
        List<ASTNode> rewriteHistory = nodeStore.get(locationNode);
        assert(rewriteHistory != null);
        ASTNode first = rewriteHistory.get(0);
        MethodDeclaration md = getMethodDeclaration(first);
        if (locationNodeCheck.hasReturn || fixCodeNodeCheck.hasReturn) {
            needsReturnCheck.add(md);
        }
        else if (locationNodeCheck.hasBreak || fixCodeNodeCheck.hasBreak) {
            needsBreakCheck.add(md);
        }
        else if (locationNodeCheck.hasContinue || fixCodeNodeCheck.hasContinue) {
            needsContinueCheck.add(md);
        }
    }

    public void recordVariantCallsite(MethodDeclaration md, Block callsite) {
        variant2Callsite.put(md, callsite);
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

    private MethodDeclaration getMethodDeclaration(ASTNode n) {
        if (n != null) {
            if (n instanceof MethodDeclaration) {
                return (MethodDeclaration) n;
            }
            else {
                return getMethodDeclaration(n.getParent());
            }
        }
        else {
            throw new RuntimeException("No surrounding method declaration");
        }
    }

    public void markVariantMethod(ASTNode callsite, MethodDeclaration variantMethod) {
        MethodDeclaration callsiteMd = getMethodDeclaration(callsite);
        if (method2Variants.containsKey(callsiteMd)) {
            method2Variants.get(callsiteMd).add(variantMethod);
        }
        else {
            HashSet<MethodDeclaration> variants = new HashSet<>();
            variants.add(variantMethod);
            method2Variants.put(callsiteMd, variants);
        }
    }

    private void writeFieldsToClass(MethodDeclaration md, VarNamesCollector collector) {
        TypeDeclaration classDecl = getTypeDeclaration(md);
        ListRewrite lr = rewriter.getListRewrite(classDecl, TypeDeclaration.BODY_DECLARATIONS_PROPERTY);
        for (MyParameter p : collector.parameters) {
            FieldDeclaration fd = genField(p.getType(), collector.varNames.get(p.getName().getIdentifier()));
            lr.insertLast(fd, null);
        }
        for (MyParameter p : collector.localVariables) {
            FieldDeclaration fd = genField(p.getType(), collector.varNames.get(p.getName().getIdentifier()));
            lr.insertLast(fd, null);
        }
        // additional fields for keeping the states of looping
        FieldDeclaration hasBreak = genField(ast.newPrimitiveType(PrimitiveType.BOOLEAN), collector.hasBreakFieldName);
        lr.insertLast(hasBreak, null);
        FieldDeclaration hasReturn = genField(ast.newPrimitiveType(PrimitiveType.BOOLEAN), collector.hasReturnFieldName);
        lr.insertLast(hasReturn, null);
        FieldDeclaration hasContinue = genField(ast.newPrimitiveType(PrimitiveType.BOOLEAN), collector.hasContinueFieldName);
        lr.insertLast(hasContinue, null);
        if (hasReturnValue(md)) {
            FieldDeclaration returnValue = genField(md.getReturnType2(), collector.returnValueFieldName);
            lr.insertLast(returnValue, null);
        }
    }

    FieldDeclaration genField(Type t, String name) {
        VariableDeclarationFragment vdf = ast.newVariableDeclarationFragment();
        vdf.setName(ast.newSimpleName(name));
        FieldDeclaration fd = ast.newFieldDeclaration(vdf);
        fd.setType((Type) ASTNode.copySubtree(ast, t));
        return fd;
    }

    boolean hasReturnValue(MethodDeclaration md) {
        Type retType = md.getReturnType2();
        if (retType == null) {
            return false;
        }
        else if (retType.isPrimitiveType() && ((PrimitiveType) retType).getPrimitiveTypeCode() == PrimitiveType.VOID) {
            return false;
        }
        else {
            return true;
        }
    }

}


class VarNamesCollector extends ASTVisitor {
    MethodDeclaration md;
    Map<String, String> varNames;
    Set<MyParameter> parameters;
    Set<MyParameter> localVariables;
    Random rand;
    String hasReturnFieldName;
    String hasBreakFieldName;
    String hasContinueFieldName;
    String returnValueFieldName;

    public VarNamesCollector(MethodDeclaration m) {
        this.md = m;
        this.varNames = new HashMap<>();
        this.parameters = new HashSet<>();
        this.localVariables = new HashSet<>();
        this.rand = new Random();
        this.hasReturnFieldName =  "hasReturn" + "_" + genRandomString();
        this.hasBreakFieldName = "hasBreak" + "_" + genRandomString();
        this.hasContinueFieldName = "hasContinue" + "_" + genRandomString();
        this.returnValueFieldName = "retVal" + "_" + genRandomString();
    }

    public void analyze() {
        md.accept(this);
    }

    String genRandomString() {
        return Integer.toString(Math.abs(rand.nextInt()));
    }

    @Override
    public boolean visit(SingleVariableDeclaration node) {
        // formal parameters
        String name = node.getName().getIdentifier();
        if (!varNames.containsKey(name)) {
            AST ast = node.getAST();
            String fieldName = name + "_" + genRandomString();
            varNames.put(name, fieldName);
            MyParameter p = new MyParameter(node.getType(), ast.newSimpleName(name), ast, node.isVarargs());
            parameters.add(p);
        }
        return false;
    }

    @Override
    public boolean visit(VariableDeclarationExpression node) {
        Type t = node.getType();
        for (VariableDeclarationFragment f : (List<VariableDeclarationFragment>) node.fragments()) {
            String varName = f.getName().getIdentifier();
            if (!varNames.containsKey(varName)) {
                AST ast = node.getAST();
                String fieldName = varName + "_" + genRandomString();
                varNames.put(varName, fieldName);
                MyParameter p = new MyParameter(t, ast.newSimpleName(varName), ast, false);
                localVariables.add(p);
            }
        }
        return false;
    }

    @Override
    public boolean visit(VariableDeclarationStatement node) {
        Type t = node.getType();
        for (VariableDeclarationFragment f : (List<VariableDeclarationFragment>) node.fragments()) {
            String varName = f.getName().getIdentifier();
            if (!varNames.containsKey(varName)) {
                AST ast = node.getAST();
                String fieldName = varName + "_" + genRandomString();
                varNames.put(varName, fieldName);
                MyParameter p = new MyParameter(t, ast.newSimpleName(varName), ast, false);
                localVariables.add(p);
            }
        }
        return false;
    }
}

class VarToFieldVisitor extends ASTVisitor {
    VarNamesCollector collector;
    ASTRewrite rewriter;
    AST ast;

    VarToFieldVisitor(VarNamesCollector collector, ASTRewrite rewriter) {
        this.collector = collector;
        this.rewriter = rewriter;
        this.ast = rewriter.getAST();
    }

    @Override
    public boolean visit(SimpleName node) {
        if (isPartOfSuperConstructorInvocation(node))
            return false;
        if (isPartOfFieldAccess(node))
            return false;
        if (isMethodName(node))
            return false;
        if (collector.varNames.containsKey(node.getIdentifier()) && !node.isDeclaration()) {
            SimpleName newName = ast.newSimpleName(collector.varNames.get(node.getIdentifier()));
            rewriter.replace(node, newName, null);
        }
        return false;
    }

    private boolean isMethodName(ASTNode n) {
        StructuralPropertyDescriptor property = n.getLocationInParent();
        return property == MethodInvocation.NAME_PROPERTY;
    }

    private boolean isPartOfFieldAccess(ASTNode n) {
        if (n != null) {
            if (n instanceof FieldAccess)
                return true;
            else
                return isPartOfFieldAccess(n.getParent());
        }
        else {
            return false;
        }
    }

    private boolean isPartOfSuperConstructorInvocation(ASTNode n) {
        if (n != null) {
            if (n instanceof SuperConstructorInvocation)
                return true;
            else
                return isPartOfSuperConstructorInvocation(n.getParent());
        }
        else {
            return false;
        }
    }
}

class FieldInitVisitor extends ASTVisitor {
    VarNamesCollector collector;
    ASTRewrite rewriter;
    AST ast;
    VarToFieldVisitor var2field;

    FieldInitVisitor(VarNamesCollector collector, ASTRewrite rewriter, VarToFieldVisitor var2field) {
        this.collector = collector;
        this.rewriter = rewriter;
        this.ast = rewriter.getAST();
        this.var2field = var2field;
    }

    public void initParameterFields() {
        ListRewrite lsr = rewriter.getListRewrite(collector.md.getBody(), Block.STATEMENTS_PROPERTY);
        for (MyParameter p : collector.parameters) {
            Assignment assign = ast.newAssignment();
            FieldAccess fa = ast.newFieldAccess();
            fa.setExpression(ast.newThisExpression());
            fa.setName(ast.newSimpleName(collector.varNames.get(p.getName().getIdentifier())));
            assign.setLeftHandSide(fa);
            assign.setRightHandSide(p.getName());
            ExpressionStatement assignStmt = ast.newExpressionStatement(assign);
            if (collector.md.isConstructor() && collector.md.getBody().statements().get(0) instanceof SuperConstructorInvocation) {
                lsr.insertAt(assignStmt, 1, null);
            }
            else {
                lsr.insertFirst(assignStmt, null);
            }
        }
    }

    @Override
    public boolean visit(VariableDeclarationStatement node) {
        List<Statement> assignments = new LinkedList<>();
        for (VariableDeclarationFragment f : (List<VariableDeclarationFragment>) node.fragments()) {
            Expression initializer = (Expression) ASTNode.copySubtree(ast, f.getInitializer());
            if (initializer != null && collector.varNames.containsKey(f.getName().getIdentifier())) {
                Assignment assign = ast.newAssignment();
                FieldAccess fa = ast.newFieldAccess();
                fa.setExpression(ast.newThisExpression());
                fa.setName(ast.newSimpleName(collector.varNames.get(f.getName().getIdentifier())));
                assign.setLeftHandSide(fa);
                assign.setRightHandSide(initializer);
                ExpressionStatement assignStmt = ast.newExpressionStatement(assign);
                assignStmt.accept(var2field);
                assignments.add(assignStmt);
            }
        }
        if (assignments.size() > 1) {
            Block block = ast.newBlock();
            block.statements().addAll(assignments);
            rewriter.replace(node, block, null);
        }
        else if (assignments.size() == 1){
            rewriter.replace(node, assignments.get(0), null);
        }
        return false;
    }

    @Override
    public boolean visit(VariableDeclarationExpression node) {
        if (node.getParent() instanceof ForStatement) {
            List<Assignment> assignments = new LinkedList<>();
            for (VariableDeclarationFragment f : (List<VariableDeclarationFragment>) node.fragments()) {
                Expression initializer = (Expression) ASTNode.copySubtree(ast, f.getInitializer());
                if (initializer != null && collector.varNames.containsKey(f.getName().getIdentifier())) {
                    Assignment assign = ast.newAssignment();
                    FieldAccess fa = ast.newFieldAccess();
                    fa.setExpression(ast.newThisExpression());
                    fa.setName(ast.newSimpleName(collector.varNames.get(f.getName().getIdentifier())));
                    assign.setLeftHandSide(fa);
                    assign.setRightHandSide(initializer);
                    assign.accept(var2field);
                    assignments.add(assign);
                }
            }
            ListRewrite lsr = rewriter.getListRewrite(node.getParent(), ForStatement.INITIALIZERS_PROPERTY);
            lsr.remove(node, null);
            for (Assignment assign : assignments) {
                lsr.insertLast(assign, null);
            }
        }
        else {
            throw new RuntimeException("Unexpected VariableDeclarationExpression in " + node.getParent().getClass());
        }
        return false;
    }
}

class VariantBreakContinueReturnVisitor extends ASTVisitor {
    VarNamesCollector collector;
    ASTRewrite rewriter;
    VarToFieldVisitor var2field;
    AST ast;

    public VariantBreakContinueReturnVisitor(VarNamesCollector collector, ASTRewrite rewriter, VarToFieldVisitor var2field) {
        this.collector = collector;
        this.rewriter = rewriter;
        this.var2field = var2field;
        ast = rewriter.getAST();
    }

    @Override
    public boolean visit(BreakStatement node) {
        if (!isPartOfSwitchStatement(node)) {
            Block b = ast.newBlock();
            Assignment assign = ast.newAssignment();
            FieldAccess fa = ast.newFieldAccess();
            fa.setExpression(ast.newThisExpression());
            fa.setName(ast.newSimpleName(collector.hasBreakFieldName));
            assign.setLeftHandSide(fa);
            assign.setRightHandSide(ast.newBooleanLiteral(true));
            ExpressionStatement assignStmt = ast.newExpressionStatement(assign);
            b.statements().add(assignStmt);
            b.statements().add(ast.newReturnStatement());

            rewriter.replace(node, b, null);
        }
        return false;
    }

    @Override
    public boolean visit(ContinueStatement node) {
        Block b = ast.newBlock();
        Assignment assign = ast.newAssignment();
        FieldAccess fa = ast.newFieldAccess();
        fa.setExpression(ast.newThisExpression());
        fa.setName(ast.newSimpleName(collector.hasContinueFieldName));
        assign.setLeftHandSide(fa);
        assign.setRightHandSide(ast.newBooleanLiteral(true));
        ExpressionStatement assignStmt = ast.newExpressionStatement(assign);
        b.statements().add(assignStmt);
        b.statements().add(ast.newReturnStatement());

        rewriter.replace(node, b, null);
        return false;
    }

    @Override
    public boolean visit(ReturnStatement node) {
        Block b = ast.newBlock();
        Assignment assign = ast.newAssignment();
        FieldAccess fa = ast.newFieldAccess();
        fa.setExpression(ast.newThisExpression());
        fa.setName(ast.newSimpleName(collector.hasReturnFieldName));
        assign.setLeftHandSide(fa);
        assign.setRightHandSide(ast.newBooleanLiteral(true));
        ExpressionStatement assignStmt = ast.newExpressionStatement(assign);
        b.statements().add(assignStmt);
        if (node.getExpression() != null) {
            Assignment retValAssign = ast.newAssignment();
            FieldAccess retValFA = ast.newFieldAccess();
            retValFA.setExpression(ast.newThisExpression());
            retValFA.setName(ast.newSimpleName(collector.returnValueFieldName));
            retValAssign.setLeftHandSide(retValFA);
            Expression retVal = (Expression) ASTNode.copySubtree(ast, node.getExpression());
            retValAssign.setRightHandSide(retVal);
            ExpressionStatement assignStmt2 = ast.newExpressionStatement(retValAssign);
            assignStmt2.accept(var2field);
            b.statements().add(assignStmt2);
        }
        b.statements().add(ast.newReturnStatement());

        rewriter.replace(node, b, null);
        return false;
    }

    private boolean isPartOfSwitchStatement(ASTNode n) {
        if (n != null) {
            if (n instanceof SwitchStatement)
                return true;
            else
                return isPartOfSwitchStatement(n.getParent());
        }
        else {
            return false;
        }
    }
}

class CheckBreakContinueReturnVisitor extends ASTVisitor {
    boolean hasReturn = false;
    boolean hasBreak = false;
    boolean hasContinue = false;

    @Override
    public boolean visit(BreakStatement node) {
        if (!isPartOfSwitchStatement(node))
            hasBreak = true;
        return true;
    }

    @Override
    public boolean visit(ContinueStatement node) {
        hasContinue = true;
        return true;
    }

    @Override
    public boolean visit(ReturnStatement node) {
        hasReturn = true;
        return true;
    }

    private boolean isPartOfSwitchStatement(ASTNode n) {
        if (n != null) {
            if (n instanceof SwitchStatement)
                return true;
            else
                return isPartOfSwitchStatement(n.getParent());
        }
        else {
            return false;
        }
    }
}
