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

import org.gradle.api.JavaVersion;
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
import org.gradle.api.plugins.scala.ScalaBasePlugin;
import org.gradle.language.base.internal.compile.CompileSpec;
import org.gradle.language.base.internal.compile.Compiler;
import org.gradle.language.scala.platform.ScalaPlatform;
import org.gradle.platform.base.internal.toolchain.ToolProvider;
import org.gradle.util.TreeVisitor;

import java.io.File;

public class DefaultScalaToolChain implements ScalaToolChainInternal {
    private ProjectFinder projectFinder;
    private CompilerDaemonManager compilerDaemonManager;
    private JavaCompilerFactory javaCompilerFactory;
    private final IsolatedAntBuilder isolatedAntBuilder;
    private final ConfigurationContainer configurationContainer;
    private final DependencyHandler dependencyHandler;
    private final JavaVersion javaVersion;

    public DefaultScalaToolChain(ProjectFinder projectFinder, CompilerDaemonManager compilerDaemonManager, JavaCompilerFactory javaCompilerFactory, IsolatedAntBuilder isolatedAntBuilder, ConfigurationContainer configurationContainer, DependencyHandler dependencyHandler) {
        this.projectFinder = projectFinder;
        this.compilerDaemonManager = compilerDaemonManager;
        this.javaCompilerFactory = javaCompilerFactory;
        this.isolatedAntBuilder = isolatedAntBuilder;
        this.configurationContainer = configurationContainer;
        this.dependencyHandler = dependencyHandler;
        this.javaVersion = JavaVersion.current();
    }

    public String getName() {
        return String.format("Scala Toolchain");
    }

    public String getDisplayName() {
        return String.format("Scala Toolchain (JDK %s (%s))", javaVersion.getMajorVersion(), javaVersion);
    }

    public ToolProvider select(ScalaPlatform targetPlatform) {
        return new ScalaToolProvider(projectFinder, compilerDaemonManager, javaCompilerFactory, isolatedAntBuilder, dependencyHandler, configurationContainer, targetPlatform.getScalaVersion());
    }

    private class ScalaToolProvider implements ToolProvider {

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
                scalaJavaJointCompileSpec.setScalaClasspath(resolveDependency(String.format("org.scala-lang:scala-compiler:%s", scalaVersion)).getFiles());
                scalaJavaJointCompileSpec.setZincClasspath(resolveDependency(String.format("com.typesafe.zinc:zinc:%s", ScalaBasePlugin.DEFAULT_ZINC_VERSION)).getFiles());
                File projectDir = projectFinder.getProject(":").getProjectDir();
                ScalaCompilerFactory scalaCompilerFactory = new ScalaCompilerFactory(projectDir, antBuilder, javaCompilerFactory, DefaultScalaToolChain.this.compilerDaemonManager);
                @SuppressWarnings("unchecked") Compiler<T> delegatingCompiler = (Compiler<T>) scalaCompilerFactory.newCompiler(scalaJavaJointCompileSpec);
                return delegatingCompiler;
            }

            /**
             * TODO RG: better error handling
             * */
            return null;
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
}
