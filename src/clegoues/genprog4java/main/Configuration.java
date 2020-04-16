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

import clegoues.genprog4java.java.ClassInfo;
import clegoues.util.ConfigurationBuilder;
import clegoues.util.GlobalUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.log4j.Logger;
import org.eclipse.jdt.core.JavaCore;

import java.io.*;
import java.nio.file.FileSystems;
import java.util.ArrayList;
import java.util.Random;

import static clegoues.util.ConfigurationBuilder.*;

public class Configuration {
	protected static Logger logger = Logger.getLogger(Configuration.class);

	public static final ConfigurationBuilder.RegistryToken token =
		ConfigurationBuilder.getToken();

	//public static String sourceDir = "./";
	public static String sourceDir = ConfigurationBuilder.of( STRING )
		.withVarName( "sourceDir" )
		.withDefault( "./" )
		.withHelp( "directory containing the source files" )
		.build();
	//public static String outputDir = "./tmp/";
	public static String outputDir = ConfigurationBuilder.of( STRING )
		.withVarName( "outputDir" )
		.withDefault( "./tmp/" )
		.withHelp( "directory to contain generated files" )
		.build();
	public static String libs = ConfigurationBuilder.of( STRING )
		.withVarName( "libs" )
		.withHelp( "classpath to compile the project" )
		.build();
	//public static String sourceVersion = "1.6";
	public static String sourceVersion = ConfigurationBuilder.of( STRING )
		.withVarName( "sourceVersion" )
		.withDefault(JavaCore.VERSION_1_6)
		.withHelp( "Java version of the source code" )
		.build();
	//public static String globalExtension = ".java";
	public static String globalExtension = ConfigurationBuilder.of( STRING )
		.withVarName( "globalExtension" )
		.withDefault( ".java" )
		.withHelp( "source file extension" )
		.build();
	//public static ArrayList<ClassInfo> targetClassNames = new ArrayList<ClassInfo>();
	public static ArrayList<ClassInfo> targetClassNames =
		new ConfigurationBuilder< ArrayList< ClassInfo > >()
			.withVarName( "targetClassNames" )
			.withFlag( "targetClassName" )
			.withDefault( "" )
			.withHelp( "the class(es) to repair" )
			.withCast( new ConfigurationBuilder.LexicalCast< ArrayList< ClassInfo > >(){
				public ArrayList<ClassInfo> parse( String value ) {
					if ( ! value.isEmpty() ) {
						try {
							return getClasses( value );
						} catch (FileNotFoundException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						} catch (IOException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
					}
					return new ArrayList< ClassInfo >();
				}
				
			} )
			.build();
	//public static String javaRuntime = "";
	public static String javaRuntime = ConfigurationBuilder.of( STRING )
		.withVarName( "javaRuntime" )
		.withDefault( Runtime.getRuntime().toString() )
		.withHelp( "the java runtime to use" )
		.build();
	public static String javaVM = ConfigurationBuilder.of( STRING )
		.withVarName( "javaVM" )
		.withHelp( "path to Java" )
		.build();
	//public static String testClassPath = "";
	public static String testClassPath = ConfigurationBuilder.of( STRING )
		.withVarName( "testClassPath" )
		.withDefault( "" )
		.withHelp( "classpath to run the tests" )
		.build();
	//public static String srcClassPath = "";
	public static String srcClassPath = ConfigurationBuilder.of( STRING )
		.withVarName( "srcClassPath" )
		.withDefault( "" )
		.withHelp( "another classpath" )
		.build();
	//public static String jacocoPath = "";
	public static String jacocoPath = ConfigurationBuilder.of( STRING )
		.withVarName( "jacocoPath" )
		.withDefault( "" )
		.withHelp( "path to javaagent JAR file" )
		.build();
	public static Random randomizer;
	public static long seed = ConfigurationBuilder.of( LONG )
		.withVarName( "seed" )
		.withDefault( Long.toString( System.currentTimeMillis() ) )
		.withHelp( "seed to initialize random number generator" )
		.withCast( new ConfigurationBuilder.LexicalCast< Long >() {
			public Long parse(String value) {
				long seed = Long.valueOf( value );
				randomizer = new Random( seed );
				return seed;
			}
		} )
		.build();
	//public static boolean doSanity = true;
	public static boolean doSanity = ConfigurationBuilder.of( BOOL_ARG )
		.withVarName( "doSanity" )
		.withFlag( "sanity" )
		.withDefault( "true" )
		.withHelp( "indicates whether to run sanity check" )
		.build();
	//public static String workingDir = "./";
	public static String workingDir = ConfigurationBuilder.of( STRING )
		.withVarName( "workingDir" )
		.withDefault( "./" )
		.withHelp( "directory containing the source directory" )
		.build();
	//public static String classSourceFolder = "";
	public static String classSourceFolder = ConfigurationBuilder.of( STRING )
		.withVarName( "classSourceFolder" )
		.withDefault( "" )
		.withHelp( "directory to contain compiled classes" )
		.build();
	//public static String classTestFolder = "";
	public static String classTestFolder = ConfigurationBuilder.of( STRING )
		.withVarName( "classTestFolder" )
		.withDefault( "" )
		.withHelp( "unused" )
		.build();
	//public static String compileCommand = "";
	public static String compileCommand = ConfigurationBuilder.of( STRING )
		.withVarName( "compileCommand" )
		.withDefault( "" )
		.withHelp( "command for compiling the program" )
		.build();
	public static Boolean cleanUpVariants = ConfigurationBuilder.of( BOOL_ARG )
			.withVarName("cleanUpVariants")
			.withDefault("true")
			.withHelp("whether to clean up the output dir before execution")
			.build();
	public static Boolean debug = ConfigurationBuilder.of(BOOL_ARG)
			.withVarName("debug")
			.withDefault("false")
			.withHelp("print debug messages")
			.build();

	public enum EditMode {
		GENPROG, 			// generate fresh single edits
		EXISTING, 		// reuse existing single edits
		PRE_COMPUTE,	// pre-compute a pool of single edits
	}
	public static EditMode editMode = new ConfigurationBuilder<EditMode>()
			.withVarName("editMode")
			.withHelp("mode of generating single edits")
			.withCast(new ConfigurationBuilder.LexicalCast<EditMode>() {
				@Override
				public EditMode parse(String value) {
					String lower = value.toLowerCase();
					switch (lower) {
						case "genprog":
							return EditMode.GENPROG;
						case "existing":
							return EditMode.EXISTING;
						case "pre-compute":
						case "pre_compute":
							return EditMode.PRE_COMPUTE;
						default:
							throw new RuntimeException("Unrecognized mode: " + value);
					}
				}
			})
			.build();

	public static String editSerFile = ConfigurationBuilder.of(STRING)
			.withVarName("editSerFile")
            .withDefault("javaEditPool.ser")
			.withHelp("file name of the serialized edits")
			.build();

	private Configuration() {}

	
	//Save original target file to an outside folder if it is the first run. Or load it if it is not.

	public static void saveOrLoadTargetFiles(){
		
		String safeFolder = Configuration.outputDir  + File.separatorChar + "original" + File.separatorChar;
		
		//If there is a variant already created in the output folder then it is not the first run
		File originalFolder = FileSystems.getDefault().getPath(Configuration.outputDir, "original").toFile();
		if (originalFolder.exists()){
			
			for( ClassInfo s : Configuration.targetClassNames ){
				String srcFolder = FileSystems.getDefault().getPath(Configuration.workingDir, Configuration.sourceDir, s.getPackage()).toFile().getAbsolutePath();
				//overwrite the targetClass with the one saved before
				GlobalUtils.runCommand("cp " + safeFolder + s.pathToJavaFile() + " " + srcFolder);
			}
			
		//else 	it is the first run
		}else{
			//create safe folder and save the original target class
			saveTargetFiles();
		}
		
	}

	public static ClassInfo getClassAndPackage(String fullName) {
		String s = fullName.trim();
		int startOfClass = s.lastIndexOf('.');
		String justClass = s.substring(startOfClass + 1, s.length());
		String packagePath = s.substring(0,startOfClass).replace('.', '/');
		return new ClassInfo(justClass,packagePath );
	}
	
	public static ArrayList<ClassInfo> getClasses(String filename)
			throws IOException, FileNotFoundException {
		ArrayList<ClassInfo> returnValue = new ArrayList<ClassInfo>();
		String ext = FilenameUtils.getExtension(filename);
		if (ext.equals("txt")) {
			FileInputStream fis;
			fis = new FileInputStream(filename);
			BufferedReader br = new BufferedReader(new InputStreamReader(fis));
			String line;
			while(((line = br.readLine()) != null) && !line.isEmpty()) {
				returnValue.add(getClassAndPackage(line));
			}
			br.close();
		} else {
			returnValue.add(getClassAndPackage(filename));
		}

		return returnValue;
	}
	
	public static void saveTargetFiles() {
		
		String original = Configuration.outputDir  + File.separatorChar + "original" + File.separatorChar;

		//copy the target classes to an "original" folder; we will work from there.
		File createFile = new File(original);
		createFile.mkdirs();

		String sourceDirPath = FileSystems.getDefault().getPath(Configuration.workingDir, Configuration.sourceDir).toFile().getAbsolutePath() + File.separator;
		
		for( ClassInfo fileInfo : Configuration.targetClassNames ){
			String className = fileInfo.getClassName();
			String packagePath = fileInfo.getPackage();
			String pathToFile = "";
					
			String topLevel = sourceDirPath + className + ".java";
			
			File classFile = new File(topLevel);
			if(classFile.exists()) {
				pathToFile = topLevel;
			} else {
				pathToFile = sourceDirPath + packagePath + File.separatorChar + className + ".java";
			}
			File packagePathFile = new File(original + packagePath);
			packagePathFile.mkdirs();
			String cmd = "cp " + pathToFile + " " + original + packagePath + File.separatorChar;
			GlobalUtils.runCommand(cmd);
		}	
	}
}
