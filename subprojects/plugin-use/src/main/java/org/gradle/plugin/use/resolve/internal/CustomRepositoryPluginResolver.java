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
import org.gradle.api.internal.initialization.ClassLoaderScope;
import org.gradle.api.internal.plugins.PluginInspector;
import org.gradle.api.specs.Specs;
import org.gradle.internal.Factories;
import org.gradle.internal.Factory;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.internal.classpath.DefaultClassPath;
import org.gradle.internal.exceptions.Contextual;
import org.gradle.plugin.use.internal.InvalidPluginRequestException;
import org.gradle.plugin.use.internal.PluginRequest;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Set;

public class CustomRepositoryPluginResolver implements PluginResolver {
    private final ClassLoaderScope parentScope;
    private final PluginInspector pluginInspector;
    private final Factory<DependencyResolutionServices> dependencyResolutionServicesFactory;

    public CustomRepositoryPluginResolver(ClassLoaderScope parentScope, PluginInspector pluginInspector, Factory<DependencyResolutionServices> dependencyResolutionServicesFactory) {
        this.parentScope = parentScope;
        this.pluginInspector = pluginInspector;
        this.dependencyResolutionServicesFactory = dependencyResolutionServicesFactory;
    }

    @Override
    public void resolve(PluginRequest pluginRequest, PluginResolutionResult result) throws InvalidPluginRequestException {
        final String artifactAddress = pluginRequest.getId() + ":" + pluginRequest.getId() + ":" + pluginRequest.getVersion();
        ClassPath classPath = resolvePluginDependencies(getRepoUri(), artifactAddress);
        result.found("Custom Repository", new ClassPathPluginResolution(pluginRequest.getId(), parentScope, Factories.constant(classPath), pluginInspector));
    }

    private URI getRepoUri() {
        String repoUrl = System.getProperty("org.gradle.plugin.repoUrl");
        URI uri = URI.create(repoUrl);
        if (!uri.isAbsolute()) {
            /*
             * TODO this is a workaround for the fact that this code currently runs in a
             * context that does not have a base dir, so the identity file resolver is used.
             * That resolver cannot deal with relative paths. We use the current working dir
             * as a workaround. In the final implementation, the repository handler will live
             * in a context that hase a base dir (settings scope or project scope).
             */
            uri = new File(repoUrl).getAbsoluteFile().toURI();
        }
        return uri;
    }

    private ClassPath resolvePluginDependencies(final URI repoUri, final String groupArtifactVersion) {
        DependencyResolutionServices resolution = dependencyResolutionServicesFactory.create();

        RepositoryHandler repositories = resolution.getResolveRepositoryHandler();
        repositories.maven(new Action<MavenArtifactRepository>() {
            public void execute(MavenArtifactRepository mavenArtifactRepository) {
                mavenArtifactRepository.setUrl(repoUri.toString());
            }
        });

        Dependency dependency = resolution.getDependencyHandler().create(groupArtifactVersion);

        ConfigurationContainerInternal configurations = (ConfigurationContainerInternal) resolution.getConfigurationContainer();
        ConfigurationInternal configuration = configurations.detachedConfiguration(dependency);

        try {
            Set<File> files = configuration.getResolvedConfiguration().getFiles(Specs.satisfyAll());
            return new DefaultClassPath(files);
        } catch (ResolveException e) {
            throw new DependencyResolutionException("Failed to resolve all plugin dependencies from " + repoUri, e.getCause());
        }
    }

    @Contextual
    public static class DependencyResolutionException extends GradleException {
        public DependencyResolutionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
