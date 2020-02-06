package clegoues.genprog4java.mut;

import clegoues.genprog4java.mut.varexc.VarexCGlobal;
import org.apache.log4j.Logger;
import org.eclipse.jdt.core.dom.*;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.text.edits.TextEdit;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;

/**
 * Cut a method into some smaller ones for VarexC
 */
public class MethodCutter {

    private IDocument sourceFile;
    private ASTNode rootASTNode;
    private ASTRewrite rewriter;
    private AST ast;
    private int maxBytes;
    private Logger logger = Logger.getLogger(MethodCutter.class);

    /**
     * Construct a method cutter instance from source document and memory footprint limit.
     *
     * For now, we only cut at the statement level in a given method body, so that we don'type need fancy analysis to
     * find a cut point. Note that if-else is a single statement in JDT, see {@link Statement} for different types.
     *
     * We assume that single statements are not too big. If our assumption does not hold, we might need more
     * fine-grained cutting.
     *
     * @param document  IDocument instance that stores the source code
     * @param maxBytes  Maximum bytes for a method based on {@link ASTNode#subtreeBytes()}, which is very
     *                  inaccurate.
     */
    public MethodCutter(IDocument document, int maxBytes) {
        this.sourceFile = document;
        this.maxBytes = maxBytes;
        ASTParser parser = ASTParser.newParser(AST.JLS8);
        parser.setSource(document.get().toCharArray());
        this.rootASTNode = parser.createAST(null);
        this.rewriter = ASTRewrite.create(rootASTNode.getAST());
        this.ast = this.rewriter.getAST();
    }

    public void applyCutEdits() {
        CompilationUnit cu = (CompilationUnit) rootASTNode;
        for (Object o : cu.types()) {
            processClass((TypeDeclaration) o);
        }
        TextEdit edits = rewriter.rewriteAST(sourceFile, null);
        try {
            edits.apply(sourceFile);
        } catch (BadLocationException e) {
            e.printStackTrace();
        }
    }

    private void processClass(TypeDeclaration classDecl) {
        MethodDeclaration[] methodDecls = classDecl.getMethods();
        for (MethodDeclaration m : methodDecls) {
            if (m.isConstructor()) continue;
            processMethod(classDecl, m);
        }
    }

    /**
     * Iteratively split methods into smaller ones
     *
     * If the first statement of the method is already too big, we try to split that statement further,
     * based on the statement type.
     *
     * @param classDecl
     * @param methodDecl
     */
    private MethodDeclaration processMethod(TypeDeclaration classDecl, MethodDeclaration methodDecl) {
        if (methodDecl.getBody() == null) {
            // abstract methods
            return methodDecl;
        }
        List<Statement> statements = methodDecl.getBody().statements();
        Statement cp = findCutPoint(statements);
        if (cp != null) {
            // different ways of splitting depending on the types of statement
            if (statements.indexOf(cp) == 1) {
                Statement first = statements.get(0);
                if (first instanceof IfStatement) {
                    return splitIfStatement(classDecl, methodDecl, cp);
                }
                else if (first instanceof Block) {
                    System.err.println("Block? Should not appear!?");
                    return splitMethodBody(classDecl, methodDecl, cp);
                }
                else if (first instanceof WhileStatement || first instanceof ForStatement || first instanceof EnhancedForStatement || first instanceof DoStatement || first instanceof SwitchStatement || first instanceof TryStatement) {
                    System.err.println("Might need to split " + first.getClass());
                    return splitMethodBody(classDecl, methodDecl, cp);
                }
                else {
                    return splitMethodBody(classDecl, methodDecl, cp);
                }
            }
            else {
                return splitMethodBody(classDecl, methodDecl, cp);
            }
        }
        else {
            return methodDecl;
        }
    }

