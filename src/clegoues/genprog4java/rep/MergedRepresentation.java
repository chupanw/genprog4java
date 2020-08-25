package clegoues.genprog4java.rep;

import clegoues.genprog4java.Search.Population;
import clegoues.genprog4java.fitness.Fitness;
import clegoues.genprog4java.fitness.FitnessValue;
import clegoues.genprog4java.fitness.TestCase;
import clegoues.genprog4java.java.ClassInfo;
import clegoues.genprog4java.main.Configuration;
import clegoues.genprog4java.mut.EditHole;
import clegoues.genprog4java.mut.EditOperation;
import clegoues.genprog4java.mut.RewriteFinalizer;
import clegoues.genprog4java.mut.edits.java.*;
import clegoues.genprog4java.mut.holes.java.JavaLocation;
import clegoues.genprog4java.mut.holes.java.StatementHole;
import clegoues.genprog4java.mut.varexc.VarexCGlobal;
import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.jdt.core.dom.*;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;
import org.eclipse.text.edits.MalformedTreeException;
import org.eclipse.text.edits.TextEdit;

import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.*;

/**
 * Merging multiple JavaRepresentations into one by:
 *
 * 1. putting all genomes into one
 * 2. Keep a list of JavaRepresentation to selectively merge variants that can compile
 */
public class MergedRepresentation extends JavaRepresentation {

    private LinkedList<JavaRepresentation> composed = new LinkedList<>();
    private HashMap<ASTNode, List<ASTNode>> nodeStore = new HashMap<>();
    private HashSet<JavaEditOperation> compilableEdits = new HashSet<>();
    private HashSet<JavaEditOperation> uncompilableEdits = new HashSet<>();

    private MergedRepresentation(){}

    public static <G extends EditOperation> MergedRepresentation merge(Population<G> p) {
        MergedRepresentation merged = new MergedRepresentation();
        for (Representation<G> r : p) {
            JavaRepresentation jr = (JavaRepresentation) r;
            merged.composed.add(jr);
            for (JavaEditOperation e : jr.getGenome()) {
                merged.getGenome().add(e);
            }
            if (merged.sourceInfo != jr.sourceInfo)
                merged.sourceInfo = jr.sourceInfo;  // Should get updated only once because all JavaRepresentations are
                                                    // essentially copied from the original one
        }
        return merged;
    }

    @Override
    protected ArrayList<Pair<ClassInfo, String>> internalComputeSourceBuffers() {
        sortGenome();
        putExpMutationToEnd();
        collectEdits();
        excludeEdits();
        HashSet<EditOperation> toRemove = excludeRepetitive();
        serializeEdits();
        compilableEdits.removeAll(toRemove);    // the actual removal should come after serialization so that single edits can be preserved
        ArrayList<Pair<ClassInfo, String>> retVal = new ArrayList<Pair<ClassInfo, String>>();
        for (Map.Entry<ClassInfo, String> pair : sourceInfo.getOriginalSource().entrySet()) {
            ClassInfo ci = pair.getKey();
            String filename = ci.getClassName();
            String path = ci.getPackage();
            String source = pair.getValue();
            IDocument original = new Document(source);
            CompilationUnit cu = sourceInfo.getBaseCompilationUnits().get(ci);
            AST ast = cu.getAST();
            ASTRewrite rewriter = ASTRewrite.create(ast);
            VarexCGlobal.addImportGlobalOptions(cu, rewriter);
            RewriteFinalizer finalizer = new RewriteFinalizer(rewriter);

            try {
                for (JavaEditOperation edit : this.getGenome()) {
                    JavaLocation locationStatement = (JavaLocation) edit.getLocation();
                    if(compilableEdits.contains(edit) && locationStatement.getClassInfo()!=null && locationStatement.getClassInfo().getClassName().equalsIgnoreCase(filename) && locationStatement.getClassInfo().getPackage().equalsIgnoreCase(path)){
                        edit.methodEdit(rewriter, nodeStore, finalizer);
                    }
                }
                finalizer.finalizeEdits();
                TextEdit edits = rewriter.rewriteAST(original, null);
                edits.apply(original);
            } catch (IllegalArgumentException e) {
                e.printStackTrace();
                return null;
            } catch (MalformedTreeException e) {
                e.printStackTrace();
                return null;
            } catch (BadLocationException e) {
                e.printStackTrace();
                return null;
            } catch (ClassCastException e) {
                e.printStackTrace();
                return null;
            }
            // FIXME: I sense there's a better way to signify that
            // computeSourceBuffers failed than
            // to return null at those catch blocks

            retVal.add(Pair.of(ci, original.get()));
        }
        retVal.add(Pair.of(new ClassInfo("GlobalOptions", "varexc"), VarexCGlobal.getCodeAsString()));
        return retVal;
    }

    private void collectEdits() {
        for (JavaRepresentation jr : composed) {
            assert jr.getGenome().size() <= 1 : "Expecting single edits";
            if (jr.canCompile) {
                compilableEdits.addAll(jr.getGenome());
            } else {
                uncompilableEdits.addAll(jr.getGenome());
            }
        }
    }

