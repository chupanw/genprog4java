package clegoues.genprog4java.mut;

import clegoues.genprog4java.main.Configuration;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.ToolFactory;
import org.eclipse.jdt.core.dom.*;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;
import org.eclipse.jdt.core.formatter.CodeFormatter;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;
import org.eclipse.text.edits.TextEdit;

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
    private HashMap<MethodDeclaration, Boolean> needsReturnCheckAndFirst = new HashMap<>(); // the boolean field indicates if we need to wrap the modified return in if-stmt
    private HashSet<MethodDeclaration> needsBreakCheck = new HashSet<>();
    private HashSet<MethodDeclaration> needsContinueCheck = new HashSet<>();
    private HashMap<MethodDeclaration, VariantCallsite> variant2Callsite = new HashMap<>();
    private HashMap<MethodDeclaration, HashSet<MethodDeclaration>> method2Variants = new HashMap<>();
    private HashMap<ASTNode, MethodDeclaration> firstModifiedInVariant = new HashMap<>();
    /**
     * Applicable to all expression level operators
     */
    private HashSet<MethodDeclaration> variantsWithoutRewritingReturn = new HashSet<>();

    private Document mutatedJavaFile;
    private CompilationUnit cu = null;
    private AST ast = null;

    public RewriteFinalizer(Document doc) {
        mutatedJavaFile = doc;
    }

    //////////////////////////////////////////////////////////
    // Collect information while generating individual edits
    //////////////////////////////////////////////////////////

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
            needsReturnCheckAndFirst.put(chain.get(0), shouldWrapReturnInIf(locationNode));
        }
        if (locationNodeCheck.hasBreak || fixCodeNodeCheck.hasBreak) {
            MethodDeclaration closestBreakPoint = findClosestCheckPoint(locationNode, true);
            needsBreakCheck.add(closestBreakPoint);
        }
        if (locationNodeCheck.hasContinue || fixCodeNodeCheck.hasContinue) {
            MethodDeclaration closestContinuePoint = findClosestCheckPoint(locationNode, false);
            needsContinueCheck.add(closestContinuePoint);
        }
    }

    private boolean shouldWrapReturnInIf(Statement locationNode) {
        if (locationNode instanceof ReturnStatement && locationNode.getParent() instanceof Block && locationNode.getParent().getParent() instanceof MethodDeclaration) {
            return false;
        }
        return true;
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
    private MethodDeclaration findClosestCheckPoint(ASTNode locationNode, boolean shouldConsiderSwitchStmt) {
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

    //////////////////////////////////////////////////
    // Apply individual edits and update states
    //////////////////////////////////////////////////

    public void applyEditsSoFar(ASTRewrite rewriter) {
        for (Map.Entry<MethodDeclaration, HashSet<MethodDeclaration>> entry : method2Variants.entrySet()) {
            MethodDeclaration mutatedMethod = entry.getKey();
            writeVariantMethods(mutatedMethod, rewriter);
        }
    }

    private void writeVariantMethods(MethodDeclaration md, ASTRewrite rewriter) {
        TypeDeclaration classDecl = getTypeDeclaration(md);
        for (MethodDeclaration m : method2Variants.get(md)) {
            ListRewrite lr = rewriter.getListRewrite(classDecl, TypeDeclaration.BODY_DECLARATIONS_PROPERTY);
            lr.insertLast(m, null);
        }
    }

    /**
     * All the method declarations and call sites stored in the fields need update because object references are
     * different after applying the edits
     */
    private void updateAllFields() {
        ASTParser parser = ASTParser.newParser(AST.JLS8);
        Map options = JavaCore.getOptions();
        JavaCore.setComplianceOptions(Configuration.sourceVersion, options);
        parser.setCompilerOptions(options);
        parser.setSource(mutatedJavaFile.get().toCharArray());
        parser.setKind(ASTParser.K_COMPILATION_UNIT);
        this.cu = (CompilationUnit) parser.createAST(new NullProgressMonitor());
        this.ast = cu.getAST();

        this.needsBreakCheck = updateHashSet(this.needsBreakCheck);
        this.needsReturnCheckAndFirst = updateNeedsReturnCheckAndFirst(this.needsReturnCheckAndFirst);
        this.needsReturnCheck = updateHashSet(this.needsReturnCheck);
        this.needsContinueCheck = updateHashSet(this.needsContinueCheck);
        this.variantsWithoutRewritingReturn = updateHashSet(this.variantsWithoutRewritingReturn);
        this.variant2Callsite = updateVariant2Callsite(this.variant2Callsite);
        this.method2Variants = updateMethod2Variants(this.method2Variants);
    }

    private HashSet<MethodDeclaration> updateHashSet(HashSet<MethodDeclaration> oldSet) {
        HashSet<MethodDeclaration> res = new HashSet<>();
        for (MethodDeclaration oldMethod : oldSet) {
            res.add(getMethodDeclaration(this.cu, oldMethod));
        }
        return res;
    }

    private HashMap<MethodDeclaration, Boolean> updateNeedsReturnCheckAndFirst(HashMap<MethodDeclaration, Boolean> oldMap) {
        HashMap<MethodDeclaration, Boolean> res = new HashMap<>();
        for (Map.Entry<MethodDeclaration, Boolean> entry : oldMap.entrySet()) {
            res.put(getMethodDeclaration(this.cu, entry.getKey()), entry.getValue());
        }
        return res;
    }

    private HashMap<MethodDeclaration, HashSet<MethodDeclaration>> updateMethod2Variants(HashMap<MethodDeclaration, HashSet<MethodDeclaration>> oldHashMap) {
        HashMap<MethodDeclaration, HashSet<MethodDeclaration>> res = new HashMap<>();
        for (HashMap.Entry<MethodDeclaration, HashSet<MethodDeclaration>> oldEntry : oldHashMap.entrySet()) {
            MethodDeclaration oldMutatedMethod = oldEntry.getKey();
            MethodDeclaration newMutatedMethod = getMethodDeclaration(this.cu, oldMutatedMethod);
            HashSet<MethodDeclaration> newVariants = new HashSet<>();
            for (MethodDeclaration oldVariantMethod : oldEntry.getValue()) {
                MethodDeclaration newVariantMethod = getMethodDeclaration(this.cu, oldVariantMethod);
                newVariants.add(newVariantMethod);
            }
            res.put(newMutatedMethod, newVariants);
        }
        return res;
    }

    private HashMap<MethodDeclaration, VariantCallsite> updateVariant2Callsite(HashMap<MethodDeclaration, VariantCallsite> oldHashMap) {
        HashMap<MethodDeclaration, VariantCallsite> res = new HashMap<>();
        for (Map.Entry<MethodDeclaration, VariantCallsite> oldEntry : oldHashMap.entrySet()) {
            MethodDeclaration oldVariantMethod = oldEntry.getKey();
            VariantCallsite oldVariantCallsite = oldEntry.getValue();
            MethodDeclaration newVariantMethod = getMethodDeclaration(this.cu, oldVariantMethod);
            VariantCallsiteFinder finder = new VariantCallsiteFinder(oldVariantCallsite);
            this.cu.accept(finder);
            VariantCallsite newVariantCallsite = finder.getNewVariantCallsite();
            res.put(newVariantMethod, newVariantCallsite);
        }
        return res;
    }

    private MethodDeclaration getMethodDeclaration(CompilationUnit cu, MethodDeclaration oldMethod) {
        MethodDeclaration res = null;
        for (Object o : cu.types()) {
            TypeDeclaration t = (TypeDeclaration) o;
            res = getMethodDeclaration(t, oldMethod);
            if (res != null) return res;
        }
        MethodDeclarationCollector collector = new MethodDeclarationCollector();
        cu.accept(collector);
        for (MethodDeclaration m : collector.methods) {
            if (m.getName().getIdentifier().equals(oldMethod.getName().getIdentifier())
                    && m.parameters().size() == oldMethod.parameters().size()) {
                boolean parameterMatch = true;
                for (int i = 0; i < oldMethod.parameters().size(); i++) {
                    SingleVariableDeclaration thisParameter = (SingleVariableDeclaration) m.parameters().get(i);
                    SingleVariableDeclaration thatParameter = (SingleVariableDeclaration) oldMethod.parameters().get(i);
                    if (!thisParameter.getType().toString().equals(thatParameter.getType().toString()) || !thisParameter.getName().getIdentifier().equals(thatParameter.getName().getIdentifier())) {
                        parameterMatch = false;
                        break;
                    }
                }
                if (parameterMatch) {
                    return m;
                }
            }
        }
        throw new RuntimeException("MethodDeclaration not found: " + oldMethod.getName().getIdentifier());
    }

    private MethodDeclaration getMethodDeclaration(TypeDeclaration td, MethodDeclaration thatMethod) {
        for (Object o : td.getMethods()) {
            MethodDeclaration thisMethod = (MethodDeclaration) o;
            if (thisMethod.getName().getIdentifier().equals(thatMethod.getName().getIdentifier())
                    && thisMethod.parameters().size() == thatMethod.parameters().size()) {
                boolean parameterMatch = true;
                for (int i = 0; i < thatMethod.parameters().size(); i++) {
                    SingleVariableDeclaration thisParameter = (SingleVariableDeclaration) thisMethod.parameters().get(i);
                    SingleVariableDeclaration thatParameter = (SingleVariableDeclaration) thatMethod.parameters().get(i);
                    if (!thisParameter.getType().toString().equals(thatParameter.getType().toString()) || !thisParameter.getName().getIdentifier().equals(thatParameter.getName().getIdentifier())) {
                        parameterMatch = false;
                        break;
                    }
                }
                if (parameterMatch) {
                    return thisMethod;
                }
            }
        }
        return null;
    }

    ///////////////////////////////////////////////////////////////////
    // Finalize the transformation by creating and initializing fields
    ///////////////////////////////////////////////////////////////////

    public void finalizeEdits() {
        updateAllFields();
        for (HashMap.Entry<MethodDeclaration, HashSet<MethodDeclaration>> entry : method2Variants.entrySet()) {
            MethodDeclaration mutatedMethod = entry.getKey();

            addSynchronized(mutatedMethod);
            removeFinal();

            VarNamesCollector collector = new VarNamesCollector(mutatedMethod);
            collectVariableNames(collector, mutatedMethod);

            writeFieldsToClass(mutatedMethod, collector);

            VarToFieldVisitor var2field = new VarToFieldVisitor(collector);
            changeVars2Fields(mutatedMethod, collector, var2field);

            FieldInitVisitor fiv = new FieldInitVisitor(collector, this.ast);
            initFields(fiv, mutatedMethod);

            storeAndRestoreStates(collector, mutatedMethod);

            rewriteBreakContinueReturnInVariantMethods(mutatedMethod, collector, var2field);

            initBreakContinueReturnFields(mutatedMethod, collector);

            addChecksToVariantCallSites(mutatedMethod, collector);
        }
        updateDoc();
    }

    private void updateDoc() {
        String code = this.cu.toString();
        Map options = JavaCore.getOptions();
        JavaCore.setComplianceOptions(Configuration.sourceVersion, options);
        CodeFormatter formatter = ToolFactory.createCodeFormatter(options);
        TextEdit edit = formatter.format(CodeFormatter.K_COMPILATION_UNIT, code, 0, code.length(), 0, null);
        try {
            this.mutatedJavaFile.set(code);
            edit.apply(this.mutatedJavaFile);
        } catch (BadLocationException e) {
            e.printStackTrace();
        }
    }

    private void storeAndRestoreStates(VarNamesCollector collector, MethodDeclaration mutatedMethod) {
        StoreStateBeforeMethodCallVisitor stateRestoreVisitor = new StoreStateBeforeMethodCallVisitor(collector, mutatedMethod, this.ast);
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
            if (needsBreakCheck.contains(m)) {
//                FieldAccess fa = ast.newFieldAccess();
//                fa.setExpression(ast.newThisExpression());
                SimpleName fa = ast.newSimpleName(collector.hasBreakFieldName);
                Assignment assign = ast.newAssignment();
                assign.setLeftHandSide(fa);
                assign.setRightHandSide(ast.newBooleanLiteral(false));
                ExpressionStatement assignStmt = ast.newExpressionStatement(assign);
                m.getBody().statements().add(0, assignStmt);
            }
            if (needsContinueCheck.contains(m)) {
//                FieldAccess fa = ast.newFieldAccess();
//                fa.setExpression(ast.newThisExpression());
                SimpleName fa = ast.newSimpleName(collector.hasContinueFieldName);
                Assignment assign = ast.newAssignment();
                assign.setLeftHandSide(fa);
                assign.setRightHandSide(ast.newBooleanLiteral(false));
                ExpressionStatement assignStmt = ast.newExpressionStatement(assign);
                m.getBody().statements().add(0, assignStmt);
            }
            if (needsReturnCheck.contains(m)) {
//                FieldAccess fa = ast.newFieldAccess();
//                fa.setExpression(ast.newThisExpression());
                SimpleName fa = ast.newSimpleName(collector.hasReturnFieldName);
                Assignment assign = ast.newAssignment();
                assign.setLeftHandSide(fa);
                assign.setRightHandSide(ast.newBooleanLiteral(false));
                ExpressionStatement assignStmt = ast.newExpressionStatement(assign);
                m.getBody().statements().add(0, assignStmt);
            }
        }
    }

    private void addChecksToVariantCallSites(MethodDeclaration mutatedMethod, VarNamesCollector collector) {
        for (MethodDeclaration m : method2Variants.get(mutatedMethod)) {
            if (needsBreakCheck.contains(m)) {
                Block b = variant2Callsite.get(m).block;
                IfStatement ifStmt = ast.newIfStatement();
                SimpleName fa = ast.newSimpleName(collector.hasBreakFieldName);
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
                SimpleName fa = ast.newSimpleName(collector.hasContinueFieldName);
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
                SimpleName fa = ast.newSimpleName(collector.hasReturnFieldName);
                ifStmt.setExpression(fa);
                ReturnStatement retStmt = ast.newReturnStatement();
                if (needsReturnCheckAndFirst.containsKey(m) && hasReturnValue(mutatedMethod)) {
                    SimpleName retValFA = ast.newSimpleName(collector.returnValueFieldName);
                    retStmt.setExpression(retValFA);
                }
                if (needsReturnCheckAndFirst.containsKey(m) && !needsReturnCheckAndFirst.get(m)) {
                    b.statements().add(retStmt);
                }
                else {
                    ifStmt.setThenStatement(retStmt);
                    b.statements().add(ifStmt);
                }
            }
        }
    }

    private void rewriteBreakContinueReturnInVariantMethods(MethodDeclaration md, VarNamesCollector collector, VarToFieldVisitor var2field) {
        VariantBreakContinueReturnVisitor v = new VariantBreakContinueReturnVisitor(collector, this.ast);
        for (MethodDeclaration m : method2Variants.get(md)) {
            if (!variantsWithoutRewritingReturn.contains(m)) {
                m.accept(v);
            }
        }
        v.applyEdits();
    }

    private void changeVars2Fields(MethodDeclaration md, VarNamesCollector collector, VarToFieldVisitor v) {
        md.accept(v);
        for (MethodDeclaration m : method2Variants.get(md)) {
            m.accept(v);
        }
    }

    private void addSynchronized(MethodDeclaration md) {
        if (!md.isConstructor()) {
            md.modifiers().add(ast.newModifier(Modifier.ModifierKeyword.SYNCHRONIZED_KEYWORD));
        }
    }

    /**
     * Current mutation group extraction does not consider alternative changes to the same final field as conflicting
     */
    private void removeFinal() {
        for (Object ot : this.cu.types()) {
            TypeDeclaration t = (TypeDeclaration) ot;
            for (FieldDeclaration f : t.getFields()) {
                int idx = -1;
                for (int i = 0; i < f.modifiers().size(); i++) {
                    if ((f.modifiers().get(i) instanceof Modifier) && ((Modifier) f.modifiers().get(i)).isFinal()) {
                        idx = i;
                    }
                }
                if (idx != -1)
                    f.modifiers().remove(idx);
            }
        }
    }


//    /**
//     * Mark that a node will be replaced later by some other variants, so that we can avoid mutating it. Otherwise,
//     * some variant methods might get silenced.
//     */
//    public void markPendingChange(ASTNode n) {
//        pendingChanges.add(n);
//    }



    public static boolean isLoop(ASTNode n) {
        return n instanceof WhileStatement || n instanceof ForStatement || n instanceof EnhancedForStatement || n instanceof DoStatement;
    }

    public static boolean isSwitch(ASTNode n) {
        return n instanceof SwitchStatement;
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


    private void writeFieldsToClass(MethodDeclaration md, VarNamesCollector collector) {
        TypeDeclaration classDecl = getTypeDeclaration(md);
        for (MyParameter p : collector.parameters) {
            FieldDeclaration fd = genField(p.getType(), collector.varNames.get(p.getName().getIdentifier()));
            classDecl.bodyDeclarations().add(fd);
        }
        for (MyParameter p : collector.localVariables) {
            FieldDeclaration fd = genField(p.getType(), collector.varNames.get(p.getName().getIdentifier()));
            classDecl.bodyDeclarations().add(fd);
        }
        // additional fields for keeping the states of looping
        FieldDeclaration hasBreak = genField(ast.newPrimitiveType(PrimitiveType.BOOLEAN), collector.hasBreakFieldName);
        classDecl.bodyDeclarations().add(hasBreak);
        FieldDeclaration hasReturn = genField(ast.newPrimitiveType(PrimitiveType.BOOLEAN), collector.hasReturnFieldName);
        classDecl.bodyDeclarations().add(hasReturn);
        FieldDeclaration hasContinue = genField(ast.newPrimitiveType(PrimitiveType.BOOLEAN), collector.hasContinueFieldName);
        classDecl.bodyDeclarations().add(hasContinue);
        if (hasReturnValue(md)) {
            FieldDeclaration returnValue = genField(md.getReturnType2(), collector.returnValueFieldName);
            classDecl.bodyDeclarations().add(returnValue);
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

    Type getTypeOf(String name) {
        for (MyParameter p : parameters) {
            if (p.getName().getIdentifier().equals(name))
                return p.getType();
        }
        for (MyParameter p : localVariables) {
            if (p.getName().getIdentifier().equals(name))
                return p.getType();
        }
        throw new RuntimeException("Couldn't find variable with name " + name);
    }

    @Override
    public boolean visit(SingleVariableDeclaration node) {
        // formal parameters
        String name = node.getName().getIdentifier();
        if (!varNames.containsKey(name)) {
            AST ast = node.getAST();
            String fieldName = name + "_" + genRandomString();
            varNames.put(name, fieldName);
            Type t = node.getType();
            if (!node.extraDimensions().isEmpty()) {
                t = node.getAST().newArrayType((Type) ASTNode.copySubtree(node.getAST(), t), node.extraDimensions().size());
            }
            MyParameter p = new MyParameter(t, ast.newSimpleName(name), ast, node.isVarargs());
            if (node.getLocationInParent() == MethodDeclaration.PARAMETERS_PROPERTY) {
                parameters.add(p);
            } else {
                localVariables.add(p);
            }
        } else {
            Type thisType = node.getType();
            Type existingType = getTypeOf(name);
            if (!thisType.toString().equals(existingType.toString()))
                throw new RuntimeException("Repeated variable names, rename to avoid type collision: " + name + " in method " + md.getName().getIdentifier());
        }
        return false;
    }

    @Override
    public boolean visit(VariableDeclarationExpression node) {
        Type t = node.getType();
        for (VariableDeclarationFragment f : (List<VariableDeclarationFragment>) node.fragments()) {
            String varName = f.getName().getIdentifier();
            if (!f.extraDimensions().isEmpty()) {
                t = node.getAST().newArrayType((Type) ASTNode.copySubtree(node.getAST(), t), f.extraDimensions().size());
            }
            if (!varNames.containsKey(varName)) {
                AST ast = node.getAST();
                String fieldName = varName + "_" + genRandomString();
                varNames.put(varName, fieldName);
                MyParameter p = new MyParameter(t, ast.newSimpleName(varName), ast, false);
                localVariables.add(p);
            } else {
                Type thisType = t;
                Type existingType = getTypeOf(varName);
                if (!thisType.toString().equals(existingType.toString()))
                    throw new RuntimeException("Repeated variable names, rename to avoid type collision: " + varName + " in method " + md.getName().getIdentifier());
            }
        }
        return false;
    }

    @Override
    public boolean visit(VariableDeclarationStatement node) {
        Type t = node.getType();
        for (VariableDeclarationFragment f : (List<VariableDeclarationFragment>) node.fragments()) {
            String varName = f.getName().getIdentifier();
            if (!f.extraDimensions().isEmpty()) {
                t = node.getAST().newArrayType((Type) ASTNode.copySubtree(node.getAST(), t), f.extraDimensions().size());
            }
            if (!varNames.containsKey(varName)) {
                AST ast = node.getAST();
                String fieldName = varName + "_" + genRandomString();
                varNames.put(varName, fieldName);
                MyParameter p = new MyParameter(t, ast.newSimpleName(varName), ast, false);
                localVariables.add(p);
            } else {
                Type thisType = t;
                Type existingType = getTypeOf(varName);
                if (!thisType.toString().equals(existingType.toString()))
                    throw new RuntimeException("Repeated variable names, rename to avoid type collision: " + varName + " in method " + md.getName().getIdentifier());
            }
        }
        return false;
    }
}

class VarToFieldVisitor extends ASTVisitor {
    VarNamesCollector collector;

    VarToFieldVisitor(VarNamesCollector collector) {
        this.collector = collector;
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
            node.setIdentifier(collector.varNames.get(node.getIdentifier()));
        }
        return false;
    }

    private boolean isMethodName(ASTNode n) {
        StructuralPropertyDescriptor property = n.getLocationInParent();
        return property == MethodInvocation.NAME_PROPERTY;
    }

    private boolean isPartOfFieldAccess(ASTNode n) {
        StructuralPropertyDescriptor property = n.getLocationInParent();
        return property == FieldAccess.NAME_PROPERTY;
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
    AST ast;

    FieldInitVisitor(VarNamesCollector collector, AST ast) {
        this.collector = collector;
        this.ast = ast;
    }

    public void initParameterFields() {
        for (MyParameter p : collector.parameters) {
            Assignment assign = ast.newAssignment();
            SimpleName fa = ast.newSimpleName(collector.varNames.get(p.getName().getIdentifier()));
            assign.setLeftHandSide(fa);
            assign.setRightHandSide(p.getName());
            ExpressionStatement assignStmt = ast.newExpressionStatement(assign);
            if (collector.md.isConstructor() && collector.md.getBody().statements().get(0) instanceof SuperConstructorInvocation) {
                collector.md.getBody().statements().add(1, assignStmt);
            }
            else {
                collector.md.getBody().statements().add(0, assignStmt);
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
                SimpleName fa = ast.newSimpleName(collector.varNames.get(name));
                assign.setLeftHandSide(fa);
                assign.setRightHandSide(ast.newSimpleName(name));
                ExpressionStatement assignStmt = ast.newExpressionStatement(assign);
                ((Block) parent.getBody()).statements().add(0, assignStmt);
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
                SimpleName fa = ast.newSimpleName(collector.varNames.get(f.getName().getIdentifier()));
                assign.setLeftHandSide(fa);
                assign.setRightHandSide(initializer);
                ExpressionStatement assignStmt = ast.newExpressionStatement(assign);
                assignments.add(assignStmt);
            }
        }
        List statements;
        if (node.getParent() instanceof Block) {
            statements = ((Block) node.getParent()).statements();
        }
        else if (node.getParent() instanceof SwitchStatement) {
            statements = ((SwitchStatement) node.getParent()).statements();
        }
        else {
            throw new RuntimeException("Unexpected type, check if it has statements field: " + node.getParent());
        }
        int baseIndex = statements.indexOf(node);
        statements.remove(baseIndex);
        for (int i = 0; i < assignments.size(); i++) {
            statements.add(baseIndex + i, assignments.get(i));
        }
        return false;
    }

    @Override
    public boolean visit(VariableDeclarationExpression node) {
        if (node.getParent() instanceof ForStatement) {
            ForStatement forStatement = (ForStatement) node.getParent();
            List<Assignment> assignments = new LinkedList<>();
            for (VariableDeclarationFragment f : (List<VariableDeclarationFragment>) node.fragments()) {
                Expression initializer = (Expression) ASTNode.copySubtree(ast, f.getInitializer());
                if (initializer != null && collector.varNames.containsKey(f.getName().getIdentifier())) {
                    Assignment assign = ast.newAssignment();
                    SimpleName fa = ast.newSimpleName(collector.varNames.get(f.getName().getIdentifier()));
                    assign.setLeftHandSide(fa);
                    assign.setRightHandSide(initializer);
                    assignments.add(assign);
                }
            }
            forStatement.initializers().remove(node);
            for (Assignment assign : assignments) {
                forStatement.initializers().add(assign);
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
    AST ast;
    LinkedList<Edit> edits = new LinkedList<>();

    public VariantBreakContinueReturnVisitor(VarNamesCollector collector, AST ast) {
        this.collector = collector;
        this.ast = ast;
    }

    @Override
    public boolean visit(BreakStatement node) {
        if (!isPartOfSwitchStatement(node)) {
            Block b = ast.newBlock();
            Assignment assign = ast.newAssignment();
            SimpleName fa = ast.newSimpleName(collector.hasBreakFieldName);
            assign.setLeftHandSide(fa);
            assign.setRightHandSide(ast.newBooleanLiteral(true));
            ExpressionStatement assignStmt = ast.newExpressionStatement(assign);
            b.statements().add(assignStmt);
            b.statements().add(ast.newReturnStatement());

            StructuralPropertyDescriptor property = node.getLocationInParent();
            if (property instanceof ChildListPropertyDescriptor) {
                storeEdit((Statement) node.getParent(), node, b);
            } else {
                node.getParent().setStructuralProperty(property, b);
            }
        }
        return false;
    }

    @Override
    public boolean visit(ContinueStatement node) {
        Block b = ast.newBlock();
        Assignment assign = ast.newAssignment();
        SimpleName fa = ast.newSimpleName(collector.hasContinueFieldName);
        assign.setLeftHandSide(fa);
        assign.setRightHandSide(ast.newBooleanLiteral(true));
        ExpressionStatement assignStmt = ast.newExpressionStatement(assign);
        b.statements().add(assignStmt);
        b.statements().add(ast.newReturnStatement());

        StructuralPropertyDescriptor property = node.getLocationInParent();
        if (property instanceof ChildListPropertyDescriptor) {
            storeEdit((Statement) node.getParent(), node, b);
        } else {
            node.getParent().setStructuralProperty(property, b);
        }
        return false;
    }

    @Override
    public boolean visit(ReturnStatement node) {
        Block b = ast.newBlock();
        Assignment assign = ast.newAssignment();
        SimpleName fa = ast.newSimpleName(collector.hasReturnFieldName);
        assign.setLeftHandSide(fa);
        assign.setRightHandSide(ast.newBooleanLiteral(true));
        ExpressionStatement assignStmt = ast.newExpressionStatement(assign);
        b.statements().add(assignStmt);
        if (node.getExpression() != null) {
            Assignment retValAssign = ast.newAssignment();
            SimpleName retValFA = ast.newSimpleName(collector.returnValueFieldName);
            retValAssign.setLeftHandSide(retValFA);
            Expression retVal = (Expression) ASTNode.copySubtree(ast, node.getExpression());
            retValAssign.setRightHandSide(retVal);
            ExpressionStatement assignStmt2 = ast.newExpressionStatement(retValAssign);
            b.statements().add(assignStmt2);
        }
        b.statements().add(ast.newReturnStatement());

        StructuralPropertyDescriptor property = node.getLocationInParent();
        if (property instanceof ChildListPropertyDescriptor) {
            storeEdit((Statement) node.getParent(), node, b);
        } else {
            node.getParent().setStructuralProperty(property, b);
        }
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

    private void storeEdit(Statement b, ASTNode oldNode, ASTNode newNode) {
        Edit e = new Edit(b, oldNode, newNode);
        edits.add(e);
    }

    public void applyEdits() {
        for (Edit e : edits) {
            List statements;
            if (e.block instanceof Block)
                statements = ((Block) e.block).statements();
            else if (e.block instanceof SwitchStatement)
                statements = ((SwitchStatement) e.block).statements();
            else
                throw new RuntimeException("Unknown Statement type, does it have a statements field?: " + e.block);
            int index = statements.indexOf(e.oldNode);
            statements.remove(e.oldNode);
            statements.add(index, e.newNode);
        }
    }

    class Edit {
        Statement block;
        ASTNode oldNode;
        ASTNode newNode;
        Edit(Statement b, ASTNode oldNode, ASTNode newNode) {
            this.block = b;
            this.oldNode = oldNode;
            this.newNode = newNode;
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
    TypeDeclaration mutatedClass;

    HashSet<String> localMethodNames;
    List<MyParameter> sortedVariables;
    HashMap<String, Type> nameToType;
    AST ast;
    HashSet<ASTNode> cache;

    StoreStateBeforeMethodCallVisitor(VarNamesCollector collector, MethodDeclaration mutatedMethod, AST ast) {
        this.collector = collector;
        this.mutatedClass = RewriteFinalizer.getTypeDeclaration(mutatedMethod);

        cache = new HashSet<>();
        this.ast = ast;
        this.id = UUID.randomUUID().toString().replace('-', '_');
        sortedVariables = new LinkedList<>();
        nameToType = new HashMap<>();
        for (MyParameter p : collector.localVariables) {
            sortedVariables.add(p);
            nameToType.put(collector.varNames.get(p.getName().getIdentifier()), p.getType());
        }
        for (MyParameter p : collector.parameters) {
            sortedVariables.add(p);
            nameToType.put(collector.varNames.get(p.getName().getIdentifier()), p.getType());
        }
        sortedVariables.sort((a, b) -> a.getName().getIdentifier().compareTo(b.getName().getIdentifier()));
        localMethodNames = new HashSet<>();
        for (MethodDeclaration m : mutatedClass.getMethods()) {
            if (!m.getName().getIdentifier().startsWith("variant")) {
                localMethodNames.add(m.getName().getIdentifier());
            }
        }
    }

    public void writeStoreAndRestoreToClass() {

        // stack field
        ClassInstanceCreation cic = ast.newClassInstanceCreation();
        cic.setType(ast.newSimpleType(ast.newName("java.util.Stack")));
        VariableDeclarationFragment vdf = ast.newVariableDeclarationFragment();
        vdf.setName(ast.newSimpleName("stack_" + id));
        vdf.setInitializer(cic);
        FieldDeclaration stackField = ast.newFieldDeclaration(vdf);
        stackField.modifiers().add(ast.newModifier(Modifier.ModifierKeyword.STATIC_KEYWORD));
        stackField.setType(ast.newSimpleType(ast.newName("java.util.Stack")));
        mutatedClass.bodyDeclarations().add(0, stackField);

        // store method
        MethodDeclaration storeMethod = ast.newMethodDeclaration();
        storeMethod.setName(ast.newSimpleName("store_" + id));
        storeMethod.setReturnType2(ast.newPrimitiveType(PrimitiveType.VOID));
        storeMethod.modifiers().add(ast.newModifier(Modifier.ModifierKeyword.STATIC_KEYWORD));
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
        mutatedClass.bodyDeclarations().add(0, storeMethod);

        // restore method
        MethodDeclaration restoreMethod = ast.newMethodDeclaration();
        restoreMethod.setName(ast.newSimpleName("restore_" + id));
        restoreMethod.setReturnType2(ast.newPrimitiveType(PrimitiveType.VOID));
        restoreMethod.modifiers().add(ast.newModifier(Modifier.ModifierKeyword.STATIC_KEYWORD));
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
        mutatedClass.bodyDeclarations().add(0, restoreMethod);
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
        // restore after return statement is unreachable
        if (localMethodNames.contains(node.getName().getIdentifier()) && !(RewriteFinalizer.getStatement(node) instanceof ReturnStatement)) {
            Assignment assign = getAssignment(node);
            if (assign != null) {
                Expression lhs = assign.getLeftHandSide();
                if (lhs instanceof SimpleName && nameToType.containsKey(((SimpleName) lhs).getIdentifier())) {
                    String originalVarName = ((SimpleName) lhs).getIdentifier();
                    String tmpVarName = "tmp_" + UUID.randomUUID().toString().replace('-', '_');
                    Statement stmt = RewriteFinalizer.getStatement(node);

                    assign.setLeftHandSide(ast.newSimpleName(tmpVarName));

                    VariableDeclarationFragment tmpDefFragment = ast.newVariableDeclarationFragment();
                    tmpDefFragment.setName(ast.newSimpleName(tmpVarName));
                    VariableDeclarationStatement tmpDefStmt = ast.newVariableDeclarationStatement(tmpDefFragment);
                    tmpDefStmt.setType((Type) ASTNode.copySubtree(ast, nameToType.get(originalVarName)));

                    Assignment tmp2origin = ast.newAssignment();
                    tmp2origin.setLeftHandSide(ast.newSimpleName(originalVarName));
                    tmp2origin.setRightHandSide(ast.newSimpleName(tmpVarName));
                    ExpressionStatement tmp2originStmt = ast.newExpressionStatement(tmp2origin);

                    if (stmt.getParent() instanceof Block) {
                        Block b = (Block) stmt.getParent();
                        int idx = b.statements().indexOf(stmt);
                        b.statements().add(idx, tmpDefStmt);
                        b.statements().add(idx + 2, tmp2originStmt);
                    } 
                    else if (stmt.getParent() instanceof SwitchStatement) {
                        SwitchStatement ss = (SwitchStatement) stmt.getParent();
                        int idx = ss.statements().indexOf(stmt);
                        ss.statements().add(idx, tmpDefStmt);
                        ss.statements().add(idx + 2, tmp2originStmt);
                    }
                    else {
                        Block b = ast.newBlock();
                        StructuralPropertyDescriptor property = stmt.getLocationInParent();
                        stmt.getParent().setStructuralProperty(property, b);
                        b.statements().add(tmpDefStmt);
                        b.statements().add(stmt);
                        b.statements().add(tmp2originStmt);
                    }
                }
            } 
            replace(node);
        }
        return false;
    }

    private Assignment getAssignment(ASTNode n) {
        if (n == null) {
            return null;
        }
        else if (n instanceof Assignment) {
            return (Assignment) n;
        } 
        else {
            return getAssignment(n.getParent());
        }
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
            System.err.println("[DEBUG] method call not inside block, failed to store/restore state, potentially unsafe for recursive calls");
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
                int originalStmtIndex = closestBlock.statements().indexOf(originalStmt);
                closestBlock.statements().add(originalStmtIndex, storeStmt);
                closestBlock.statements().add(originalStmtIndex + 2, restoreStmt);
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

class VariantCallsiteFinder extends ASTVisitor {
    private VariantCallsite oldCallsite;
    private Block newCallsite = null;
    VariantCallsiteFinder(VariantCallsite old) {
        this.oldCallsite = old;
    }
    @Override
    public boolean visit(Block node) {
        if (node.toString().equals(oldCallsite.block.toString())) {
            newCallsite = node;
            return false;
        }
        return true;
    }
    public VariantCallsite getNewVariantCallsite() {
        if (newCallsite == null)
            throw new RuntimeException("Couldn't find matching callsite for " + oldCallsite.block.toString());
        else {
            return new VariantCallsite(newCallsite, oldCallsite.isInsideLoop);
        }
    }
}

/**
 * Fallback way of matching old method to new method.
 *
 * Only methods are matched, no types.
 */
class MethodDeclarationCollector extends ASTVisitor {
    HashSet<MethodDeclaration> methods = new HashSet<>();
    @Override
    public boolean visit(MethodDeclaration node) {
        methods.add(node);
        return true;
    }
}