    /**
     * Split a huge {@link IfStatement}
     *
     * We assume the incoming method to have the following structure:
     *
     *  if (condition)
     *      Statement
     *  [
     *  else
     *      Statement
     *  ]
     *  Statements*
     *
     * In this case, we split in the following way:
     *
     *  1. Put the tailing statements into a separate method M1, and replace with a method call C1
     *  2. Put C1 into both then branch and else branch, if there is an else branch
     *  3. Extract branches as methods and replace with method calls
     *  4. Continue splitting the extracted methods
     *
     *
     * @param classDecl
     * @param methodDecl
     * @return
     */
    private MethodDeclaration splitIfStatement(TypeDeclaration classDecl, MethodDeclaration methodDecl, Statement cp) {
        logger.info("Cutting " + classDecl.getName() + "." + methodDecl.getName() + " by splitting if statement");
        IfStatement ifStmt = (IfStatement) methodDecl.getBody().statements().get(0);
        // Step 1
        MethodDeclaration splitM = splitMethodBody(classDecl, methodDecl, cp);
        // todo: has this been written yet?

        // Step 2
        ListRewrite lr = rewriter.getListRewrite(splitM.getBody(), Block.STATEMENTS_PROPERTY);
        List<Statement> rewritten = lr.getRewrittenList();
        Statement last = rewritten.get(rewritten.size() - 1);
        lr.remove(last, null);

        // then branch
        List<Statement> thenStmts = new LinkedList<>();
        if (ifStmt.getThenStatement() instanceof Block) {
            thenStmts.addAll(getStmtsFromBlock((Block) ifStmt.getThenStatement()));
        }
        else {
            thenStmts.add(ifStmt.getThenStatement());
        }
        if (!isTerminalStmt(thenStmts.get(thenStmts.size() - 1)))
            thenStmts.add(last);
        Block thenBlock = branch2Method(classDecl, methodDecl, thenStmts);

        // else branch
        List<Statement> elseStmts = new LinkedList<>();
        if (ifStmt.getElseStatement() != null) {
            if (ifStmt.getElseStatement() instanceof Block) {
                elseStmts.addAll(getStmtsFromBlock((Block) ifStmt.getElseStatement()));
            }
            else {
                elseStmts.add(ifStmt.getElseStatement());
            }
        }
        if (elseStmts.isEmpty() || !isTerminalStmt(elseStmts.get(elseStmts.size() - 1)))
            elseStmts.add(last);
        Block elseBlock = branch2Method(classDecl, methodDecl, elseStmts);

        IfStatement replacementIfStmt = ast.newIfStatement();
        replacementIfStmt.setExpression((Expression) ASTNode.copySubtree(ast, ifStmt.getExpression()));
        replacementIfStmt.setThenStatement(thenBlock);
        replacementIfStmt.setElseStatement(elseBlock);

        rewriter.replace(ifStmt, replacementIfStmt, null);

        return methodDecl;
    }

    private boolean isTerminalStmt(Statement s) {
        return s instanceof ReturnStatement || s instanceof ThrowStatement || s instanceof BreakStatement || s instanceof ContinueStatement;
    }

    private List<Statement> getStmtsFromBlock(Block block) {
        if (block.statements().size() == 1 && block.statements().get(0) instanceof Block) {
            return getStmtsFromBlock((Block) block.statements().get(0));
        } else {
            return block.statements();
        }
    }

    /**
     * Potentially unsafe if there are blocks for scoping in the original code
     * @param block
     * @param statements
     */
    private void addStmtsToBlock(Block block, List<Statement> statements) {
        for (Statement s : statements) {
            if (s instanceof Block) {
                addStmtsToBlock(block, ((Block) s).statements());
            } else {
                block.statements().add(ASTNode.copySubtree(ast, s));
            }
        }
    }

