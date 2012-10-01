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

import org.gradle.api.AntBuilder;
import org.gradle.api.internal.project.IsolatedAntBuilder;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.tasks.compile.AntJavaCompiler;
import org.gradle.api.internal.tasks.compile.Compiler;
import org.gradle.api.internal.tasks.compile.DefaultJavaCompilerFactory;
import org.gradle.api.internal.tasks.compile.JavaCompileSpec;
import org.gradle.api.internal.tasks.compile.daemon.CompilerDaemonFactory;
import org.gradle.api.internal.tasks.compile.daemon.CompilerDaemonManager;
import org.gradle.api.internal.tasks.compile.daemon.InProcessCompilerDaemonFactory;
import org.gradle.api.tasks.compile.CompileOptions;
import org.gradle.api.tasks.scala.ScalaCompileOptions;
import org.gradle.internal.Factory;

public class ScalaCompilerFactory {
    private final ProjectInternal project;
    private final IsolatedAntBuilder antBuilder;
    private final Factory<AntBuilder> antBuilderFactory;
    private final DefaultJavaCompilerFactory javaCompilerFactory;

    public ScalaCompilerFactory(ProjectInternal project, IsolatedAntBuilder antBuilder, Factory<AntBuilder> antBuilderFactory, DefaultJavaCompilerFactory javaCompilerFactory) {
        this.project = project;
        this.antBuilder = antBuilder;
        this.antBuilderFactory = antBuilderFactory;
        this.javaCompilerFactory = javaCompilerFactory;
    }

    public org.gradle.api.internal.tasks.compile.Compiler<ScalaJavaJointCompileSpec> create(final ScalaCompileOptions scalaOptions, final CompileOptions javaOptions) {
        if (scalaOptions.isUseAnt()) {
            Compiler<ScalaCompileSpec> scalaCompiler = new AntScalaCompiler(antBuilder);
            Compiler<JavaCompileSpec> javaCompiler = new AntJavaCompiler(antBuilderFactory);
            return new DefaultScalaJavaJointCompiler(scalaCompiler, javaCompiler);
        }

        // for now, leave it up to sbt to also do java compilation
        Compiler<ScalaJavaJointCompileSpec> scalaCompiler = null;
        try {
            scalaCompiler = (Compiler<ScalaJavaJointCompileSpec>) getClass().getClassLoader().loadClass("org.gradle.api.internal.tasks.scala.ApiScalaCompiler").newInstance();
        } catch (Exception e) {
            throw new RuntimeException("Could not load Scala compiler adapter.", e);
        }
        CompilerDaemonFactory daemonFactory;
        if (scalaOptions.isFork()) {
            daemonFactory = CompilerDaemonManager.getInstance();
        } else {
            daemonFactory = InProcessCompilerDaemonFactory.getInstance();
        }
        scalaCompiler = new DaemonScalaCompiler(project, scalaCompiler, daemonFactory);
        return new NormalizingScalaCompiler(scalaCompiler);
    }
}
