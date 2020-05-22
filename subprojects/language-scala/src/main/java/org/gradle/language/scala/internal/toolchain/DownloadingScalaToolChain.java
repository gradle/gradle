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
import org.gradle.api.artifacts.ResolveException;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.internal.ClassPathRegistry;
import org.gradle.api.internal.tasks.scala.HashedClasspath;
import org.gradle.initialization.ClassLoaderRegistry;
import org.gradle.internal.classloader.ClasspathHasher;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.internal.classpath.DefaultClassPath;
import org.gradle.language.scala.ScalaPlatform;
import org.gradle.platform.base.internal.toolchain.ToolProvider;
import org.gradle.process.internal.JavaForkOptionsFactory;
import org.gradle.workers.internal.ActionExecutionSpecFactory;
import org.gradle.workers.internal.WorkerDaemonFactory;

import java.io.File;
import java.util.Set;

public class DownloadingScalaToolChain implements ScalaToolChainInternal {
    private final File gradleUserHomeDir;
    private final File daemonWorkingDir;
    private final WorkerDaemonFactory workerDaemonFactory;
    private final ConfigurationContainer configurationContainer;
    private final DependencyHandler dependencyHandler;
    private final JavaVersion javaVersion;
    private final JavaForkOptionsFactory forkOptionsFactory;
    private final ClassPathRegistry classPathRegistry;
    private final ClassLoaderRegistry classLoaderRegistry;
    private final ActionExecutionSpecFactory actionExecutionSpecFactory;
    private final ClasspathHasher classpathHasher;

    public DownloadingScalaToolChain(File gradleUserHomeDir, File daemonWorkingDir, WorkerDaemonFactory workerDaemonFactory, ConfigurationContainer configurationContainer, DependencyHandler dependencyHandler, JavaForkOptionsFactory forkOptionsFactory, ClassPathRegistry classPathRegistry, ClassLoaderRegistry classLoaderRegistry, ActionExecutionSpecFactory actionExecutionSpecFactory, ClasspathHasher classpathHasher) {
        this.gradleUserHomeDir = gradleUserHomeDir;
        this.daemonWorkingDir = daemonWorkingDir;
        this.workerDaemonFactory = workerDaemonFactory;
        this.configurationContainer = configurationContainer;
        this.dependencyHandler = dependencyHandler;
        this.forkOptionsFactory = forkOptionsFactory;
        this.classPathRegistry = classPathRegistry;
        this.classLoaderRegistry = classLoaderRegistry;
        this.actionExecutionSpecFactory = actionExecutionSpecFactory;
        this.javaVersion = JavaVersion.current();
        this.classpathHasher = classpathHasher;
    }

    @Override
    public String getName() {
        return "Scala Toolchain";
    }

    @Override
    public String getDisplayName() {
        return "Scala Toolchain (JDK " + javaVersion.getMajorVersion() + " (" + javaVersion + "))";
    }

    @Override
    public ToolProvider select(ScalaPlatform targetPlatform) {
        try {
            Dependency scalaCompiler = dependencyHandler.create("org.scala-lang:scala-compiler:" + targetPlatform.getScalaVersion());
            Dependency compilerBridge = dependencyHandler.create("org.scala-sbt:compiler-bridge_" + targetPlatform.getScalaCompatibilityVersion() + ":" + DefaultScalaToolProvider.DEFAULT_ZINC_VERSION + ":sources@jar");
            Dependency compilerInterface = dependencyHandler.create("org.scala-sbt:compiler-interface:" + DefaultScalaToolProvider.DEFAULT_ZINC_VERSION);
            Configuration scalaClasspath = resolveDependency(scalaCompiler, compilerBridge, compilerInterface);
            ClassPath resolvedScalaClasspath = DefaultClassPath.of(scalaClasspath.resolve());
            HashedClasspath hashedScalaClasspath = new HashedClasspath(resolvedScalaClasspath, classpathHasher);

            Configuration zincClasspath = resolveDependency(dependencyHandler.create("org.scala-sbt:zinc_2.12:" + DefaultScalaToolProvider.DEFAULT_ZINC_VERSION));
            Set<File> resolvedZincClasspath = zincClasspath.resolve();
            return new DefaultScalaToolProvider(daemonWorkingDir, workerDaemonFactory, forkOptionsFactory, hashedScalaClasspath, resolvedZincClasspath, classPathRegistry, classLoaderRegistry, actionExecutionSpecFactory);

        } catch(ResolveException resolveException) {
            return new NotFoundScalaToolProvider(resolveException);
        }
    }

    private Configuration resolveDependency(Dependency... dependencies) {
        return configurationContainer.detachedConfiguration(dependencies);
    }
}
