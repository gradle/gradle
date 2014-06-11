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
import org.gradle.api.internal.project.IsolatedAntBuilder;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.tasks.compile.JavaCompileSpec;
import org.gradle.api.internal.tasks.compile.JavaCompilerFactory;
import org.gradle.api.internal.tasks.compile.daemon.CompilerDaemonFactory;
import org.gradle.api.tasks.scala.ScalaCompileOptions;
import org.gradle.language.base.internal.compile.Compiler;
import org.gradle.language.base.internal.compile.CompilerFactory;

public class ScalaCompilerFactory implements CompilerFactory<ScalaJavaJointCompileSpec> {
    private final ProjectInternal project;
    private final IsolatedAntBuilder antBuilder;
    private final JavaCompilerFactory javaCompilerFactory;
    private final CompilerDaemonFactory compilerDaemonFactory;

    public ScalaCompilerFactory(ProjectInternal project, IsolatedAntBuilder antBuilder, JavaCompilerFactory javaCompilerFactory, CompilerDaemonFactory compilerDaemonFactory) {
        this.project = project;
        this.antBuilder = antBuilder;
        this.javaCompilerFactory = javaCompilerFactory;
        this.compilerDaemonFactory = compilerDaemonFactory;
    }

    public Compiler<ScalaJavaJointCompileSpec> newCompiler(ScalaJavaJointCompileSpec spec) {
        ScalaCompileOptions scalaOptions = spec.getScalaCompileOptions();
        if (scalaOptions.isUseAnt()) {
            Compiler<ScalaCompileSpec> scalaCompiler = new AntScalaCompiler(antBuilder);
            Compiler<JavaCompileSpec> javaCompiler = javaCompilerFactory.createForJointCompilation(spec.getCompileOptions());
            return new NormalizingScalaCompiler(new DefaultScalaJavaJointCompiler(scalaCompiler, javaCompiler));
        }

        if (!scalaOptions.isFork()) {
            throw new GradleException("The Zinc based Scala compiler ('scalaCompileOptions.useAnt=false') "
                    + "requires forking ('scalaCompileOptions.fork=true'), but the latter is set to 'false'.");
        }

        // currently, we leave it to ZincScalaCompiler to also compile the Java code
        Compiler<ScalaJavaJointCompileSpec> scalaCompiler;
        try {
            scalaCompiler = (Compiler<ScalaJavaJointCompileSpec>) getClass().getClassLoader()
                    .loadClass("org.gradle.api.internal.tasks.scala.jdk6.ZincScalaCompiler").newInstance();
        } catch (Exception e) {
            throw new RuntimeException("Internal error: Failed to load org.gradle.api.internal.tasks.scala.jdk6.ZincScalaCompiler", e);
        }

        scalaCompiler = new DaemonScalaCompiler(project.getRootProject().getProjectDir(), scalaCompiler, compilerDaemonFactory);
        return new NormalizingScalaCompiler(scalaCompiler);
    }
}
