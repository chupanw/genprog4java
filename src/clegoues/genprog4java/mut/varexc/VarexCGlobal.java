package clegoues.genprog4java.mut.varexc;

import org.eclipse.jdt.core.dom.*;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ITrackedNodePosition;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;

import java.util.HashSet;
/**
 * Generate a class that contains all boolean options
 */
public class VarexCGlobal {
    private static int methodCnt = 0;

    private static HashSet<String> variantNames = new HashSet<>();
    public static void addVariantName(String n) {
        variantNames.add(n);
    }

    public static int getNextMethodID() {
        return methodCnt++;
    }

    public static String getCodeAsString() {
        StringBuffer buf = new StringBuffer();

        buf.append("package varexc;\n\n");
        buf.append("import edu.cmu.cs.varex.annotation.VConditional;\n\n");
        buf.append("public class GlobalOptions {\n");
        for (String n : variantNames) {
            buf.append("\t@VConditional public static boolean " + n + " = false;\n");
        }
        buf.append("}\n");
        return buf.toString();
    }

    public static void addImportGlobalOptions(CompilationUnit cu, ASTRewrite rewriter) {
        AST ast = rewriter.getAST();
        ImportDeclaration id = ast.newImportDeclaration();
        id.setName(ast.newName(new String[] {"varexc", "GlobalOptions"}));
        TypeDeclaration td = (TypeDeclaration) cu.types().get(0);
        ITrackedNodePosition tdLocation = rewriter.track(td);
        ListRewrite lrw = rewriter.getListRewrite(cu, CompilationUnit.IMPORTS_PROPERTY);
        lrw.insertLast(id, null);
    }
}
