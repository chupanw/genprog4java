package clegoues.genprog4java.mut.varexc;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ITrackedNodePosition;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;

/**
 * Generate a class that contains all boolean options
 */
public class GlobalOptionsGen {
    // inclusive
    private static int startID = 0;
    // non-exclusive
    private static int endID = 0;

    private final static String prefix = "c";

    public static String getNext() {
        endID++;
        return prefix + (endID - 1);
    }

    public static void resetStartingIndex() {
        startID = endID;
    }

    public static String getCodeAsString() {
        StringBuffer buf = new StringBuffer();

        buf.append("package varexc;\n\n");
        buf.append("import edu.cmu.cs.varex.annotation.VConditional;\n\n");
        buf.append("public class GlobalOptions {\n");
        for (int i = startID; i < endID; i++) {
            buf.append("\t@VConditional public static boolean " + prefix + i + " = false;\n");
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
