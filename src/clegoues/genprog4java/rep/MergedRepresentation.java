package clegoues.genprog4java.rep;

import clegoues.genprog4java.Search.Population;
import clegoues.genprog4java.java.ClassInfo;
import clegoues.genprog4java.mut.EditOperation;
import clegoues.genprog4java.mut.edits.java.JavaEditOperation;
import clegoues.genprog4java.mut.holes.java.JavaLocation;
import clegoues.genprog4java.mut.varexc.GlobalOptionsGen;
import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;
import org.eclipse.text.edits.MalformedTreeException;
import org.eclipse.text.edits.TextEdit;

import java.util.*;

/**
 * Merging multiple JavaRepresentations into one by:
 *
 * 1. putting all genomes into one
 * 2. Keep a list of JavaRepresentation to selectively merge variants that can compile
 */
public class MergedRepresentation extends JavaRepresentation {

    private LinkedList<JavaRepresentation> composed = new LinkedList<>();
    private HashSet<EditOperation> compilableEdits = null;

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
        collectCompilableEdits();
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
            GlobalOptionsGen.addImportGlobalOptions(cu, rewriter);

            try {
                for (JavaEditOperation edit : this.getGenome()) {
                    JavaLocation locationStatement = (JavaLocation) edit.getLocation();
                    if(compilableEdits.contains(edit) && locationStatement.getClassInfo()!=null && locationStatement.getClassInfo().getClassName().equalsIgnoreCase(filename) && locationStatement.getClassInfo().getPackage().equalsIgnoreCase(path)){
                        edit.mergeEdit(rewriter);
                    }
                }
                // todo: we need additional checking to ensure changes are at different locations

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
        retVal.add(Pair.of(new ClassInfo("GlobalOptions", "varexc"), GlobalOptionsGen.getCodeAsString()));
        return retVal;
    }

    private void collectCompilableEdits() {
        if (compilableEdits == null) {
            compilableEdits = new HashSet<>();
            for (JavaRepresentation jr : composed) {
                if (jr.canCompile) {
                    compilableEdits.addAll(jr.getGenome());
                }
            }
        }
    }
}
