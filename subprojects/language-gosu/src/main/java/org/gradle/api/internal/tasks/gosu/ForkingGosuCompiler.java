/*
 * Copyright 2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.api.internal.tasks.gosu;

import gw.lang.gosuc.simple.GosuCompiler;
import gw.lang.gosuc.simple.ICompilerDriver;
import gw.lang.gosuc.simple.IGosuCompiler;
import gw.lang.gosuc.simple.SoutCompilerDriver;
import org.gradle.api.internal.tasks.SimpleWorkResult;
import org.gradle.api.internal.tasks.compile.CompilationFailedException;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.tasks.WorkResult;
import org.gradle.language.base.internal.compile.Compiler;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class ForkingGosuCompiler implements Compiler<GosuCompileSpec>, Serializable {
    private static final Logger LOGGER = Logging.getLogger(ForkingGosuCompiler.class);
    private final Iterable<File> gosuClasspath;
    private final File gradleUserHome;
    private final String taskName;

    public ForkingGosuCompiler(Iterable<File> gosuClasspath, File gradleUserHome) {
        this(gosuClasspath, gradleUserHome, "");
    }

    public ForkingGosuCompiler(Iterable<File> gosuClasspath, File gradleUserHome, String taskName) {
        this.gosuClasspath = gosuClasspath;
        this.gradleUserHome = gradleUserHome;
        this.taskName = taskName;
    }

    public WorkResult execute(GosuCompileSpec spec) {
        return Compiler.execute(gosuClasspath, gradleUserHome, spec, taskName);
    }

    // Similar to Scala, defer loading of Gosu runtime classes until we are
    // running in the compiler daemon and have them on the class path
    // --
    // Gosu types referenced below *must* be named in DaemonGosuCompiler's SHARED_PACKAGES field
    private static class Compiler {
        static WorkResult execute(final Iterable<File> gosuClasspath, File gradleUserHome, final GosuCompileSpec spec, String taskName) {
            LOGGER.info("Compiling with the Forking Gosu compiler.");

            ICompilerDriver driver = new SoutCompilerDriver();
            IGosuCompiler gosuc = new GosuCompiler();

            LOGGER.info("Initializing Gosu compiler");
            LOGGER.info("arg gosuClasspath is: {}", gosuClasspath);
            LOGGER.info("spec.getClasspath() is: {}", spec.getClasspath());

            final List<String> sourceRoots = new ArrayList<String>();
            for(File sourceRoot : spec.getSourceRoots()) {
                if (!sourceRoot.exists()) {
                    throw new IllegalStateException("srcdir \"" + sourceRoot.getPath() + "\" does not exist!");
                }
                sourceRoots.add(sourceRoot.getAbsolutePath());
            }
            final List<String> classpath = new ArrayList<String>();
            classpath.addAll(getJreJars()); //add the Java libs to the compiler classpath
            for(File classpathEntry : spec.getClasspath()) { //add the project's dependencies to the compiler classpath
                classpath.add(classpathEntry.getAbsolutePath());
            }
            for(File gosuClasspathEntry : gosuClasspath) { //add the Gosu runtime to the compiler classpath
                classpath.add(gosuClasspathEntry.getAbsolutePath());
            }
            gosuc.initializeGosu(sourceRoots, classpath, spec.getDestinationDir().getAbsolutePath());

            Set<File> filesToCompile = spec.getSource().getFiles();

            for(File file : filesToCompile) {
                try {
                    gosuc.compile(file, driver);
                } catch (Exception e) {
                    throw new CompilationFailedException(e);
                }

            }

            gosuc.unitializeGosu();

            boolean errorsInCompilation = printResults(taskName, (SoutCompilerDriver) driver);

            if(errorsInCompilation) {
                throw new CompilationFailedException();
            }

            return new SimpleWorkResult(true);
        }

        private static boolean printResults(String projectName, SoutCompilerDriver driver) {
            List<String> warnings = driver.getWarnings();
            boolean errorsInCompilation = driver.hasErrors();
            List<String> errors = driver.getErrors();

            List<String> warningMessages = new ArrayList<String>();
            List<String> errorMessages = new ArrayList<String>();

            for(String warning : warnings) {
                warningMessages.add("[WARNING] " + warning);
            }
            int numWarnings = warningMessages.size();

            int numErrors = 0;
            if(errorsInCompilation) {
                for(String error : errors) {
                    errorMessages.add("[ERROR] " + error);
                }
                numErrors = errorMessages.size();
            }

            boolean hasWarningsOrErrors = numWarnings > 0 || errorsInCompilation;
            StringBuilder sb;
            sb = new StringBuilder();
            sb.append(projectName == null || projectName.equals("") ? "Gosu compilation" : projectName);
            sb.append(" completed");
            if(hasWarningsOrErrors) {
                sb.append(" with ");
                if(numWarnings > 0) {
                    sb.append(numWarnings).append(" warning").append(numWarnings == 1 ? "" : 's');
                }
                if(errorsInCompilation) {
                    sb.append(numWarnings > 0 ? " and " : "");
                    sb.append(numErrors).append(" error").append(numErrors == 1 ? "" : 's');
                }
            } else {
                sb.append(" successfully.");
            }

            if(hasWarningsOrErrors) {
                //log.warn(sb.toString());
                LOGGER.error(sb.toString());
            } else {
                LOGGER.info(sb.toString());
            }

            //log at most 100 warnings or errors
            for(String warningMessage : warningMessages.subList(0, Math.min(warningMessages.size(), 100))) {
                LOGGER.warn(warningMessage);
            }
            for(String errorMessage : errorMessages.subList(0, Math.min(errorMessages.size(), 100))) {
                LOGGER.error(errorMessage);
            }

            return errorsInCompilation;
        }

        /**
         * Get all JARs from the lib directory of the System's java.home property
         * @return List of absolute paths to all JRE libraries
         */
        private static List<String> getJreJars() {
            String javaHome = System.getProperty("java.home");
            File libsDir = new File(javaHome, "/lib");

            return walkRecursively(libsDir);
        }

        /**
         * @param dir the root to walk
         * @return all .jar files in the root and its subdirectories
         */
        private static List<String> walkRecursively(File dir) {
            File[] listFile = dir.listFiles();
            List<String> retVal = new ArrayList<String>();
            if(listFile != null) {
                for(File file : listFile) {
                    if(file.isDirectory()) {
                        retVal.addAll(walkRecursively(file));
                    } else {
                        if(file.getName().endsWith(".jar")) {
                            retVal.add(file.getAbsolutePath());
                        }
                    }
                }
            }
            return retVal;
        }

    }

}