    private Block branch2Method(TypeDeclaration classDecl, MethodDeclaration methodDecl, List<Statement> statements) {
        MethodDeclaration m = ast.newMethodDeclaration();
        MethodInvocation mi = ast.newMethodInvocation();
        Block mBody = ast.newBlock();
        String mName = "_splitting_branch_" + VarexCGlobal.getNextMethodID();

        mi.setName(ast.newSimpleName(mName));
        m.setReturnType2((Type) ASTNode.copySubtree(ast, methodDecl.getReturnType2()));
        m.setName(ast.newSimpleName(mName));
        m.setBody(mBody);

        HashSet<Parameter> parameters = getParameters(methodDecl);
        for (Parameter p : parameters) {
            SingleVariableDeclaration svd = ast.newSingleVariableDeclaration();
            svd.setType(p.getType());
            svd.setName(p.getName());
            m.parameters().add(svd);
            mi.arguments().add(p.getName());
        }

        addStmtsToBlock(mBody, statements);

        MethodDeclaration processed = processMethod(classDecl, m);
        writeMethod2Class(classDecl, processed);

        Block branch = ast.newBlock();
        Type retType = m.getReturnType2();
        if (retType != null && retType.isPrimitiveType() && ((PrimitiveType) retType).getPrimitiveTypeCode() == PrimitiveType.VOID) {
            ExpressionStatement expStmt = ast.newExpressionStatement(mi);
            branch.statements().add(expStmt);
        }
        else {
            ReturnStatement retStmt = ast.newReturnStatement();
            retStmt.setExpression(mi);
            branch.statements().add(retStmt);
        }
        return branch;
    }

    /**
     * Split a method at some point, put the trailing statements into a new method and insert a method call
     *
     * @param classDecl Current class declaration, used for inserting new methods
     * @param methodDecl    The method being split
     * @param cp    Cut point, the statement starting from which a new method should be created
     * @return  The method after splitting.
     */
    private MethodDeclaration splitMethodBody(TypeDeclaration classDecl, MethodDeclaration methodDecl, Statement cp) {
        logger.info("Cutting " + classDecl.getName() + "." + methodDecl.getName() + " by splitting method body");

        List<Statement> statements = methodDecl.getBody().statements();
        HashSet<Parameter> blockVarsInScope = getVarsInScope(methodDecl.getBody(), cp);
        HashSet<Parameter> parameters = getParameters(methodDecl);
        HashSet<Parameter> varsInScope = new HashSet<>(blockVarsInScope);
        varsInScope.addAll(parameters);

        // Construct a new method and the method invocation
        MethodDeclaration m = ast.newMethodDeclaration();
        String mn = "_snippet_" + methodDecl.getName().getIdentifier() + "_" + VarexCGlobal.getNextMethodID();
        m.setName(ast.newSimpleName(mn));
        Type retType = getReturnType(methodDecl);
        m.setReturnType2((Type) ASTNode.copySubtree(ast, retType));
        MethodInvocation mi = ast.newMethodInvocation();    // the replacement return expression
        mi.setName(ast.newSimpleName(mn));

        for (Parameter p : varsInScope) {
            SingleVariableDeclaration svd = ast.newSingleVariableDeclaration();
            svd.setType(p.getType());
            svd.setName(p.getName());
            m.parameters().add(svd);
            mi.arguments().add(p.getName());
        }

        Block block = ast.newBlock();
        m.setBody(block);
        int index = statements.indexOf(cp);
        ListRewrite lr = rewriter.getListRewrite(methodDecl.getBody(), Block.STATEMENTS_PROPERTY);

        for (int i = index; i < statements.size(); i++) {
            block.statements().add(ASTNode.copySubtree(ast, statements.get(i)));
            lr.remove(statements.get(i), null);
        }

        if (!(m.getReturnType2().isPrimitiveType() && ((PrimitiveType) m.getReturnType2()).getPrimitiveTypeCode() == PrimitiveType.VOID)) {
            ReturnStatement rs = ast.newReturnStatement();
            rs.setExpression(mi);
            lr.insertLast(rs, null);
        }
        else {
            ExpressionStatement es = ast.newExpressionStatement(mi);
            lr.insertLast(es, null);
        }

        MethodDeclaration processed = processMethod(classDecl, m);
        writeMethod2Class(classDecl, processed);

        return methodDecl;
    }

