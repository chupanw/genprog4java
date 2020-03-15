package clegoues.genprog4java.mut.edits.java;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;

import java.io.Serializable;
import java.util.HashMap;

public class JavaSavedEdit extends JavaEditOperation implements Serializable {
    public String editString;
    public boolean canCompile;
    public String variantOption;

    JavaSavedEdit(String editString, String variantOption, boolean canCompile) {
        this.editString = editString;
        this.variantOption = variantOption;
        this.canCompile = canCompile;
    }

    @Override
    public String getVariantFolder() {
        if (variantOption == null)
            throw new RuntimeException("The filed variantOption not initialized");
        return this.variantOption;
    }

    @Override
    public int hashCode() {
        return (editString + variantOption + canCompile).hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof JavaSavedEdit) {
            JavaSavedEdit that = (JavaSavedEdit) obj;
            return that.variantOption.equals(this.variantOption)
                    && that.editString.equals(this.editString)
                    && that.canCompile == this.canCompile;
        }
        return false;
    }

    @Override
    public void edit(ASTRewrite rewriter) {
        throw new RuntimeException("edit() should not be called on SavedEdit");
    }

    @Override
    public void mergeEdit(ASTRewrite rewriter, HashMap<ASTNode, ASTNode> nodeStore) {
        throw new RuntimeException("mergeEdit() should not be called on SavedEdit");
    }

    @Override
    public String toString() {
        return "Saved" + editString;
    }
}
