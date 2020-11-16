package clegoues.genprog4java.main;

import clegoues.genprog4java.mut.edits.java.JavaEditOperation;
import clegoues.genprog4java.rep.Representation;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;

public class Solutions {

    private static FileWriter writer;
    public static long startTime = System.currentTimeMillis();
    public static long repairAttemptCount = 0L;

    static {
        try {
            writer = new FileWriter(new File("solutions.txt"), true);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void closeFile() {
        long total = System.currentTimeMillis() - startTime;
        try {
            writer.write("repair attempt total time " + total + "ms total attempts " + repairAttemptCount + "\n");
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void increaseAttemptCount() {
        repairAttemptCount += 1;
    }

    public static void markSolution(Representation rep, int generation) {
        //elapsed_time,generation,{variantOption*}
        StringBuilder line = new StringBuilder();
        long elapsedTime = System.currentTimeMillis() - startTime;
        line.append(elapsedTime).append("ms,").append(generation).append(",");
        ArrayList genome = rep.getGenome();
        line.append("{");
        for (int i = 0; i < genome.size(); i++) {
            JavaEditOperation e = (JavaEditOperation) genome.get(i);
            if (i == 0) {
                line.append(e.getVariantOption());
            } else {
                line.append(",").append(e.getVariantOption());
            }
        }
        line.append("}\n");
        try {
            writer.write(line.toString());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
