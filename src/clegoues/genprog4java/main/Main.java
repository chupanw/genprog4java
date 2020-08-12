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

package clegoues.genprog4java.main;

import clegoues.genprog4java.Search.*;
import clegoues.genprog4java.fitness.Fitness;
import clegoues.genprog4java.localization.DefaultLocalization;
import clegoues.genprog4java.localization.Localization;
import clegoues.genprog4java.localization.UnexpectedCoverageResultException;
import clegoues.genprog4java.mut.edits.java.JavaEditOperation;
import clegoues.genprog4java.rep.CachingRepresentation;
import clegoues.genprog4java.rep.JavaRepresentation;
import clegoues.genprog4java.rep.Representation;
import clegoues.util.ConfigurationBuilder;
import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Logger;

import java.io.File;
import java.io.IOException;

public class Main {

	protected static Logger logger = Logger.getLogger(Main.class);

	public static void main(String[] args) throws IOException,
	UnexpectedCoverageResultException {
		Solutions.startTime = System.currentTimeMillis();
		Search searchEngine = null;
		Representation baseRep = null;
		Fitness fitnessEngine = null;
		Population incomingPopulation = null;
		assert (args.length > 0);
		long startTime = System.currentTimeMillis();
		BasicConfigurator.configure();

		ConfigurationBuilder.register( Configuration.token );
		ConfigurationBuilder.register( Fitness.token );
		ConfigurationBuilder.register( CachingRepresentation.token );
		ConfigurationBuilder.register( JavaRepresentation.token );
		ConfigurationBuilder.register( Population.token );
		ConfigurationBuilder.register( Search.token );
		ConfigurationBuilder.register( OracleSearch.token );
		ConfigurationBuilder.register( RandomSingleEdit.token );
		ConfigurationBuilder.register( DefaultLocalization.token );

		ConfigurationBuilder.parseArgs( args );
		Configuration.saveOrLoadTargetFiles();
		ConfigurationBuilder.storeProperties();

        if (Configuration.editMode != Configuration.EditMode.EXISTING && Configuration.cleanUpVariants) {
            logger.info("Removing all variants in " + Configuration.outputDir);
            File outputDir = new File(Configuration.outputDir);	// outputDir and workDir are actually different
            String failed = deleteVariants(outputDir);
            if (!failed.equals("")) {
                logger.error("failed to delete " + failed);
            }
        }

		File workDir = new File(Configuration.outputDir);
		if (!workDir.exists())
			workDir.mkdir();
		logger.info("Configuration file loaded");

		fitnessEngine = new Fitness();  // Fitness must be created before rep!
		baseRep = (Representation) new JavaRepresentation();
		baseRep.load(Configuration.targetClassNames);
		Localization localization = new DefaultLocalization(baseRep);
		baseRep.setLocalization(localization);
		
		switch(Search.searchStrategy.trim()) {

		case "brute": searchEngine = new BruteForce<JavaEditOperation>(fitnessEngine);
		break;
		case "trp": searchEngine = new RandomSingleEdit<JavaEditOperation>(fitnessEngine);
		break;
		case "oracle": searchEngine = new OracleSearch<JavaEditOperation>(fitnessEngine);
		break;
		case "ga":
		default: searchEngine = new GeneticProgramming<JavaEditOperation>(fitnessEngine);
		break;
		}
		incomingPopulation = new Population<JavaEditOperation>(); 

		try {
			searchEngine.doSearch(baseRep, incomingPopulation);
		} catch (CloneNotSupportedException e) {
			e.printStackTrace();
		}
		int elapsed = getElapsedTime(startTime);
		logger.info("\nTotal elapsed time: " + elapsed + "\n");
		Solutions.closeFile();
		Runtime.getRuntime().exit(0);
	}

	private static int getElapsedTime(long start) {
		return (int) (System.currentTimeMillis() - start) / 1000;
	}

	private static String deleteVariants(File f) {
        assert f.isDirectory() : "Failed to delete " + f + "because it's not a directory";
        File[] files = f.listFiles();
        for (int i = 0; i < files.length; i++) {
            File x = files[i];
            if (x.isDirectory() && x.getName().startsWith("variant")) {
                boolean success = deleteFolder(x);
                if (!success) return x.getName();
            }
        }
        return "";
    }

	private static boolean deleteFolder(File f) {
		assert f.isDirectory() : "Failed to delete " + f + "because it's not a directory";
        File[] files = f.listFiles();
        for (int i = 0; i < files.length; i++) {
            if (!files[i].delete()) deleteFolder(files[i]);
        }
        return f.delete();
	}
}