    private void serializeEdits() {
        editFactory.pool.addEdits(compilableEdits, true);
        editFactory.pool.addEdits(uncompilableEdits, false);
        Path path = FileSystems.getDefault().getPath(Configuration.outputDir, Configuration.editSerFile);
        JavaEditPool.serialize(editFactory.pool, path);
    }

    /**
     * Exclude certain types of edits that can cause compilation issues when encoded as if statements
     *
     * this() calls (i.e., calls to other constructors)
     * super() calls (i.e., calls to parent class' constructor)
     *
     */
    private void excludeEdits() {
        HashSet<EditOperation> toRemove = new HashSet<>();
        for (EditOperation e : compilableEdits) {
            ASTNode code = ((JavaLocation) e.getLocation()).getCodeElement();
            EditHole fixHole = e.getHoleCode();
            if (code instanceof ConstructorInvocation || code instanceof SuperConstructorInvocation) {
                exclude(toRemove, e);
            }
            else if (code instanceof ExpressionStatement) {
                Expression expression = ((ExpressionStatement) code).getExpression();
                if (isAssignment2Final(expression))
                    exclude(toRemove, e);
            }
            else if (code instanceof Assignment) {
                if (isAssignment2Final((Expression) code))
                    exclude(toRemove, e);
            }
//            else if (code instanceof BreakStatement || code instanceof ContinueStatement) {
//                exclude(toRemove, e);
//            }
            else if (code instanceof Block) {
                exclude(toRemove, e);
            }
            if (fixHole instanceof StatementHole) {
                ASTNode fixStmt = ((StatementHole) fixHole).getCode();
                if (fixStmt instanceof VariableDeclarationStatement) {
                    exclude(toRemove, e);
                }
            }
            // exclude edits that append/replace loops or potential recursive calls
            // update: we might not need this anymore because we have a smarter block count reset
            // that checks the empty config
//            StatementTypeVisitor stmtVisitor = new StatementTypeVisitor();
//            if (fixHole != null && fixHole.getCode() != null) {
//                ASTNode fixCode = (ASTNode) fixHole.getCode();
//                stmtVisitor.markLocalMethods(fixCode);
//                fixCode.accept(stmtVisitor);
//            }
//            if (stmtVisitor.hasLoop || stmtVisitor.hasLocalMethodCall) {
//                exclude(toRemove, e);
//            }
        }
        compilableEdits.removeAll(toRemove);
    }

    private HashSet<EditOperation> excludeRepetitive() {
        HashSet<EditOperation> toRemove = new HashSet<>();
        // the value is the variant folder name that will be kept in the meta program
        HashMap<Expression, String> uniqueAORLocation = new HashMap<>();
        HashMap<Expression, String> uniqueUOILocation = new HashMap<>();
        HashMap<Expression, String> uniqueRORLocation = new HashMap<>();
        // no need for LCR and ABS as they have only one variant
        for (EditOperation e : compilableEdits) {
            if (e instanceof AOR) {
                if (uniqueAORLocation.containsKey(((AOR) e).locationExpr)) {
                    excludeAndResetVariantOption(toRemove, e, uniqueAORLocation.get(((AOR) e).locationExpr));
                }
                else {
                    uniqueAORLocation.put(((AOR) e).locationExpr, e.getVariantFolder());
                }
            }
            if (e instanceof UOI) {
                if (uniqueUOILocation.containsKey(((UOI) e).locationExpr)) {
                    excludeAndResetVariantOption(toRemove, e, uniqueUOILocation.get(((UOI) e).locationExpr));
                }
                else {
                    uniqueUOILocation.put(((UOI) e).locationExpr, e.getVariantFolder());
                }
            }
            if (e instanceof ROR) {
                if (uniqueRORLocation.containsKey(((ROR) e).locationExpr)) {
                    excludeAndResetVariantOption(toRemove, e, uniqueRORLocation.get(((ROR) e).locationExpr));
                }
                else {
                    uniqueRORLocation.put(((ROR) e).locationExpr, e.getVariantFolder());
                }
            }
        }
        return toRemove;
    }

    private void excludeAndResetVariantOption(Set<EditOperation> set, EditOperation e, String finalVariantFolder) {
        String optionName = finalVariantFolder + "_" + ((JavaEditOperation) e).getVariantOptionSuffix();
        ((JavaEditOperation) e).setVariantOption(optionName);
        logger.info("Resetting variant option of " + e.toString() + " to " + optionName + ", will be excluded after serialization");
        set.add(e);
    }

    private void exclude(Set<EditOperation> set, EditOperation e) {
        logger.info("Excluding " + e.toString());
        set.add(e);
    }

    private boolean isAssignment2Final(Expression expression) {
        if (expression instanceof Assignment) {
            Assignment assignment = (Assignment) expression;
            Expression left = assignment.getLeftHandSide();
            if (left instanceof FieldAccess) {
                return isFinalField(((FieldAccess) left).getName());
            }
            else if (left instanceof SimpleName) {
                return isFinalField((SimpleName) left);
            }
        }
        return false;
    }

