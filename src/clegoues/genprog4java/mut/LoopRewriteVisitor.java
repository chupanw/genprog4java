package clegoues.genprog4java.mut;

import org.eclipse.jdt.core.dom.*;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;

import java.util.*;

public class LoopRewriteVisitor extends ASTVisitor {

    Set<MyParameter> parameters;
    Map<MyParameter, Expression> additionalVars;
    ASTRewrite rewriter;
    TypeDeclaration classDecl;
    MethodDeclaration methodDecl;

    Set<String> varNames;
    Map<String, String> var2field;
    String isBreakFieldName;
    String isReturnFieldName;
    String returnValueFieldName;
    AST ast;
    Random rand = new Random();

    public LoopRewriteVisitor(Set<MyParameter> parameters,
                              Map<MyParameter, Expression> additionalVars,
                              ASTRewrite rewriter,
                              TypeDeclaration classDecl,
                              MethodDeclaration methodDecl
    ) {
        this.parameters = parameters;
        this.additionalVars = additionalVars;
        this.rewriter = rewriter;
        this.classDecl = classDecl;
        this.methodDecl = methodDecl;

        this.varNames = new HashSet<>();
        for (MyParameter p : this.parameters) {
            varNames.add(p.getName().getIdentifier());
        }
        for (MyParameter p : this.additionalVars.keySet()) {
            varNames.add(p.getName().getIdentifier());
        }
        this.var2field = new HashMap<>();
        this.isBreakFieldName = "isBreak" + "_" + genRandomString();
        this.isReturnFieldName = "isReturn" + "_" + genRandomString();
        this.ast = this.rewriter.getAST();

        if (hasReturnValue()) {
            returnValueFieldName = "returnValue" + "_" + genRandomString();
        }
        else {
            returnValueFieldName = null;
        }
    }

    String genRandomString() {
        return Integer.toString(Math.abs(rand.nextInt()));
    }

    public List<Statement> getRewrittenLoopBody(Block body) {
        ListRewrite lr = rewriter.getListRewrite(body, Block.STATEMENTS_PROPERTY);
        List<Statement> rewritten = new LinkedList<>();
        for (Statement s : (List<Statement>) lr.getRewrittenList()) {
            rewritten.add((Statement) ASTNode.copySubtree(ast, s));
        }

        FieldAccess isReturnFA = ast.newFieldAccess();
        isReturnFA.setExpression(ast.newThisExpression());
        isReturnFA.setName(ast.newSimpleName(isReturnFieldName));
        Assignment isReturnAssign = ast.newAssignment();
        isReturnAssign.setLeftHandSide(isReturnFA);
        isReturnAssign.setRightHandSide(ast.newBooleanLiteral(false));
        rewritten.add(0, ast.newExpressionStatement(isReturnAssign));

        FieldAccess isBreakFA = ast.newFieldAccess();
        isBreakFA.setExpression(ast.newThisExpression());
        isBreakFA.setName(ast.newSimpleName(isBreakFieldName));
        Assignment isBreakAssign = ast.newAssignment();
        isBreakAssign.setLeftHandSide(isBreakFA);
        isBreakAssign.setRightHandSide(ast.newBooleanLiteral(false));
        rewritten.add(0, ast.newExpressionStatement(isBreakAssign));

        return rewritten;
    }

    public List<Statement> getBeforeLoopSeq() {
        List<Statement> beforeLoopSeq = new ArrayList<>();
        for (MyParameter p : parameters) {
            FieldAccess fa = ast.newFieldAccess();
            fa.setExpression(ast.newThisExpression());
            fa.setName(ast.newSimpleName(var2field.get(p.getName().getIdentifier())));
            Assignment assign = ast.newAssignment();
            assign.setLeftHandSide(fa);
            assign.setRightHandSide(p.getName());
            Statement assignStmt = ast.newExpressionStatement(assign);
            beforeLoopSeq.add(assignStmt);
        }

        for (Map.Entry<MyParameter, Expression> entry : additionalVars.entrySet()) {
            FieldAccess fa = ast.newFieldAccess();
            fa.setExpression(ast.newThisExpression());
            fa.setName(ast.newSimpleName(var2field.get(entry.getKey().getName().getIdentifier())));
            Assignment assign = ast.newAssignment();
            assign.setLeftHandSide(fa);
            assign.setRightHandSide((Expression) ASTNode.copySubtree(ast, entry.getValue()));
            beforeLoopSeq.add(ast.newExpressionStatement(assign));
        }

        return beforeLoopSeq;
    }

