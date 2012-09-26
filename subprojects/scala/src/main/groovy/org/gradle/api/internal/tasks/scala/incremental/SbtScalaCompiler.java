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

package org.gradle.api.internal.tasks.scala.incremental;

import com.google.common.collect.ImmutableList;
import com.typesafe.zinc.Inputs;
import com.typesafe.zinc.Setup;

import org.gradle.api.InvalidUserDataException;
import org.gradle.api.internal.tasks.compile.CompilationFailedException;
import org.gradle.api.internal.tasks.compile.Compiler;
import org.gradle.api.internal.tasks.compile.SimpleWorkResult;
import org.gradle.api.internal.tasks.scala.ScalaCompileSpec;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.tasks.WorkResult;
import org.gradle.internal.jvm.Jvm;

import java.io.File;
import java.util.Collections;
import java.util.List;

public class SbtScalaCompiler implements Compiler<ScalaCompileSpec> {
    private static final Logger LOGGER = Logging.getLogger(SbtScalaCompiler.class);

    public WorkResult execute(ScalaCompileSpec spec) {
        xsbti.Logger logger = new SbtLoggerAdapter(LOGGER);

        com.typesafe.zinc.Compiler compiler = createCompiler(spec.getScalaClasspath(), logger);
        List<String> scalacOptions = Collections.emptyList(); // TODO
        List<String> javacOptions = Collections.emptyList(); // TODO
        Inputs inputs = Inputs.create(ImmutableList.copyOf(spec.getClasspath()), ImmutableList.copyOf(spec.getSource()), spec.getDestinationDir(),
                scalacOptions, javacOptions, spec.getScalaCompileOptions().getIncrementalCacheFile(), spec.getIncrementalCacheMap(), "mixed");
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

    private File findScalaJar(Iterable<File> classpath, String pattern) {
        for (File file : classpath) {
            if (file.getName().matches(pattern)) {
                return file;
            }
        }
        throw new InvalidUserDataException(String.format(
                "Cannot find a Jar matching pattern '%s' on Scala classpath %s. Please make sure it is available.",
                pattern, ImmutableList.copyOf(classpath)));
    }

    private com.typesafe.zinc.Compiler createCompiler(Iterable<File> scalaClasspath, xsbti.Logger logger) {
        File compilerJar = findScalaJar(scalaClasspath, "scala-compiler(-.*)?.jar");
        File libraryJar = findScalaJar(scalaClasspath, "scala-library(-.*)?.jar");
        List<File> extraJars = Collections.emptyList(); // TODO
        File sbtInterfaceJar = findScalaJar(scalaClasspath, "sbt-interface(-.*)?.jar");
        File compilerInterfaceSourcesJar = findScalaJar(scalaClasspath, "compiler-interface(-.*)?-sources.jar");
        Setup setup = Setup.create(compilerJar, libraryJar, extraJars, sbtInterfaceJar, compilerInterfaceSourcesJar, Jvm.current().getJavaHome());
        if (LOGGER.isDebugEnabled()) {
            Setup.debug(setup, logger);
        }
        return com.typesafe.zinc.Compiler.create(setup, logger);
    }
}
