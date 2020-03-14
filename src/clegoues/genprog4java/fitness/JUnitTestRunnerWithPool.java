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

package clegoues.genprog4java.fitness;

import org.junit.runner.JUnitCore;
import org.junit.runner.Request;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;

import java.lang.reflect.Field;
import java.util.Arrays;

/**
 * Based on {@link JUnitTestRunner}, with the addition to specify which variants (as boolean flags) to enabled
 * in the merged version of all variants.
 *
 * Each argument starting at position 1 specifies one variant that should be enabled.
 *
 * @author chupanw
 */
public class JUnitTestRunnerWithPool {
	/**
	 *     
	 * @param args junit classname to be run (usually a line in a 
	 * tests file listing positive or negative tests).  If the className also specifies a method 
	 * name (via ::), this will notice, but it will still run the full class.
	 * 
	 *  Prints results to console.
	 */
	public static void main(String[] args) {
		try {
			String clazzName = args[0].trim();
			Request testRequest = null;
			String methodName = null;
			
			System.err.println("Test Class: " + clazzName);
			if(clazzName.contains("::")) {
				String[] intermed = clazzName.split("::");
				clazzName = intermed[0];
				methodName = intermed[1];
			}
			Class<?>[] testClazz = new Class[1];
			testClazz[0] = Class.forName(clazzName);
			if(methodName == null) { 
			testRequest = Request.classes(testClazz);
			} else {
				testRequest = Request.method(testClazz[0], methodName);
			}

			// enable variants
			String[] variants = Arrays.copyOfRange(args, 1, args.length);
			enableVariants(variants);

			System.out.println("Requested #: "
					+ testRequest.getRunner().testCount());

			JUnitCore runner = new JUnitCore();
			Result r = runner.run(testRequest);

			System.out.println("[SUCCESS]:" + r.wasSuccessful());
			System.out.println("[TOTAL]:" + r.getRunCount());
			System.out.println("[FAILURE]:" + r.getFailureCount());

			for (Failure f : r.getFailures()) {
				System.out.println(f.toString());
				System.out.println(f.getTrace());
			}

			System.out.println("\n" + r.getFailures().toString());

		} catch (Exception e) {
			e.printStackTrace();
		}
		Runtime.getRuntime().exit(0);
	}

	private static void enableVariants(String[] variants) {
		try {
			Class<?> GlobalOptions = Class.forName("varexc.GlobalOptions");
			for (String v : variants) {
				Field f = GlobalOptions.getDeclaredField(v);
				f.setAccessible(true);
				f.set(null, true);
			}
		} catch (ClassNotFoundException | NoSuchFieldException | IllegalAccessException e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}
	}
}