    public List<Statement> getAfterLoopSeq() {
        List<Statement> afterLoopSeq = new ArrayList<>();
        for (MyParameter p : parameters) {
            FieldAccess fa = ast.newFieldAccess();
            fa.setExpression(ast.newThisExpression());
            fa.setName(ast.newSimpleName(var2field.get(p.getName().getIdentifier())));
            Assignment assign = ast.newAssignment();
            assign.setLeftHandSide(p.getName());
            assign.setRightHandSide(fa);
            Statement assignStmt = ast.newExpressionStatement(assign);
            afterLoopSeq.add(assignStmt);
        }
        return afterLoopSeq;
    }

    private boolean hasSwitchStmtParent(ASTNode node) {
        ASTNode parent = node.getParent();
        if (parent == null) {
            return false;
        } else {
            if (parent instanceof SwitchStatement)
                return true;
            else
                return hasSwitchStmtParent(parent);
        }
    }

    @Override
    public void endVisit(BreakStatement node) {
        if (hasSwitchStmtParent(node))
            return;

        Block breakBlock = ast.newBlock();

        Assignment a = ast.newAssignment();
        FieldAccess fa = ast.newFieldAccess();
        fa.setExpression(ast.newThisExpression());
        fa.setName(ast.newSimpleName(isBreakFieldName));
        a.setLeftHandSide(fa);
        a.setRightHandSide(ast.newBooleanLiteral(true));
        breakBlock.statements().add(ast.newExpressionStatement(a));

        ReturnStatement ret = ast.newReturnStatement();
        if (hasReturnValue()) {
            FieldAccess retFA = ast.newFieldAccess();
            retFA.setExpression(ast.newThisExpression());
            retFA.setName(ast.newSimpleName(returnValueFieldName));
            ret.setExpression(retFA);
        }
        breakBlock.statements().add(ret);

        replace(node, breakBlock);
    }

    @Override
    public void endVisit(ContinueStatement node) {
        ReturnStatement ret = ast.newReturnStatement();
        if (hasReturnValue()) {
            FieldAccess retFA = ast.newFieldAccess();
            retFA.setExpression(ast.newThisExpression());
            retFA.setName(ast.newSimpleName(returnValueFieldName));
            ret.setExpression(retFA);
        }
        replace(node, ret);
    }

    @Override
    public void endVisit(ReturnStatement node) {
        Block returnBlock = ast.newBlock();

        FieldAccess isReturnFA = ast.newFieldAccess();
        isReturnFA.setExpression(ast.newThisExpression());
        isReturnFA.setName(ast.newSimpleName(isReturnFieldName));
        Assignment assignIsReturn = ast.newAssignment();
        assignIsReturn.setLeftHandSide(isReturnFA);
        assignIsReturn.setRightHandSide(ast.newBooleanLiteral(true));
        returnBlock.statements().add(ast.newExpressionStatement(assignIsReturn));

        if (hasReturnValue()) {
            FieldAccess returnValFA = ast.newFieldAccess();
            returnValFA.setExpression(ast.newThisExpression());
            returnValFA.setName(ast.newSimpleName(returnValueFieldName));
            Assignment assignReturnVal = ast.newAssignment();
            assignReturnVal.setLeftHandSide(returnValFA);
            assignReturnVal.setRightHandSide((Expression) ASTNode.copySubtree(ast, node.getExpression()));
            returnBlock.statements().add(ast.newExpressionStatement(assignReturnVal));

            ReturnStatement retStmt = ast.newReturnStatement();
            retStmt.setExpression((FieldAccess) ASTNode.copySubtree(ast, returnValFA));
            returnBlock.statements().add(retStmt);
        }
        else {
            returnBlock.statements().add(ASTNode.copySubtree(ast, node));
        }

        replace(node, returnBlock);
    }

