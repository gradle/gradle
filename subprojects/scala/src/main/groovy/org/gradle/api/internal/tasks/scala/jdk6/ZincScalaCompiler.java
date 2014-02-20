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

package org.gradle.api.internal.tasks.scala.jdk6;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.typesafe.zinc.*;
import org.gradle.api.GradleException;
import org.gradle.api.JavaVersion;
import org.gradle.api.internal.tasks.SimpleWorkResult;
import org.gradle.api.internal.tasks.compile.CompilationFailedException;
import org.gradle.api.internal.tasks.compile.Compiler;
import org.gradle.api.internal.tasks.compile.JavaCompilerArgumentsBuilder;
import org.gradle.api.internal.tasks.scala.ScalaCompilerArgumentsGenerator;
import org.gradle.api.internal.tasks.scala.ScalaJavaJointCompileSpec;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.tasks.WorkResult;
import org.gradle.internal.jvm.Jvm;
import scala.Option;
import xsbti.F0;

import java.io.File;
import java.io.Serializable;
import java.util.List;

public class ZincScalaCompiler implements Compiler<ScalaJavaJointCompileSpec>, Serializable {
    private static final Logger LOGGER = Logging.getLogger(ZincScalaCompiler.class);

    public WorkResult execute(ScalaJavaJointCompileSpec spec) {
        if (!JavaVersion.current().isJava6Compatible()) {
            throw new GradleException("To use the Zinc Scala compiler, Java 6 or higher is required.");
        }
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
            List<String> javacOptions = new JavaCompilerArgumentsBuilder(spec).includeClasspath(false).build();
            Inputs inputs = Inputs.create(ImmutableList.copyOf(spec.getClasspath()), ImmutableList.copyOf(spec.getSource()), spec.getDestinationDir(),
                    scalacOptions, javacOptions, spec.getScalaCompileOptions().getIncrementalOptions().getAnalysisFile(), spec.getAnalysisMap(), "mixed", getIncOptions(), true);
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

        private static IncOptions getIncOptions() {
            //The values are based on what I have found in sbt-compiler-maven-plugin.googlecode.com and zinc documentation
            //Hard to say what effect they have on the incremental build
            int transitiveStep = 3;
            double recompileAllFraction = 0.5d;
            boolean relationsDebug = false;
            boolean apiDebug = false;
            int apiDiffContextSize = 5;
            Option<File> apiDumpDirectory = Option.empty();
            boolean transactional = false;
            Option<File> backup = Option.empty();

            return new IncOptions(transitiveStep, recompileAllFraction, relationsDebug, apiDebug, apiDiffContextSize, apiDumpDirectory, transactional, backup);
        }

        static com.typesafe.zinc.Compiler createCompiler(Iterable<File> scalaClasspath, Iterable<File> zincClasspath, xsbti.Logger logger) {
            ScalaLocation scalaLocation = ScalaLocation.fromPath(Lists.newArrayList(scalaClasspath));
            SbtJars sbtJars = SbtJars.fromPath(Lists.newArrayList(zincClasspath));
            Setup setup = Setup.create(scalaLocation, sbtJars, Jvm.current().getJavaHome(), true);
            if (LOGGER.isDebugEnabled()) {
                Setup.debug(setup, logger);
            }
            return com.typesafe.zinc.Compiler.getOrCreate(setup, logger);
        }
    }

    private static class SbtLoggerAdapter implements xsbti.Logger {
        public void error(F0<String> msg) {
            LOGGER.error(msg.apply());
        }

        public void warn(F0<String> msg) {
            LOGGER.warn(msg.apply());
        }

        public void info(F0<String> msg) {
            LOGGER.info(msg.apply());
        }

        public void debug(F0<String> msg) {
            LOGGER.debug(msg.apply());
        }

        public void trace(F0<Throwable> exception) {
            LOGGER.trace(exception.apply().toString());
        }
    }
}
