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

import org.gradle.api.GradleException;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.project.IsolatedAntBuilder;
import org.gradle.api.internal.tasks.compile.JavaCompileSpec;
import org.gradle.api.internal.tasks.compile.JavaCompilerFactory;
import org.gradle.api.internal.tasks.compile.daemon.CompilerDaemonFactory;
import org.gradle.language.base.internal.compile.Compiler;
import org.gradle.language.base.internal.compile.CompilerFactory;

import java.io.File;
import java.util.Set;

public class ScalaCompilerFactory implements CompilerFactory<ScalaJavaJointCompileSpec> {
    private final IsolatedAntBuilder antBuilder;
    private final JavaCompilerFactory javaCompilerFactory;
    private final CompilerDaemonFactory compilerDaemonFactory;
    private FileCollection scalaClasspath;
    private FileCollection zincClasspath;
    private final File rootProjectDirectory;
    private final File gradleUserHomeDir;

    public ScalaCompilerFactory(File rootProjectDirectory, IsolatedAntBuilder antBuilder, JavaCompilerFactory javaCompilerFactory, CompilerDaemonFactory compilerDaemonFactory, FileCollection scalaClasspath, FileCollection zincClasspath, File gradleUserHomeDir) {
        this.rootProjectDirectory = rootProjectDirectory;
        this.antBuilder = antBuilder;
        this.javaCompilerFactory = javaCompilerFactory;
        this.compilerDaemonFactory = compilerDaemonFactory;
        this.scalaClasspath = scalaClasspath;
        this.zincClasspath = zincClasspath;
        this.gradleUserHomeDir = gradleUserHomeDir;
    }

    @SuppressWarnings("unchecked")
    public Compiler<ScalaJavaJointCompileSpec> newCompiler(ScalaJavaJointCompileSpec spec) {
        ScalaCompileOptionsInternal scalaOptions = (ScalaCompileOptionsInternal) spec.getScalaCompileOptions();
        Set<File> scalaClasspathFiles = scalaClasspath.getFiles();
        if (scalaOptions.internalIsUseAnt()) {
            Compiler<ScalaCompileSpec> scalaCompiler = new AntScalaCompiler(antBuilder, scalaClasspathFiles);
            Compiler<JavaCompileSpec> javaCompiler = javaCompilerFactory.createForJointCompilation(spec.getClass());
            return new NormalizingScalaCompiler(new DefaultScalaJavaJointCompiler(scalaCompiler, javaCompiler));
        }

        if (!scalaOptions.internalIsFork()) {
            throw new GradleException("The Zinc based Scala compiler ('scalaCompileOptions.useAnt=false') "
                    + "requires forking ('scalaCompileOptions.fork=true'), but the latter is set to 'false'.");
        }

        Set<File> zincClasspathFiles = zincClasspath.getFiles();

        // currently, we leave it to ZincScalaCompiler to also compile the Java code
        Compiler<ScalaJavaJointCompileSpec> scalaCompiler = new DaemonScalaCompiler<ScalaJavaJointCompileSpec>(rootProjectDirectory, new ZincScalaCompiler(scalaClasspathFiles, zincClasspathFiles, gradleUserHomeDir), compilerDaemonFactory, zincClasspathFiles);
        return new NormalizingScalaCompiler(scalaCompiler);
    }
}