    private void replace(ASTNode node, Statement replacement) {
        ASTNode parent = node.getParent();
        StructuralPropertyDescriptor property = node.getLocationInParent();
        if (property instanceof ChildListPropertyDescriptor) {
            if (parent instanceof Block) {
                List<Statement> statements = ((Block) parent).statements();
                statements.replaceAll(x -> {
                    if (x.equals(node)) return replacement; else return x;
                });
            }
            else if (parent instanceof SwitchStatement) {
                List<Statement> statements = ((SwitchStatement) parent).statements();
                statements.replaceAll(x -> {
                    if (x.equals(node)) return replacement; else return x;
                });
            }
            else {
                throw new UnsupportedOperationException("Need to implement child substitution for " + parent.getClass());
            }
        }
        else {
            parent.setStructuralProperty(property, replacement);
        }
    }

    boolean hasReturnValue() {
        Type retType = methodDecl.getReturnType2();
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

    @Override
    public boolean visit(SimpleName node) {
        if (varNames.contains(node.getIdentifier()) && !node.isDeclaration()) {
            FieldAccess fa = ast.newFieldAccess();
            fa.setExpression(ast.newThisExpression());
            String fieldName;
            if (var2field.containsKey(node.getIdentifier())) {
                fieldName = var2field.get(node.getIdentifier());
            }
            else {
                fieldName = node.getIdentifier() + "_" + genRandomString();
                var2field.put(node.getIdentifier(), fieldName);
            }
            fa.setName(ast.newSimpleName(fieldName));
            ASTNode parent = node.getParent();
            StructuralPropertyDescriptor property = node.getLocationInParent();
            if (property instanceof ChildListPropertyDescriptor) {
                if (parent instanceof MethodInvocation) {
                    List<Expression> args = ((MethodInvocation) parent).arguments();
                    args.replaceAll(x -> {
                        if (x instanceof SimpleName && ((SimpleName) x).getIdentifier().equals(node.getIdentifier()))
                            return fa;
                        else
                            return x;
                    });
                }
                else {
                    throw new UnsupportedOperationException("Need to implement child substitution for " + parent.getClass());
                }
            }
            else {
                parent.setStructuralProperty(property, fa);
            }
        }
        return true;
    }

    public void writeFields2Class() {
        ListRewrite lr = rewriter.getListRewrite(classDecl, TypeDeclaration.BODY_DECLARATIONS_PROPERTY);
        for (MyParameter p : parameters) {
            FieldDeclaration fd = genField(p.getType(), p.getName().getIdentifier(), true);
            lr.insertLast(fd, null);
        }
        for (MyParameter p : additionalVars.keySet()) {
            FieldDeclaration fd = genField(p.getType(), p.getName().getIdentifier(), true);
            lr.insertLast(fd, null);
        }
        // additional fields for keeping the states of looping
        FieldDeclaration isBreak = genField(ast.newPrimitiveType(PrimitiveType.BOOLEAN), isBreakFieldName, false);
        lr.insertLast(isBreak, null);
        FieldDeclaration isReturn = genField(ast.newPrimitiveType(PrimitiveType.BOOLEAN), isReturnFieldName, false);
        lr.insertLast(isReturn, null);
        if (hasReturnValue()) {
            FieldDeclaration returnValue = genField(methodDecl.getReturnType2(), returnValueFieldName, false);
            lr.insertLast(returnValue, null);
        }
    }

    FieldDeclaration genField(Type t, String name, boolean shouldLookup) {
        VariableDeclarationFragment vdf = ast.newVariableDeclarationFragment();
        if (shouldLookup)
            vdf.setName(ast.newSimpleName(var2field.get(name)));
        else
            vdf.setName(ast.newSimpleName(name));
        FieldDeclaration fd = ast.newFieldDeclaration(vdf);
        fd.setType((Type) ASTNode.copySubtree(ast, t));
        return fd;
    }

    FieldAccess genFieldAccess(String fieldName) {
        FieldAccess fa = ast.newFieldAccess();
        fa.setExpression(ast.newThisExpression());
        fa.setName(ast.newSimpleName(fieldName));
        return fa;
    }

}