    private Type getReturnType(MethodDeclaration md) {
        Type t = md.getReturnType2();
        if (t == null) {
            // e.g., constructor
            return md.getAST().newPrimitiveType(PrimitiveType.VOID);
        }
        else {
            return t;
        }
    }

    private void writeMethod2Class(TypeDeclaration classDecl, MethodDeclaration m) {
        ListRewrite lr = rewriter.getListRewrite(classDecl, TypeDeclaration.BODY_DECLARATIONS_PROPERTY);
        lr.insertLast(m, null);
    }

    /**
     * Find the statement to start a new method
     * @param statements    A list of statements in the current method body
     * @return  The statement from which we should start a new method
     */
    private Statement findCutPoint(List<Statement> statements) {
        if (statements.size() <= 1) {
            return null;
        }
        else {
            int bytes = 0;
            for (Statement s : statements) {
                bytes += memSize(s);
                if (bytes > maxBytes) {
                    System.out.println("Current size: " + bytes);
                    if (statements.indexOf(s) == 0) return statements.get(1);
                    else return s;
                }
            }
            return null;
        }
    }

    private boolean isMethodTooBig(MethodDeclaration method) {
        return memSize(method) > maxBytes;
    }

    private int memSize(ASTNode n) {
        return n.subtreeBytes();
    }


    /**
     * Go backward in the statement list to find {@link VariableDeclarationStatement} or
     * {@link VariableDeclarationExpression} wrapped as {@link ExpressionStatement}. There might
     * be more cases.
     *
     * Then include method parameters.
     *
     * For both {@link VariableDeclarationStatement} and {@link VariableDeclarationExpression},
     * there could be multiple {@link VariableDeclarationFragment}s.
     *
     * @param block
     * @param cp
     * @return
     */
    private HashSet<Parameter> getVarsInScope(Block block, Statement cp) {
        List<Statement> statements = block.statements();
        HashSet<Parameter> res = new HashSet<>();
        int index = statements.indexOf(cp);
        for (int i = index - 1; i >= 0 ; i--) {
            Statement s = statements.get(i);
            if (s instanceof VariableDeclarationStatement) {
                VariableDeclarationStatement vd = (VariableDeclarationStatement) s;
                Type t = vd.getType();
                for (Object f : vd.fragments()) {
                    Parameter p = new Parameter(t, ((VariableDeclarationFragment) f).getName(), ast, false);
                    res.add(p);
                }
            } else if (s instanceof ExpressionStatement && ((ExpressionStatement) s).getExpression() instanceof VariableDeclarationExpression) {
                VariableDeclarationExpression ve = (VariableDeclarationExpression)((ExpressionStatement) s).getExpression();
                Type t = ve.getType();
                for (Object f : ve.fragments()) {
                    Parameter p = new Parameter(t, ((VariableDeclarationFragment) f).getName(), ast, false);
                    res.add(p);
                }
            }
        }
        // method arguments
        return res;
    }

    private HashSet<Parameter> getParameters(MethodDeclaration m) {
        HashSet<Parameter> res = new HashSet<>();
        List<SingleVariableDeclaration> params = m.parameters();
        for (SingleVariableDeclaration p : params) {
            res.add(new Parameter(p.getType(), p.getName(), ast, p.isVarargs()));
        }
        return res;
    }

    class Parameter {
        private Type type;
        private SimpleName name;
        private AST target;
        private boolean isVarargs;
        Parameter(Type t, SimpleName n, AST target, boolean isVarargs) {
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
            if (obj instanceof Parameter) {
                Parameter that = (Parameter) obj;
                return this.type.equals(that.type) && this.name.equals(that.name);
            }
            else {
                return false;
            }
        }
    }
}
