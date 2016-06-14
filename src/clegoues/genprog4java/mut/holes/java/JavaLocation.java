package clegoues.genprog4java.mut.holes.java;

import org.eclipse.jdt.core.dom.ASTNode;

import clegoues.genprog4java.java.JavaStatement;
import clegoues.genprog4java.main.ClassInfo;
import clegoues.genprog4java.mut.Location;

public class JavaLocation extends Location<JavaStatement> {

	private ClassInfo classInfo = null; 
	private int id;
	// this is a bit tricky, because I may want to use *either* the id or this node depending and that
	// feels like a bad idea.  But let's try it.
	private ASTNode codeElement = null;
	
	public JavaLocation(JavaStatement location, Double weight) {
		super(location,weight);
		this.id = location.getStmtId();
	}
	
	public ClassInfo getClassInfo() {
		return this.classInfo;
	}
	
	public void setClassInfo(ClassInfo ci) {
		this.classInfo = ci;
	}

	@Override
	public Object clone() {
		JavaLocation clone = null;
		try {
			clone = (JavaLocation) super.clone();
		} catch (CloneNotSupportedException e) {
			// This should never happen
		}
		clone.setLocation(this.getLocation());
		clone.setWeight(this.getWeight());
		return (Object) clone;
	}

	@Override
	public int getId() {
		return this.id;
	}

	public ASTNode getCodeElement() {
		return codeElement;
	}

	public void setCodeElement(ASTNode codeElement) {
		this.codeElement = codeElement;
	}

	public String toString() {
		return ((Integer) this.getId()).toString();
	}
}