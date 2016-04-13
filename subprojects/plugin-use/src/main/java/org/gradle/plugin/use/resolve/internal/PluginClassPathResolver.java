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

package org.gradle.plugin.use.resolve.internal;

import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ResolveException;
import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.internal.artifacts.DependencyResolutionServices;
import org.gradle.api.internal.artifacts.configurations.ConfigurationContainerInternal;
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal;
import org.gradle.api.specs.Specs;
import org.gradle.internal.Factory;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.internal.classpath.DefaultClassPath;
import org.gradle.internal.exceptions.Contextual;

import java.io.File;
import java.util.Set;

/**
 * Creates a {@link ClassPath} from a plugin and dependencies.
 */
public class PluginClassPathResolver {
    private final Factory<DependencyResolutionServices> dependencyResolutionServicesFactory;

    public PluginClassPathResolver(Factory<DependencyResolutionServices> dependencyResolutionServicesFactory) {
        this.dependencyResolutionServicesFactory = dependencyResolutionServicesFactory;
    }

    public ClassPath resolvePluginDependencies(final String repoUrl, final String groupArtifactVersion) {
        DependencyResolutionServices resolution = dependencyResolutionServicesFactory.create();

        RepositoryHandler repositories = resolution.getResolveRepositoryHandler();
        repositories.maven(new Action<MavenArtifactRepository>() {
            public void execute(MavenArtifactRepository mavenArtifactRepository) {
                mavenArtifactRepository.setUrl(repoUrl);
            }
        });

        Dependency dependency = resolution.getDependencyHandler().create(groupArtifactVersion);

        ConfigurationContainerInternal configurations = (ConfigurationContainerInternal) resolution.getConfigurationContainer();
        ConfigurationInternal configuration = configurations.detachedConfiguration(dependency);

        try {
            Set<File> files = configuration.getResolvedConfiguration().getFiles(Specs.satisfyAll());
            return new DefaultClassPath(files);
        } catch (ResolveException e) {
            throw new DependencyResolutionException("Failed to resolve all plugin dependencies from " + repoUrl, e.getCause());
        }
    }

    @Contextual
    public static class DependencyResolutionException extends GradleException {
        public DependencyResolutionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
