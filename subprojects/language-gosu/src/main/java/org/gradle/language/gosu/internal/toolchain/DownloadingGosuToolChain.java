/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.language.gosu.internal.toolchain;

import org.gradle.api.JavaVersion;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ResolveException;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.internal.tasks.compile.daemon.CompilerDaemonManager;
import org.gradle.language.gosu.GosuPlatform;
import org.gradle.platform.base.internal.toolchain.ToolProvider;

import java.io.File;
import java.util.Set;

public class DownloadingGosuToolChain implements GosuToolChainInternal {
    private final File gradleUserHomeDir;
    private final File rootProjectDir;
    private final CompilerDaemonManager compilerDaemonManager;
    private final ConfigurationContainer configurationContainer;
    private final DependencyHandler dependencyHandler;
    private final JavaVersion javaVersion;

    public DownloadingGosuToolChain(File gradleUserHomeDir, File rootProjectDir, CompilerDaemonManager compilerDaemonManager, ConfigurationContainer configurationContainer, DependencyHandler dependencyHandler) {
        this.gradleUserHomeDir = gradleUserHomeDir;
        this.rootProjectDir = rootProjectDir;
        this.compilerDaemonManager = compilerDaemonManager;
        this.configurationContainer = configurationContainer;
        this.dependencyHandler = dependencyHandler;
        this.javaVersion = JavaVersion.current();
    }

    public String getName() {
        return String.format("Gosu Toolchain");
    }

    public String getDisplayName() {
        return String.format("Gosu Toolchain (JDK %s (%s))", javaVersion.getMajorVersion(), javaVersion);
    }

    public ToolProvider select(GosuPlatform targetPlatform) {
        try {
            Configuration gosuClasspath = resolveDependency(String.format("org.gosu-lang.gosu:gosu-ant-tools:%s", targetPlatform.getGosuVersion()));
            Set<File> resolvedGosuClasspath = gosuClasspath.resolve();
            Set<File> resolvedGosuCompilerBootstrap = resolvedGosuClasspath;
            return new DefaultGosuToolProvider(gradleUserHomeDir, rootProjectDir, compilerDaemonManager, resolvedGosuClasspath, resolvedGosuCompilerBootstrap);

        } catch(ResolveException resolveException) {
            return new NotFoundGosuToolProvider(resolveException);
        }
    }

    private Configuration resolveDependency(Object dependencyNotation) {
        Dependency dependency = dependencyHandler.create(dependencyNotation);
        return configurationContainer.detachedConfiguration(dependency);
    }
}
