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

package clegoues.genprog4java.mut.edits.java;


import clegoues.genprog4java.java.JavaStatement;
import clegoues.genprog4java.localization.Location;
import clegoues.genprog4java.mut.EditHole;
import clegoues.genprog4java.mut.EditOperation;
import clegoues.genprog4java.mut.holes.java.JavaLocation;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;

public abstract class JavaEditOperation implements EditOperation<ASTRewrite> {

	private Location<JavaStatement> location = null;
	private EditHole holeCode = null;
	protected String variantFolder = null;

	public boolean isExpMutation() {
		return false;
	}

	protected JavaEditOperation() { } 
	
	public JavaEditOperation(JavaLocation location) {
		this.location = location;
	}
	
	public Location getLocation() {
		return this.location;
	}

	protected JavaEditOperation(JavaLocation location, EditHole source) {
		this.location = location;
		this.holeCode = source;
	}
	
	public EditHole getHoleCode() {
		return this.holeCode;
	}
	public void setHoleCode(EditHole hole) {
		holeCode = hole; 
	}

	public String getVariantFolder() {
	    if (variantFolder == null)
	    	throw new RuntimeException("The field variantFolder not initialized");
		return variantFolder;
	}

	/**
	 * Used in EXISTING mode to set the boolean value
     *
	 * In most cases, should be the same as the variantFolder, but basic mutation operators might have some
	 * suffixes, hence this helper method
     *
	 * Override by {@link AOR}, {@link ROR}, {@link UOI}
	 */
	public String getVariantOption() {
		return getVariantFolder();
	}

	/**
	 * Override by {@link AOR}, {@link ROR}, {@link UOI}
	 */
	public void setVariantOption(String optionName) {
	}

	/**
	 * Override by {@link AOR}, {@link ROR}, {@link UOI}
	 */
	public String getVariantOptionSuffix() {
		return "";
	}

	public void setVariantFolder(String f) {
	    if (variantFolder != null)
	    	throw new RuntimeException("Overwriting existing variantFolder");
	    this.variantFolder = f;
	}

	protected MethodDeclaration getMethodDeclaration(ASTNode n) {
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

	@Override
	public boolean equals(Object that) {
		return that instanceof JavaEditOperation && this.toString().equals(that.toString());
	}
}
