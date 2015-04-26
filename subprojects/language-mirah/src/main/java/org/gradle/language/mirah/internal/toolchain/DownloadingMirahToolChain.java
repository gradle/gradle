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

package org.gradle.language.mirah.internal.toolchain;

import org.gradle.api.JavaVersion;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ResolveException;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.internal.artifacts.dsl.dependencies.ProjectFinder;
import org.gradle.api.internal.tasks.compile.daemon.CompilerDaemonManager;
import org.gradle.language.mirah.MirahPlatform;
import org.gradle.platform.base.internal.toolchain.ToolProvider;

import java.io.File;
import java.util.Set;

public class DownloadingMirahToolChain implements MirahToolChainInternal {
    public static final String DEFAULT_ZINC_VERSION = "0.1.5-SNAPSHOT";

    private ProjectFinder projectFinder;
    private CompilerDaemonManager compilerDaemonManager;
    private final ConfigurationContainer configurationContainer;
    private final DependencyHandler dependencyHandler;
    private final JavaVersion javaVersion;

    public DownloadingMirahToolChain(ProjectFinder projectFinder, CompilerDaemonManager compilerDaemonManager, ConfigurationContainer configurationContainer, DependencyHandler dependencyHandler) {
        this.projectFinder = projectFinder;
        this.compilerDaemonManager = compilerDaemonManager;
        this.configurationContainer = configurationContainer;
        this.dependencyHandler = dependencyHandler;
        this.javaVersion = JavaVersion.current();
    }

    public String getName() {
        return String.format("Mirah Toolchain");
    }

    public String getDisplayName() {
        return String.format("Mirah Toolchain (JDK %s (%s))", javaVersion.getMajorVersion(), javaVersion);
    }

    public ToolProvider select(MirahPlatform targetPlatform) {
        try {
            Configuration mirahClasspath = resolveDependency(String.format("org.mirah:mirah:%s", targetPlatform.getMirahVersion()));
            Set<File> resolvedMirahClasspath = mirahClasspath.resolve();
            return new DefaultMirahToolProvider(projectFinder, compilerDaemonManager, resolvedMirahClasspath);

        } catch(ResolveException resolveException) {
            return new NotFoundMirahToolProvider(resolveException);
        }
    }

    private Configuration resolveDependency(Object dependencyNotation) {
        Dependency dependency = dependencyHandler.create(dependencyNotation);
        return configurationContainer.detachedConfiguration(dependency);
    }
}
