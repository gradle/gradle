/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.internal.tasks.scala;

import com.google.common.collect.ImmutableList;
import com.typesafe.zinc.Inputs;
import com.typesafe.zinc.Setup;

import org.gradle.api.InvalidUserDataException;
import org.gradle.api.internal.tasks.compile.CompilationFailedException;
import org.gradle.api.internal.tasks.compile.Compiler;
import org.gradle.api.internal.tasks.compile.SimpleWorkResult;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.tasks.WorkResult;
import org.gradle.internal.jvm.Jvm;
import xsbti.F0;

import java.io.File;
import java.io.Serializable;
import java.util.Collections;
import java.util.List;

public class ZincScalaCompiler implements Compiler<ScalaJavaJointCompileSpec>, Serializable {
    private static final Logger LOGGER = Logging.getLogger(ZincScalaCompiler.class);

    public WorkResult execute(ScalaJavaJointCompileSpec spec) {
        return Compiler.execute(spec);
    }

    // need to defer loading of Zinc/sbt/Scala classes until we are
    // running in the compiler daemon and have them on the class path
    private static class Compiler {
        static WorkResult execute(ScalaJavaJointCompileSpec spec) {
            LOGGER.info("Compiling with Zinc Scala compiler.");

            xsbti.Logger logger = new SbtLoggerAdapter();

            com.typesafe.zinc.Compiler compiler = createCompiler(spec.getScalaClasspath(), spec.getZincClasspath(), logger);
            List<String> scalacOptions = new ScalaCompilerArgumentsGenerator().generate(spec);
            List<String> javacOptions = Collections.emptyList(); // TODO
            Inputs inputs = Inputs.create(ImmutableList.copyOf(spec.getClasspath()), ImmutableList.copyOf(spec.getSource()), spec.getDestinationDir(),
                    scalacOptions, javacOptions, spec.getScalaCompileOptions().getCompilerCacheFile(), spec.getCompilerCacheMap(), "mixed");
            if (LOGGER.isDebugEnabled()) {
                Inputs.debug(inputs, logger);
            }

            try {
                compiler.compile(inputs, logger);
            } catch (xsbti.CompileFailed e) {
                throw new CompilationFailedException(e);
            }

            return new SimpleWorkResult(true);
        }

        static File findJar(Iterable<File> classpath, String pattern) {
            for (File file : classpath) {
                if (file.getName().matches(pattern)) {
                    return file;
                }
            }
            throw new InvalidUserDataException(String.format(
                    "Cannot find a Jar matching pattern '%s' on Scala classpath %s. Please make sure it is available.",
                    pattern, ImmutableList.copyOf(classpath)));
        }

        static com.typesafe.zinc.Compiler createCompiler(Iterable<File> scalaClasspath, Iterable<File> zincClasspath, xsbti.Logger logger) {
            File compilerJar = findJar(scalaClasspath, "scala-compiler(-.*)?.jar");
            File libraryJar = findJar(scalaClasspath, "scala-library(-.*)?.jar");
            List<File> extraJars = Collections.emptyList(); // TODO
            File sbtInterfaceJar = findJar(zincClasspath, "sbt-interface(-.*)?.jar");
            File compilerInterfaceSourcesJar = findJar(zincClasspath, "compiler-interface(-.*)?-sources.jar");
            Setup setup = Setup.create(compilerJar, libraryJar, extraJars, sbtInterfaceJar, compilerInterfaceSourcesJar, Jvm.current().getJavaHome());
            if (LOGGER.isDebugEnabled()) {
                Setup.debug(setup, logger);
            }
            return com.typesafe.zinc.Compiler.create(setup, logger);
        }
    }

    // TODO: change log levels
    private static class SbtLoggerAdapter implements xsbti.Logger {
        public void error(F0<String> msg) {
            LOGGER.lifecycle(msg.apply());
        }

        public void warn(F0<String> msg) {
            LOGGER.lifecycle(msg.apply());
        }

        public void info(F0<String> msg) {
            LOGGER.lifecycle(msg.apply());
        }

        public void debug(F0<String> msg) {
            LOGGER.lifecycle(msg.apply());
        }

        public void trace(F0<Throwable> exception) {
            LOGGER.lifecycle(exception.apply().toString());
        }
    }
}
