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
            processMethod(m, true, classDecl);
        }
    }

    private void processMethod(MethodDeclaration methodDecl, boolean isExisting, TypeDeclaration classDecl) {
        List<Statement> statements = methodDecl.getBody().statements();
        Statement cp = findCutPoint(statements);
        if (cp != null) {
            logger.info("Cutting " + classDecl.getName() + "." + methodDecl.getName());
            HashSet<Parameter> varsInScope = getVarsInScope(methodDecl, cp);

            // Construct a new method
            MethodDeclaration m = ast.newMethodDeclaration();
            String mn = "_snippet_" + methodDecl.getName().getIdentifier() + "_" + VarexCGlobal.getNextMethodID();
            m.setName(ast.newSimpleName(mn));
            m.setReturnType2((Type) ASTNode.copySubtree(ast, methodDecl.getReturnType2()));
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

            if (!isExisting) {
                ListRewrite lr2 = rewriter.getListRewrite(classDecl, TypeDeclaration.BODY_DECLARATIONS_PROPERTY);
                lr2.insertLast(methodDecl, null);
            }
            processMethod(m, false, classDecl);
        }
        else {
            if (!isExisting) {
                ListRewrite lr = rewriter.getListRewrite(classDecl, TypeDeclaration.BODY_DECLARATIONS_PROPERTY);
                lr.insertLast(methodDecl, null);
            }
        }
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
     * @param m
     * @param cp
     * @return
     */
    private HashSet<Parameter> getVarsInScope(MethodDeclaration m, Statement cp) {
        List<Statement> statements = m.getBody().statements();
        HashSet<Parameter> res = new HashSet<>();
        int index = statements.indexOf(cp);
        for (int i = index - 1; i >= 0 ; i--) {
            Statement s = statements.get(i);
            if (s instanceof VariableDeclarationStatement) {
                VariableDeclarationStatement vd = (VariableDeclarationStatement) s;
                Type t = vd.getType();
                for (Object f : vd.fragments()) {
                    Parameter p = new Parameter(t, ((VariableDeclarationFragment) f).getName(), ast);
                    res.add(p);
                }
            } else if (s instanceof ExpressionStatement && ((ExpressionStatement) s).getExpression() instanceof VariableDeclarationExpression) {
                VariableDeclarationExpression ve = (VariableDeclarationExpression)((ExpressionStatement) s).getExpression();
                Type t = ve.getType();
                for (Object f : ve.fragments()) {
                    Parameter p = new Parameter(t, ((VariableDeclarationFragment) f).getName(), ast);
                    res.add(p);
                }
            }
        }
        // method arguments
        List<SingleVariableDeclaration> params = m.parameters();
        for (SingleVariableDeclaration p : params) {
            res.add(new Parameter(p.getType(), p.getName(), ast));
        }
        return res;
    }

    class Parameter {
        private Type type;
        private SimpleName name;
        private AST target;
        Parameter(Type t, SimpleName n, AST target) {
            this.type = t;
            this.name = n;
            this.target = target;
        }

        public Type getType() {
            return (Type) ASTNode.copySubtree(target, type);
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
