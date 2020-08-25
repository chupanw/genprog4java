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
    private HashSet<MethodDeclaration> needsReturnCheckAndFirst = new HashSet<>();
    private HashSet<MethodDeclaration> needsBreakCheck = new HashSet<>();
    private HashSet<MethodDeclaration> needsContinueCheck = new HashSet<>();
    private HashMap<MethodDeclaration, VariantCallsite> variant2Callsite = new HashMap<>();
    private HashMap<MethodDeclaration, HashSet<MethodDeclaration>> method2Variants = new HashMap<>();
    private HashMap<ASTNode, MethodDeclaration> firstModifiedInVariant = new HashMap<>();
    /**
     * Applicable to all expression level operators
     */
    private HashSet<MethodDeclaration> variantsWithoutRewritingReturn = new HashSet<>();

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
            collectVariableNames(collector, mutatedMethod);

            writeFieldsToClass(mutatedMethod, collector);

            VarToFieldVisitor var2field = new VarToFieldVisitor(collector, rewriter);
            changeVars2Fields(mutatedMethod, collector, var2field);

            FieldInitVisitor fiv = new FieldInitVisitor(collector, rewriter, var2field);
            initFields(fiv, mutatedMethod);

            storeAndRestoreStates(collector, mutatedMethod);

            rewriteBreakContinueReturnInVariantMethods(mutatedMethod, collector, var2field);

            initBreakContinueReturnFields(mutatedMethod, collector);

            addChecksToVariantCallSites(mutatedMethod, collector);
        }
    }

    private void storeAndRestoreStates(VarNamesCollector collector, MethodDeclaration mutatedMethod) {
        StoreStateBeforeMethodCallVisitor stateRestoreVisitor = new StoreStateBeforeMethodCallVisitor(collector, rewriter, mutatedMethod);
        stateRestoreVisitor.writeStoreAndRestoreToClass();

        mutatedMethod.accept(stateRestoreVisitor);
        for (MethodDeclaration m : method2Variants.get(mutatedMethod)) {
            m.accept(stateRestoreVisitor);
        }
    }

    private void collectVariableNames(VarNamesCollector collector, MethodDeclaration mutatedMethod) {
        mutatedMethod.accept(collector);
        for (MethodDeclaration m : method2Variants.get(mutatedMethod)) {
            m.accept(collector);
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
            if (needsBreakCheck.contains(m) && variant2Callsite.get(m).isInsideLoop) {
                Block b = variant2Callsite.get(m).block;
                IfStatement ifStmt = ast.newIfStatement();
                FieldAccess fa = ast.newFieldAccess();
                fa.setExpression(ast.newThisExpression());
                fa.setName(ast.newSimpleName(collector.hasBreakFieldName));
                ifStmt.setExpression(fa);

                Block thenBlock = ast.newBlock();
                // reset the break field after use
                Assignment reset = ast.newAssignment();
                reset.setLeftHandSide((Expression) ASTNode.copySubtree(ast, fa));
                reset.setRightHandSide(ast.newBooleanLiteral(false));
                ExpressionStatement resetStmt = ast.newExpressionStatement(reset);
                thenBlock.statements().add(resetStmt);
                thenBlock.statements().add(ast.newBreakStatement());
                ifStmt.setThenStatement(thenBlock);

                b.statements().add(ifStmt);
            }
            if (needsContinueCheck.contains(m) && variant2Callsite.get(m).isInsideLoop) {
                Block b = variant2Callsite.get(m).block;
                IfStatement ifStmt = ast.newIfStatement();
                FieldAccess fa = ast.newFieldAccess();
                fa.setExpression(ast.newThisExpression());
                fa.setName(ast.newSimpleName(collector.hasContinueFieldName));
                ifStmt.setExpression(fa);

                Block thenBlock = ast.newBlock();
                // reset the break field after use
                Assignment reset = ast.newAssignment();
                reset.setLeftHandSide((Expression) ASTNode.copySubtree(ast, fa));
                reset.setRightHandSide(ast.newBooleanLiteral(false));
                ExpressionStatement resetStmt = ast.newExpressionStatement(reset);
                thenBlock.statements().add(resetStmt);
                thenBlock.statements().add(ast.newContinueStatement());
                ifStmt.setThenStatement(thenBlock);

                b.statements().add(ifStmt);
            }
            if (needsReturnCheck.contains(m)) {
                Block b = variant2Callsite.get(m).block;
                IfStatement ifStmt = ast.newIfStatement();
                FieldAccess fa = ast.newFieldAccess();
                fa.setExpression(ast.newThisExpression());
                fa.setName(ast.newSimpleName(collector.hasReturnFieldName));
                ifStmt.setExpression(fa);
                ReturnStatement retStmt = ast.newReturnStatement();
                if (needsReturnCheckAndFirst.contains(m) && hasReturnValue(mutatedMethod)) {
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
            if (!variantsWithoutRewritingReturn.contains(m)) {
                m.accept(v);
            }
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

    public void checkSpecialStatements(Statement locationNode, Statement fixCodeNode, MethodDeclaration variantMethod, HashMap<ASTNode, List<ASTNode>> nodeStore) {
        if (!firstModifiedInVariant.containsKey(locationNode)) {
            firstModifiedInVariant.put(locationNode, variantMethod);
        }
        CheckBreakContinueReturnVisitor locationNodeCheck = new CheckBreakContinueReturnVisitor(locationNode);
        CheckBreakContinueReturnVisitor fixCodeNodeCheck = new CheckBreakContinueReturnVisitor(fixCodeNode);
        locationNode.accept(locationNodeCheck);
        if (fixCodeNode != null)
            fixCodeNode.accept(fixCodeNodeCheck);
        List<ASTNode> rewriteHistory = nodeStore.get(locationNode);
        assert(rewriteHistory != null);
        List<MethodDeclaration> chain = new LinkedList<>();
        for (ASTNode n : rewriteHistory) {
            chain.add(getMethodDeclaration(n));
        }
        if (locationNodeCheck.hasReturn || fixCodeNodeCheck.hasReturn) {
            needsReturnCheck.addAll(chain);
            // todo: this is likely wrong, causing the old error of Closure-1b, but shouldn't matter for IntroClass
            //  we might need to do something similar to the break check below
            needsReturnCheckAndFirst.add(chain.get(0));
        }
        else if (locationNodeCheck.hasBreak || fixCodeNodeCheck.hasBreak) {
            MethodDeclaration closestBreakPoint = findCloeseCheckPoint(locationNode, true);
            needsBreakCheck.add(closestBreakPoint);
        }
        else if (locationNodeCheck.hasContinue || fixCodeNodeCheck.hasContinue) {
            MethodDeclaration closestContinuePoint = findCloeseCheckPoint(locationNode, false);
            needsContinueCheck.add(closestContinuePoint);
        }
    }

    /**
     * Go up the hierarchy to find the right point to insert break check
     *
     * for (...) {
     *     if (...) {
     *         break;
     *     }
     * }
     *
     * If the if statement is moved into variant1 and the break is moved into variant2,
     * needsBreakCheck should store variant1 instead of variant2, although variant2 is where the break statement is first modified
     *
     * @param locationNode the break in the above example, or any statement can be validly replaced by a break;
     */
    private MethodDeclaration findCloeseCheckPoint(ASTNode locationNode, boolean shouldConsiderSwitchStmt) {
        MethodDeclaration res = firstModifiedInVariant.get(locationNode);
        ASTNode current = locationNode.getParent();
        while (current != null && !isLoop(current)) {
            if (shouldConsiderSwitchStmt && isSwitch(current)) {
                break;
            }
            if (firstModifiedInVariant.containsKey(current))
                res = firstModifiedInVariant.get(current);
            current = current.getParent();
        }
        return res;
    }

    public static boolean isLoop(ASTNode n) {
        return n instanceof WhileStatement || n instanceof ForStatement || n instanceof EnhancedForStatement || n instanceof DoStatement;
    }

    public static boolean isSwitch(ASTNode n) {
        return n instanceof SwitchStatement;
    }

    /**
     * Record the variant method call site (which is a block) so that we can insert break/continue/return check later
     * @param locationNode  the appended/deleted/replaced node, not modified here
     * @param variantMethod variant method to be called
     * @param callsite  a block with a single method call to this variant method
     */
    public void recordVariantCallsite(ASTNode locationNode, MethodDeclaration variantMethod, Block callsite) {
        boolean isInLoop = isInsideLoop(locationNode.getParent());
        variant2Callsite.put(variantMethod, new VariantCallsite(callsite, isInLoop));
    }

    public static TypeDeclaration getTypeDeclaration(ASTNode n) {
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

    public static Statement getStatement(ASTNode n) {
        if (n != null) {
            if (n instanceof Statement) {
                return (Statement) n;
            }
            else {
                return getStatement(n.getParent());
            }
        }
        else {
            throw new RuntimeException("No surrounding statement");
        }
    }

    public static Block getBlock(ASTNode n) {
        if (n != null) {
            if (n instanceof Block) {
                return (Block) n;
            }
            else {
                return getBlock(n.getParent());
            }
        }
        else {
            throw new RuntimeException("No surrounding block");
        }
    }

    public static boolean isInsideLoop(ASTNode n) {
        if (n == null) {
            return false;
        }
        else {
            if (n instanceof WhileStatement || n instanceof ForStatement || n instanceof EnhancedForStatement || n instanceof DoStatement)
                return true;
            else
                return isInsideLoop(n.getParent());
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

    public void markVariantMethod(ASTNode callsite, MethodDeclaration variantMethod, boolean skipRewriteReturn) {
        MethodDeclaration callsiteMd = getMethodDeclaration(callsite);
        if (method2Variants.containsKey(callsiteMd)) {
            method2Variants.get(callsiteMd).add(variantMethod);
        }
        else {
            HashSet<MethodDeclaration> variants = new HashSet<>();
            variants.add(variantMethod);
            method2Variants.put(callsiteMd, variants);
        }
        if (skipRewriteReturn) {
            variantsWithoutRewritingReturn.add(variantMethod);
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
        fd.modifiers().add(ast.newModifier(Modifier.ModifierKeyword.STATIC_KEYWORD));
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
    Map<String, String> varNames;   // union of parameters and localVariables
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
            if (node.getLocationInParent() == MethodDeclaration.PARAMETERS_PROPERTY) {
                parameters.add(p);
            } else {
                localVariables.add(p);
            }
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
        if (isQualified(node))
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

    private boolean isQualified(ASTNode n) {
        ASTNode parent = n.getParent();
        if (parent instanceof QualifiedName && ((QualifiedName) parent).getName() == n) {
            return true;
        }
        return false;
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
    public boolean visit(SingleVariableDeclaration node) {
        String name = node.getName().getIdentifier();
        if (collector.varNames.containsKey(name)) {
            if (node.getLocationInParent() == EnhancedForStatement.PARAMETER_PROPERTY) {
                EnhancedForStatement parent = (EnhancedForStatement) node.getParent();
                Assignment assign = ast.newAssignment();
                FieldAccess fa = ast.newFieldAccess();
                fa.setExpression(ast.newThisExpression());
                fa.setName(ast.newSimpleName(collector.varNames.get(name)));
                assign.setLeftHandSide(fa);
                assign.setRightHandSide(ast.newSimpleName(name));
                ExpressionStatement assignStmt = ast.newExpressionStatement(assign);
                ListRewrite lsr = rewriter.getListRewrite(parent.getBody(), Block.STATEMENTS_PROPERTY);
                lsr.insertFirst(assignStmt, null);
            }
            else if (node.getLocationInParent() == MethodDeclaration.PARAMETERS_PROPERTY) {
                return false;
            }
            else if (node.getLocationInParent() == CatchClause.EXCEPTION_PROPERTY) {
                return false;
            }
            else {
                throw new RuntimeException("Unexpected SingleVariableDeclaration in " + node.getParent().getClass());
            }
        }
        return false;
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

    ASTNode root;

    CheckBreakContinueReturnVisitor(ASTNode root) {
        this.root = root;
    }

    @Override
    public boolean visit(BreakStatement node) {
        if (canEscapeRoot(node)) {
            hasBreak = true;
        }
        return true;
    }

    @Override
    public boolean visit(ContinueStatement node) {
        if (canEscapeRoot(node)) {
            hasContinue = true;
        }
        return true;
    }

    @Override
    public boolean visit(ReturnStatement node) {
        hasReturn = true;
        return true;
    }

    private boolean canEscapeRoot(BreakStatement b) {
        ASTNode current = b;
        while (current != root) {
            if (RewriteFinalizer.isLoop(current) || RewriteFinalizer.isSwitch(current)) {
                return false;
            }
            current = current.getParent();
        }
        if (RewriteFinalizer.isLoop(current) || RewriteFinalizer.isSwitch(current)) {
            return false;
        }
        else {
            return true;
        }
    }

    private boolean canEscapeRoot(ContinueStatement c) {
        ASTNode current = c;
        while (current != root) {
            if (RewriteFinalizer.isLoop(current)) {
                return false;
            }
            current = current.getParent();
        }
        if (RewriteFinalizer.isLoop(current)) {
            return false;
        }
        else {
            return true;
        }
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

/**
 * One instance per mutated method, similar to VarNameCollector
 */
class StoreStateBeforeMethodCallVisitor extends ASTVisitor {
    VarNamesCollector collector;
    String id;  // we need different IDs for different mutated methods
    ASTRewrite rewriter;
    TypeDeclaration mutatedClass;

    HashSet<String> localMethodNames;
    List<MyParameter> sortedVariables;
    AST ast;
    HashSet<ASTNode> cache;

    StoreStateBeforeMethodCallVisitor(VarNamesCollector collector, ASTRewrite rewriter, MethodDeclaration mutatedMethod) {
        this.collector = collector;
        this.rewriter = rewriter;
        this.mutatedClass = RewriteFinalizer.getTypeDeclaration(mutatedMethod);

        cache = new HashSet<>();
        this.ast = rewriter.getAST();
        this.id = UUID.randomUUID().toString().replace('-', '_');
        sortedVariables = new LinkedList<>();
        sortedVariables.addAll(collector.localVariables);
        sortedVariables.addAll(collector.parameters);
        sortedVariables.sort((a, b) -> a.getName().getIdentifier().compareTo(b.getName().getIdentifier()));
        localMethodNames = new HashSet<>();
        for (MethodDeclaration m : mutatedClass.getMethods()) {
            localMethodNames.add(m.getName().getIdentifier());
        }
    }

    public void writeStoreAndRestoreToClass() {
        ListRewrite lsr = rewriter.getListRewrite(mutatedClass, TypeDeclaration.BODY_DECLARATIONS_PROPERTY);

        // stack field
        ClassInstanceCreation cic = ast.newClassInstanceCreation();
        cic.setType(ast.newSimpleType(ast.newName("java.util.Stack")));
        VariableDeclarationFragment vdf = ast.newVariableDeclarationFragment();
        vdf.setName(ast.newSimpleName("stack_" + id));
        vdf.setInitializer(cic);
        FieldDeclaration stackField = ast.newFieldDeclaration(vdf);
        stackField.setType(ast.newSimpleType(ast.newName("java.util.Stack")));
        lsr.insertLast(stackField, null);

        // store method
        MethodDeclaration storeMethod = ast.newMethodDeclaration();
        storeMethod.setName(ast.newSimpleName("store_" + id));
        storeMethod.setReturnType2(ast.newPrimitiveType(PrimitiveType.VOID));
        Block storeBody = ast.newBlock();
        storeMethod.setBody(storeBody);
        for (int i = 0; i < sortedVariables.size(); i++) {
            MethodInvocation mi = ast.newMethodInvocation();
            mi.setExpression(ast.newSimpleName("stack_" + id));
            mi.setName(ast.newSimpleName("push"));
            String f = collector.varNames.get(sortedVariables.get(i).getName().getIdentifier());
            mi.arguments().add(ast.newSimpleName(f));
            storeBody.statements().add(ast.newExpressionStatement(mi));
        }
        lsr.insertLast(storeMethod, null);

        // restore method
        MethodDeclaration restoreMethod = ast.newMethodDeclaration();
        restoreMethod.setName(ast.newSimpleName("restore_" + id));
        restoreMethod.setReturnType2(ast.newPrimitiveType(PrimitiveType.VOID));
        Block restoreBody = ast.newBlock();
        restoreMethod.setBody(restoreBody);
        for (int i = sortedVariables.size() - 1; i >= 0; i--) {
            Assignment assign = ast.newAssignment();
            String f = collector.varNames.get(sortedVariables.get(i).getName().getIdentifier());
            assign.setLeftHandSide(ast.newSimpleName(f));
            MethodInvocation mi = ast.newMethodInvocation();
            mi.setExpression(ast.newSimpleName("stack_" + id));
            mi.setName(ast.newSimpleName("pop"));
            CastExpression cast = ast.newCastExpression();
            cast.setType(convertPrimitive(sortedVariables.get(i).getType(), ast));
            cast.setExpression(mi);
            assign.setRightHandSide(cast);
            restoreBody.statements().add(ast.newExpressionStatement(assign));
        }
        lsr.insertLast(restoreMethod, null);
    }

    public Type convertPrimitive(Type t, AST ast) {
        if (t.isPrimitiveType()) {
            PrimitiveType pt = (PrimitiveType) t;
            if (pt.getPrimitiveTypeCode() == PrimitiveType.INT) {
                return ast.newSimpleType(ast.newName("java.lang.Integer"));
            }
            else if (pt.getPrimitiveTypeCode() == PrimitiveType.BOOLEAN) {
                return ast.newSimpleType(ast.newName("java.lang.Boolean"));
            }
            else if (pt.getPrimitiveTypeCode() == PrimitiveType.SHORT) {
                return ast.newSimpleType(ast.newName("java.lang.Short"));
            }
            else if (pt.getPrimitiveTypeCode() == PrimitiveType.LONG) {
                return ast.newSimpleType(ast.newName("java.lang.Long"));
            }
            else if (pt.getPrimitiveTypeCode() == PrimitiveType.FLOAT) {
                return ast.newSimpleType(ast.newName("java.lang.Float"));
            }
            else if (pt.getPrimitiveTypeCode() == PrimitiveType.DOUBLE) {
                return ast.newSimpleType(ast.newName("java.lang.Double"));
            }
            else if (pt.getPrimitiveTypeCode() == PrimitiveType.BYTE) {
                return ast.newSimpleType(ast.newName("java.lang.Byte"));
            }
            else if (pt.getPrimitiveTypeCode() == PrimitiveType.CHAR) {
                return ast.newSimpleType(ast.newName("java.lang.Character"));
            }
            else {
                throw new RuntimeException("Unknown primitive type: " + pt);
            }
        }
        return t;
    }

    @Override
    public boolean visit(MethodInvocation node) {
        if (node.getParent() instanceof ExpressionStatement && localMethodNames.contains(node.getName().getIdentifier())) {
            replace(node);
        }
        return false;
    }

    // let's assume instance creation does not usually create recursive calls
//    @Override
//    public boolean visit(ClassInstanceCreation node) {
//        if (node.getParent() instanceof ExpressionStatement && localMethodNames.contains()) {
//            replace(node);
//        }
//        return false;
//    }

    private void replace(ASTNode node) {
        Block closestBlock = RewriteFinalizer.getBlock(node);
        Statement originalStmt = RewriteFinalizer.getStatement(node);
        if (originalStmt.getParent() != closestBlock) {
            System.err.println("[DEBUG] failed to store/restore state, potentially unsafe for recursive calls");
        }
        else {
            if (!cache.contains(originalStmt)) {
                cache.add(originalStmt);
                MethodInvocation store = ast.newMethodInvocation();
                store.setName(ast.newSimpleName("store_" + id));
                Statement storeStmt = ast.newExpressionStatement(store);
                MethodInvocation restore = ast.newMethodInvocation();
                restore.setName(ast.newSimpleName("restore_" + id));
                Statement restoreStmt = ast.newExpressionStatement(restore);
                ListRewrite lsr = rewriter.getListRewrite(closestBlock, Block.STATEMENTS_PROPERTY);
                lsr.insertBefore(storeStmt, originalStmt, null);
                lsr.insertAfter(restoreStmt, originalStmt, null);
            }
        }
    }
}

/**
 * Store information about variant call site, e.g., to determine if we need to insert break check
 */
class VariantCallsite {
    Block block;
    boolean isInsideLoop;

    VariantCallsite(Block b, boolean isInLoop) {
        this.block = b;
        this.isInsideLoop = isInLoop;
    }
}
