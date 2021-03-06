/*
 * Copyright (c) 2014-2015, 
 *  Claire Le Goues     <clegoues@cs.cmu.edu>
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 * 1. Redistributions of source code must retain the above copyright
 * notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 * notice, this list of conditions and the following disclaimer in the
 * documentation and/or other materials provided with the distribution.
 *
 * 3. The names of the contributors may not be used to endorse or promote
 * products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
 * TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
 * PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER
 * OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package clegoues.genprog4java.mut;

import clegoues.genprog4java.localization.Location;
import clegoues.genprog4java.mut.varexc.VarexCGlobal;
import org.eclipse.jdt.core.dom.*;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public interface EditOperation<R> {
	
	public EditHole getHoleCode();
	
	public Location getLocation();

	public String getVariantFolder();
	public void setVariantFolder(String f);

	public void setHoleCode(EditHole target);

	public void edit(R rewriter);

	default void mergeEdit(R rewriter, HashMap<ASTNode, List<ASTNode>> nodeStore) {}

	default void methodEdit(R rewriter, HashMap<ASTNode, List<ASTNode>> nodeStore, RewriteFinalizer finalizer) {}

	default Expression getNextFieldAccess(ASTNode parent) {
		// condition
		VarexCGlobal.addVariantName(this.getVariantFolder());
		FieldAccess fa = parent.getAST().newFieldAccess();
		fa.setExpression(fa.getAST().newQualifiedName(fa.getAST().newSimpleName("varexc"), fa.getAST().newSimpleName("GlobalOptions")));
		fa.setName(fa.getAST().newSimpleName(this.getVariantFolder()));
		return fa;
	}

	default Expression getNextFieldAccess(AST ast, String suffix) {
		// condition
		VarexCGlobal.addVariantName(this.getVariantFolder() + "_" + suffix);
		FieldAccess fa = ast.newFieldAccess();
		fa.setExpression(ast.newQualifiedName(ast.newSimpleName("varexc"), ast.newSimpleName("GlobalOptions")));
		fa.setName(ast.newSimpleName(this.getVariantFolder() + "_" + suffix));
		return fa;
	}

	default Expression getNextFieldAccessNot(ASTNode parent) {
		// condition
		VarexCGlobal.addVariantName(this.getVariantFolder());
		FieldAccess fa = parent.getAST().newFieldAccess();
		fa.setExpression(fa.getAST().newQualifiedName(fa.getAST().newSimpleName("varexc"), fa.getAST().newSimpleName("GlobalOptions")));
		fa.setName(fa.getAST().newSimpleName(this.getVariantFolder()));
		PrefixExpression cond = parent.getAST().newPrefixExpression();
		cond.setOperator(PrefixExpression.Operator.NOT);
		cond.setOperand(fa);
		return cond;
	}

	default void applyEditAndUpdateNodeStore(ASTRewrite rewriter,
                                             ASTNode astReplacement,	// AST node used for replacing
											 HashMap<ASTNode, List<ASTNode>> nodeStore, // bookkeeping editing progress
											 ASTNode originalLocationNode,
											 ASTNode newLocationNode,
											 RewriteFinalizer finalizer
	) {
		ASTNode toBeReplaced = originalLocationNode;
		if (nodeStore.containsKey(toBeReplaced)) {
			List<ASTNode> l = nodeStore.get(toBeReplaced);
			toBeReplaced = l.get(l.size() - 1);
		}
		rewriter.replace(toBeReplaced, astReplacement, null);

		updateNodeStore(nodeStore, originalLocationNode, newLocationNode);
		ArrayList<ASTNode> originChildren = getChildrenStatementsOrExpressions(originalLocationNode);
		ArrayList<ASTNode> newChildren = getChildrenStatementsOrExpressions(newLocationNode);
		for (ASTNode s : originChildren) {
		    ASTNode ss = newChildren.get(originChildren.indexOf(s));
		    assert s.toString().equals(ss.toString()) : s.toString() + " not equal to " + ss.toString();
		    updateNodeStore(nodeStore, s, ss);
		}
	}

	default void updateNodeStore(HashMap<ASTNode, List<ASTNode>> nodeStore, ASTNode key, ASTNode value) {
		if (nodeStore.containsKey(key)) {
			List<ASTNode> l = nodeStore.get(key);
			l.add(value);
		}
		else {
			List<ASTNode> l = new ArrayList<>();
			l.add(value);
			nodeStore.put(key, l);
		}
	}

	default ArrayList<ASTNode> getChildrenStatementsOrExpressions(ASTNode node) {
		ArrayList<ASTNode> res = new ArrayList<>();
		List list= node.structuralPropertiesForType();
		for (Object aList : list) {
			StructuralPropertyDescriptor curr = (StructuralPropertyDescriptor) aList;
			Object child = node.getStructuralProperty(curr);
			if (child instanceof List) {
				for (ASTNode n : (List<ASTNode>) child) {
					if (n instanceof Statement || n instanceof Expression) {
						res.add(n);
					}
					res.addAll(getChildrenStatementsOrExpressions(n));
				}
			} else if (child instanceof Statement || child instanceof Expression) {
				res.add((ASTNode) child);
				res.addAll(getChildrenStatementsOrExpressions((ASTNode) child));
			}
		}
		return res;
	}
}