    private boolean isFinalField(SimpleName name) {
        TypeDeclaration typeDecl = getClassDecl(name);
        FieldDeclaration[] fields = typeDecl.getFields();
        for (FieldDeclaration f : fields) {
            if (!Modifier.isFinal(f.getModifiers()))
                continue;
            List<VariableDeclarationFragment> variables = f.fragments();
            for (VariableDeclarationFragment vdf : variables) {
                if (vdf.getName().getIdentifier().equals(name.getIdentifier()))
                    return true;
            }
        }
        return false;
    }

    private TypeDeclaration getClassDecl(ASTNode node) {
        if (node instanceof TypeDeclaration)
            return (TypeDeclaration) node;
        else {
            ASTNode parent = node.getParent();
            if (parent == null)
                throw new RuntimeException("Unexpected AST: \n" + node.toString());
            else
                return getClassDecl(parent);
        }
    }

    /**
     * Verify test results with pos.tests and neg.tests, similar to sanity check
     *
     * @return  true if check succeeds
     */
    public boolean checkMerged() {
        long startTime = System.currentTimeMillis();
        logger.info("Merge check: checking test results of the merged code...");
        if (!this.compile(this.getVariantFolder(), this.getVariantFolder())) {
            logger.error("Merge check: " + this.getVariantFolder()
                    + " does not compile.");
            return false;
        }
        int testNum = 1;

        ArrayList<TestCase> passingTests = new ArrayList<TestCase>();
        // make list of passing files (sanitizing out of scope tests)
        int testsOutOfScope = 0;
        int testNumber = 0;
        for (TestCase posTest : Fitness.positiveTests) {
            testNumber++;
            logger.info("Merge check: checking test number " + testNumber + " out of " + Fitness.positiveTests.size());
            FitnessValue res = this.internalTestCase(
                    this.getVariantFolder(),
                    this.getVariantFolder(), posTest, false);
            if (!res.isAllPassed()) {
                testsOutOfScope++;
                logger.info(testsOutOfScope + " tests out of scope so far, out of " + Fitness.positiveTests.size());
                logger.error("Merge check: "
                        + this.getVariantFolder()
                        + " failed positive test " + posTest.getTestName());
                return false;
            } else {
                passingTests.add(posTest);
            }
            testNum++;
        }
        Fitness.positiveTests = passingTests;
        testNum = 1;
        if (passingTests.size() < 1) {
            logger.error("Merge check: no positive tests pass.");
            return false;
        }

        //print to a file only the tests in scope
        Fitness.printTestsInScope(passingTests);

        testNum = 1;
        for (TestCase negTest : Fitness.negativeTests) {
            logger.info("\tn" + testNum + ": ");
            FitnessValue res = this.internalTestCase(
                    this.getVariantFolder(),
                    this.getVariantFolder(), negTest, false);
            if (res.isAllPassed()) {
                logger.error("Merge check: "
                        + this.getVariantFolder()
                        + " passed negative test " + negTest.toString());
                return false;
            }
            testNum++;
        }
        this.updated();
        logger.info("Merge check completed (time taken = "
                + (System.currentTimeMillis() - startTime) + ")");
        return true;
    }

    private void sortGenome() {
        this.getGenome().sort(Comparator.comparingInt(o -> o.getLocation().getId()));
    }

    private void putExpMutationToEnd() {
        ArrayList<JavaEditOperation> expMutations = new ArrayList<>();
        for (JavaEditOperation edit : this.getGenome()) {
            if (edit.isExpMutation()) {
                expMutations.add(edit);
            }
        }
        this.getGenome().removeAll(expMutations);
        this.getGenome().addAll(expMutations);
    }
}

class StatementTypeVisitor extends ASTVisitor {
    boolean hasLoop = false;
    boolean hasLocalMethodCall = false;

    HashSet<String> localMethodNames = new HashSet<>();

    public void markLocalMethods(ASTNode startingFrom) {
        TypeDeclaration cls = getTypeDeclaration(startingFrom);
        MethodDeclaration[] allMethods = cls.getMethods();
        for (MethodDeclaration m : allMethods) {
            localMethodNames.add(m.getName().getIdentifier());
        }
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
    public boolean visit(MethodInvocation node) {
        if (localMethodNames.contains(node.getName().getIdentifier())) {
            hasLocalMethodCall = true;
        }
        return super.visit(node);
    }

    @Override
    public boolean visit(EnhancedForStatement node) {
        hasLoop = true;
        return super.visit(node);
    }

    @Override
    public boolean visit(ForStatement node) {
        hasLoop = true;
        return super.visit(node);
    }

    @Override
    public boolean visit(WhileStatement node) {
        hasLoop = true;
        return super.visit(node);
    }

    @Override
    public boolean visit(DoStatement node) {
        hasLoop = true;
        return super.visit(node);
    }
}