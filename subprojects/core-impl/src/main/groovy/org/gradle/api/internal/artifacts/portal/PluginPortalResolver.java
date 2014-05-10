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

package org.gradle.api.internal.artifacts.portal;

import org.gradle.StartParameter;
import org.gradle.api.GradleException;
import org.gradle.api.Nullable;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.internal.artifacts.ArtifactDependencyResolver;
import org.gradle.api.internal.artifacts.BaseRepositoryFactory;
import org.gradle.api.internal.artifacts.ModuleMetadataProcessor;
import org.gradle.api.internal.artifacts.ResolverResults;
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.StartParameterResolutionOverride;
import org.gradle.api.internal.artifacts.metadata.ModuleVersionMetaData;
import org.gradle.api.internal.artifacts.repositories.ResolutionAwareRepository;
import org.gradle.api.internal.artifacts.repositories.transport.RepositoryTransportFactory;
import org.gradle.api.specs.Specs;
import org.gradle.internal.Factories;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.internal.classpath.DefaultClassPath;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.plugin.resolve.internal.*;

import java.io.File;
import java.util.Collections;
import java.util.Set;

public class PluginPortalResolver implements PluginResolver {

    public static final String OVERRIDE_URL_PROPERTY = PluginPortalResolver.class.getName() + ".repo.override";
    private static final String DEFAULT_API_URL = "http://plugins.gradle.org";

    private final ArtifactDependencyResolver resolver;
    private final BaseRepositoryFactory repositoryFactory;
    private final DependencyHandler dependencyHandler;
    private final ConfigurationContainer configurationContainer;
    private final PluginPortalClient portalClient;
    private final Instantiator instantiator;
    private final StartParameter startParameter;

    public PluginPortalResolver(ArtifactDependencyResolver resolver, BaseRepositoryFactory repositoryFactory, DependencyHandler dependencyHandler, ConfigurationContainer configurationHandler,
                                RepositoryTransportFactory transportFactory, Instantiator instantiator, StartParameter startParameter) {
        this.resolver = resolver;
        this.repositoryFactory = repositoryFactory;
        this.dependencyHandler = dependencyHandler;
        this.configurationContainer = configurationHandler;
        this.instantiator = instantiator;
        this.startParameter = startParameter;

        this.portalClient = new PluginPortalClient(transportFactory);
    }

    private static String getUrl() {
        return System.getProperty(OVERRIDE_URL_PROPERTY, DEFAULT_API_URL);
    }

    @Nullable
    public PluginResolution resolve(PluginRequest pluginRequest) throws InvalidPluginRequestException {
        if (startParameter.isOffline()) {
            // not yet caching plugin portal responses; let's blow up with a custom message for now
            throw new GradleException(String.format("Plugin was not found locally, and cannot be resolved from plugin portal because Gradle is running in offline mode."));
        }

        PluginUseMetaData metaData = portalClient.queryPluginMetadata(pluginRequest, getUrl());
        if (metaData == null) {
            return null;
        }
        ClassPath classPath = resolvePluginDependencies(metaData);
        return new ClassPathPluginResolution(instantiator, pluginRequest.getId(), Factories.constant(classPath));
    }

    private ClassPath resolvePluginDependencies(PluginUseMetaData metadata) {
        ResolverResults results = new ResolverResults();
        MavenArtifactRepository repository = repositoryFactory.createMavenRepository();
        repository.setUrl(metadata.implementation.get("repo"));
        Dependency dependency = dependencyHandler.create(metadata.implementation.get("gav"));
        ConfigurationInternal configuration = (ConfigurationInternal) configurationContainer.detachedConfiguration(dependency);
        // honor start parameters when resolving plugin dependencies
        StartParameterResolutionOverride resolutionOverride = new StartParameterResolutionOverride(startParameter);
        resolutionOverride.addResolutionRules(configuration.getResolutionStrategy().getResolutionRules());

        resolver.resolve(configuration, Collections.singletonList((ResolutionAwareRepository) repository), new NoopMetadataProcessor(), results);
        Set<File> files = results.getResolvedConfiguration().getFiles(Specs.satisfyAll());
        return new DefaultClassPath(files);
    }

    public String getDescriptionForNotFoundMessage() {
        return "Plugin Portal (" + getUrl() + ")";
    }

    private static class NoopMetadataProcessor implements ModuleMetadataProcessor {
        public void process(ModuleVersionMetaData metadata) {
            // do nothing
        }
    }
}
