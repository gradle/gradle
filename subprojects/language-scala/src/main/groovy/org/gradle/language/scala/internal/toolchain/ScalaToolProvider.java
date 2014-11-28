/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.language.scala.internal.toolchain;

import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.internal.artifacts.dsl.dependencies.ProjectFinder;
import org.gradle.api.internal.project.IsolatedAntBuilder;
import org.gradle.api.internal.tasks.compile.JavaCompilerFactory;
import org.gradle.api.internal.tasks.compile.daemon.CompilerDaemonManager;
import org.gradle.api.internal.tasks.scala.ScalaCompilerFactory;
import org.gradle.api.internal.tasks.scala.ScalaJavaJointCompileSpec;
import org.gradle.language.base.internal.compile.CompileSpec;
import org.gradle.language.base.internal.compile.Compiler;
import org.gradle.language.scala.plugins.ScalaLanguagePlugin;
import org.gradle.platform.base.internal.toolchain.ToolProvider;
import org.gradle.util.TreeVisitor;

import java.io.File;

class ScalaToolProvider implements ToolProvider {

    private ProjectFinder projectFinder;
    private final CompilerDaemonManager compilerDaemonManager;
    private final JavaCompilerFactory javaCompilerFactory;
    private IsolatedAntBuilder antBuilder;
    private final DependencyHandler dependencyHandler;
    private final ConfigurationContainer configurationContainer;
    private String scalaVersion;

    public ScalaToolProvider(ProjectFinder projectFinder, CompilerDaemonManager compilerDaemonManager, JavaCompilerFactory javaCompilerFactory, IsolatedAntBuilder isolatedAntBuilder, DependencyHandler dependencyHandler, ConfigurationContainer configurationContainer, String scalaVersion) {
        this.projectFinder = projectFinder;
        this.compilerDaemonManager = compilerDaemonManager;
        this.javaCompilerFactory = javaCompilerFactory;
        this.antBuilder = isolatedAntBuilder;
        this.dependencyHandler = dependencyHandler;
        this.configurationContainer = configurationContainer;
        this.scalaVersion = scalaVersion;
    }

    public <T extends CompileSpec> org.gradle.language.base.internal.compile.Compiler<T> newCompiler(T spec) {
        if (spec instanceof ScalaJavaJointCompileSpec) {
            ScalaJavaJointCompileSpec scalaJavaJointCompileSpec = (ScalaJavaJointCompileSpec) spec;
            Configuration scalaClasspath = resolveDependency(String.format("org.scala-lang:scala-compiler:%s", scalaVersion));
            Configuration zincClasspath = resolveDependency(String.format("com.typesafe.zinc:zinc:%s", ScalaLanguagePlugin.DEFAULT_ZINC_VERSION));
            File projectDir = projectFinder.getProject(":").getProjectDir();
            ScalaCompilerFactory scalaCompilerFactory = new ScalaCompilerFactory(projectDir, antBuilder, javaCompilerFactory, compilerDaemonManager, scalaClasspath, zincClasspath);
            @SuppressWarnings("unchecked") org.gradle.language.base.internal.compile.Compiler<T> delegatingCompiler = (Compiler<T>) scalaCompilerFactory.newCompiler(scalaJavaJointCompileSpec);
            return delegatingCompiler;
        }
        throw new IllegalArgumentException(String.format("Cannot create Compiler for unsupported CompileSpec type '%s'", spec.getClass().getSimpleName()));
    }

    private Configuration resolveDependency(Object dependencyNotation) {
        Dependency dependency = dependencyHandler.create(dependencyNotation);
        Configuration configuration = configurationContainer.detachedConfiguration(dependency);
        return configuration;
    }

    public boolean isAvailable() {
        return true;
    }

    public void explain(TreeVisitor<? super String> visitor) {

    }
}
