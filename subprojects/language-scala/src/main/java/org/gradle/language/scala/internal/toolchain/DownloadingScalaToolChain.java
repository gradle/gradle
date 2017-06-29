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
import org.gradle.api.internal.file.FileResolver;
import org.gradle.workers.internal.WorkerDaemonFactory;
import org.gradle.language.scala.ScalaPlatform;
import org.gradle.platform.base.internal.toolchain.ToolProvider;

import java.io.File;
import java.util.Set;

public class DownloadingScalaToolChain implements ScalaToolChainInternal {
    private final File gradleUserHomeDir;
    private final File rootProjectDir;
    private final WorkerDaemonFactory workerDaemonFactory;
    private final ConfigurationContainer configurationContainer;
    private final DependencyHandler dependencyHandler;
    private final JavaVersion javaVersion;
    private final FileResolver fileResolver;

    public DownloadingScalaToolChain(File gradleUserHomeDir, File rootProjectDir, WorkerDaemonFactory workerDaemonFactory, ConfigurationContainer configurationContainer, DependencyHandler dependencyHandler, FileResolver fileResolver) {
        this.gradleUserHomeDir = gradleUserHomeDir;
        this.rootProjectDir = rootProjectDir;
        this.workerDaemonFactory = workerDaemonFactory;
        this.configurationContainer = configurationContainer;
        this.dependencyHandler = dependencyHandler;
        this.fileResolver = fileResolver;
        this.javaVersion = JavaVersion.current();
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
            Configuration scalaClasspath = resolveDependency("org.scala-lang:scala-compiler:" + targetPlatform.getScalaVersion());
            Configuration zincClasspath = resolveDependency("com.typesafe.zinc:zinc:" + DefaultScalaToolProvider.DEFAULT_ZINC_VERSION);
            Set<File> resolvedScalaClasspath = scalaClasspath.resolve();
            Set<File> resolvedZincClasspath = zincClasspath.resolve();
            return new DefaultScalaToolProvider(gradleUserHomeDir, rootProjectDir, workerDaemonFactory, fileResolver, resolvedScalaClasspath, resolvedZincClasspath);

        } catch(ResolveException resolveException) {
            return new NotFoundScalaToolProvider(resolveException);
        }
    }

    private Configuration resolveDependency(Object dependencyNotation) {
        Dependency dependency = dependencyHandler.create(dependencyNotation);
        return configurationContainer.detachedConfiguration(dependency);
    }
}
