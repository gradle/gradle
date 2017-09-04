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
import com.typesafe.zinc.IncOptions;
import com.typesafe.zinc.Inputs;
import org.gradle.api.internal.tasks.compile.CompilationFailedException;
import org.gradle.api.internal.tasks.compile.JavaCompilerArgumentsBuilder;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.tasks.WorkResult;
import org.gradle.api.tasks.WorkResults;
import org.gradle.internal.time.Time;
import org.gradle.internal.time.Timer;
import org.gradle.language.base.internal.compile.Compiler;
import org.gradle.util.GFileUtils;
import scala.Option;

import java.io.File;
import java.io.Serializable;
import java.util.List;

public class ZincScalaCompiler implements Compiler<ScalaJavaJointCompileSpec>, Serializable {
    private static final Logger LOGGER = Logging.getLogger(ZincScalaCompiler.class);
    private final Iterable<File> scalaClasspath;
    private Iterable<File> zincClasspath;
    private final File gradleUserHome;

    public ZincScalaCompiler(Iterable<File> scalaClasspath, Iterable<File> zincClasspath, File gradleUserHome) {
        this.scalaClasspath = scalaClasspath;
        this.zincClasspath = zincClasspath;
        this.gradleUserHome = gradleUserHome;
    }

    @Override
    public WorkResult execute(ScalaJavaJointCompileSpec spec) {
        return Compiler.execute(scalaClasspath, zincClasspath, gradleUserHome, spec);
    }

    // need to defer loading of Zinc/sbt/Scala classes until we are
    // running in the compiler daemon and have them on the class path
    private static class Compiler {
        static WorkResult execute(final Iterable<File> scalaClasspath, final Iterable<File> zincClasspath, File gradleUserHome, final ScalaJavaJointCompileSpec spec) {
            LOGGER.info("Compiling with Zinc Scala compiler.");

            final xsbti.Logger logger = new SbtLoggerAdapter();

            Timer timer = Time.startTimer();
            com.typesafe.zinc.Compiler compiler = ZincScalaCompilerFactory.createParallelSafeCompiler(scalaClasspath, zincClasspath, logger, gradleUserHome);
            LOGGER.info("Initialized Zinc Scala compiler: {}", timer.getElapsed());

            List<String> scalacOptions = new ZincScalaCompilerArgumentsGenerator().generate(spec);
            List<String> javacOptions = new JavaCompilerArgumentsBuilder(spec).includeClasspath(false).noEmptySourcePath().build();
            Inputs inputs = Inputs.create(ImmutableList.copyOf(spec.getCompileClasspath()), ImmutableList.copyOf(spec.getSource()), spec.getDestinationDir(),
                    scalacOptions, javacOptions, spec.getScalaCompileOptions().getIncrementalOptions().getAnalysisFile(), spec.getAnalysisMap(), "mixed", getIncOptions(), true);
            if (LOGGER.isDebugEnabled()) {
                Inputs.debug(inputs, logger);
            }

            if (spec.getScalaCompileOptions().isForce()) {
                GFileUtils.deleteDirectory(spec.getDestinationDir());
            }
            LOGGER.info("Prepared Zinc Scala inputs: {}", timer.getElapsed());

            try {
                compiler.compile(inputs, logger);
            } catch (xsbti.CompileFailed e) {
                throw new CompilationFailedException(e);
            }
            LOGGER.info("Completed Scala compilation: {}", timer.getElapsed());

            return WorkResults.didWork(true);
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

            // We need to use the deprecated constructor as it is compatible with certain previous versions of the Zinc compiler
            @SuppressWarnings("deprecation")
            IncOptions options = new IncOptions(transitiveStep, recompileAllFraction, relationsDebug, apiDebug, apiDiffContextSize, apiDumpDirectory, transactional, backup);
            return options;
        }

    }

    private static class SbtLoggerAdapter implements xsbti.Logger {
        @Override
        public void error(xsbti.F0<String> msg) {
            LOGGER.error(msg.apply());
        }

        @Override
        public void warn(xsbti.F0<String> msg) {
            LOGGER.warn(msg.apply());
        }

        @Override
        public void info(xsbti.F0<String> msg) {
            LOGGER.info(msg.apply());
        }

        @Override
        public void debug(xsbti.F0<String> msg) {
            LOGGER.debug(msg.apply());
        }

        @Override
        public void trace(xsbti.F0<Throwable> exception) {
            LOGGER.trace(exception.apply().toString());
        }
    }
}
