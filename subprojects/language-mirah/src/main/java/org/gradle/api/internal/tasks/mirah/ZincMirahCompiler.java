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

package org.gradle.api.internal.tasks.mirah;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import org.gradle.api.internal.tasks.SimpleWorkResult;
import org.gradle.api.internal.tasks.compile.CompilationFailedException;
import org.gradle.language.base.internal.compile.Compiler;
import org.gradle.api.internal.tasks.compile.JavaCompilerArgumentsBuilder;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.tasks.WorkResult;
import org.gradle.internal.jvm.Jvm;
// import mirah.Option;

import java.io.File;
import java.io.Serializable;
import java.util.List;

public class ZincMirahCompiler implements Compiler<MirahJavaJointCompileSpec>, Serializable {
    private static final Logger LOGGER = Logging.getLogger(ZincMirahCompiler.class);
    private final Iterable<File> mirahClasspath;
    private Iterable<File> zincClasspath;

    public ZincMirahCompiler(Iterable<File> mirahClasspath, Iterable<File> zincClasspath) {
        this.mirahClasspath = mirahClasspath;
        this.zincClasspath = zincClasspath;
    }

    public WorkResult execute(MirahJavaJointCompileSpec spec) {
        return Compiler.execute(mirahClasspath, zincClasspath, spec);
    }

    // need to defer loading of Zinc/sbt/Mirah classes until we are
    // running in the compiler daemon and have them on the class path
    private static class Compiler {
        static WorkResult execute(Iterable<File> mirahClasspath, Iterable<File> zincClasspath, MirahJavaJointCompileSpec spec) {
            LOGGER.info("Compiling with Zinc Mirah compiler.");

            xsbti.Logger logger = new SbtLoggerAdapter();

            com.typesafe.zinc.Compiler compiler = createCompiler(mirahClasspath, zincClasspath, logger);
            List<String> mirahcOptions = new ZincMirahCompilerArgumentsGenerator().generate(spec);
            List<String> javacOptions = new JavaCompilerArgumentsBuilder(spec).includeClasspath(false).build();
            Inputs inputs = Inputs.create(ImmutableList.copyOf(spec.getClasspath()), ImmutableList.copyOf(spec.getSource()), spec.getDestinationDir(),
                    mirahcOptions, javacOptions, spec.getMirahCompileOptions().getIncrementalOptions().getAnalysisFile(), spec.getAnalysisMap(), "mixed", true);
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

        static com.typesafe.zinc.Compiler createCompiler(Iterable<File> mirahClasspath, Iterable<File> zincClasspath, xsbti.Logger logger) {
            MirahLocation mirahLocation = MirahLocation.fromPath(Lists.newArrayList(mirahClasspath));
            SbtJars sbtJars = SbtJars.fromPath(Lists.newArrayList(zincClasspath));
            Setup setup = Setup.create(mirahLocation, sbtJars, Jvm.current().getJavaHome(), true);
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
