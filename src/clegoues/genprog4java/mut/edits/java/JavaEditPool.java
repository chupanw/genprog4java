package clegoues.genprog4java.mut.edits.java;

import clegoues.genprog4java.main.Configuration;
import org.apache.log4j.Logger;

import java.io.*;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * A pool of edits for the JavaEditFactory to use
 *
 * <p> We rely on the {@code toString} method to distinguish {@code JavaEditOperation}s. Otherwise serialization
 * becomes difficult because {@code JavaEditOperation} transitively stores {@link org.eclipse.jdt.core.dom.ASTNode} as
 * fields, which do not implement {@code Serializable}. </p>
 *
 * <p> For this reason, we enforce that the {@code JavaEditOperation} being added to the pool overrides {@code toString}.
 * Otherwise an exception is thrown. The {@code toString} should try to incorporate distinguishing information as much
 * as possible.
 */
public class JavaEditPool implements Serializable {
    protected transient static Logger logger = Logger.getLogger(JavaEditPool.class);

    private List<JavaSavedEdit> edits = new ArrayList<>();

    public void addEdits(Set<JavaEditOperation> set, boolean canCompile) {
        for (JavaEditOperation e : set) {
            if (isToStringOverridden(e)) {
                JavaSavedEdit saved = new JavaSavedEdit(e.toString(), e.getVariantOption(), canCompile);
                edits.add(saved);
            } else {
                throw new RuntimeException("Make sure " + e.getClass().getName() + " overrides toString()");
            }
        }
    }

    public boolean isEmpty() {
        return edits.isEmpty();
    }

    public JavaSavedEdit pickOne() {
        int size = edits.size();
        int index = Configuration.randomizer.nextInt(size);
        return edits.get(index);
    }

    /**
     * We rely on the toString method to distinguish different Java edits.
     *
     * See {@link JavaEditOperation#equals(Object)}
     */
    private boolean isToStringOverridden(JavaEditOperation edit) {
        try {
            Class<?> cls = edit.getClass();
            Method m = cls.getMethod("toString", null);
            Class<?> declaringClass = m.getDeclaringClass();
            return cls == declaringClass;
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public static void serialize(JavaEditPool pool, Path path) {
        try (ObjectOutputStream output = new ObjectOutputStream(new FileOutputStream(path.toFile()))) {
            output.writeObject(pool);
            logger.info("JavaEditPool serialized to " + path.toFile().getAbsolutePath());
        } catch (IOException e) {
            logger.error(e.toString());
            throw new RuntimeException(e);
        }
    }

    public static JavaEditPool deserialize(Path path) {
        try (ObjectInputStream input = new ObjectInputStream(new FileInputStream(path.toFile()))) {
            JavaEditPool pool = (JavaEditPool) input.readObject();
            assert !pool.isEmpty() : "Empty JavaEditPool";
            return pool;
        } catch (IOException | ClassNotFoundException e) {
            logger.error(e.toString());
            throw new RuntimeException("Failed to deserialize JavaEditPool");
        }
    }
}

