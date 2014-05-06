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

import org.gradle.api.GradleException;
import org.gradle.api.Nullable;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.internal.artifacts.BaseRepositoryFactory;
import org.gradle.api.internal.artifacts.ModuleMetadataProcessor;
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal;
import org.gradle.api.internal.artifacts.metadata.ModuleVersionMetaData;
import org.gradle.api.internal.artifacts.repositories.ResolutionAwareRepository;
import org.gradle.api.specs.Specs;
import org.gradle.api.internal.artifacts.ArtifactDependencyResolver;
import org.gradle.api.internal.artifacts.ResolverResults;
import org.gradle.api.internal.artifacts.repositories.transport.RepositoryTransportFactory;
import org.gradle.internal.Factories;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.internal.classpath.DefaultClassPath;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.plugin.resolve.internal.*;

import java.io.*;
import java.util.Collections;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PluginPortalResolver implements PluginResolver {
    private static final Pattern TEST_PLUGIN_ID_PATTERN = Pattern.compile("test_(\\w+)_([0-9]+)_(.+)");

    private final ArtifactDependencyResolver resolver;
    private final BaseRepositoryFactory repositoryFactory;
    private final DependencyHandler dependencyHandler;
    private final ConfigurationContainer configurationContainer;
    private final PluginPortalClient portalClient;
    private final Instantiator instantiator;

    private String portalUrl = "http://plugins.gradle.org"; // will eventually be provided by plugin mappings

    public PluginPortalResolver(ArtifactDependencyResolver resolver, BaseRepositoryFactory repositoryFactory, DependencyHandler dependencyHandler, ConfigurationContainer configurationHandler,
                                RepositoryTransportFactory transportFactory, Instantiator instantiator) {
        this.resolver = resolver;
        this.repositoryFactory = repositoryFactory;
        this.dependencyHandler = dependencyHandler;
        this.configurationContainer = configurationHandler;
        this.instantiator = instantiator;

        portalClient = new PluginPortalClient(transportFactory);
    }

    @Nullable
    public PluginResolution resolve(PluginRequest pluginRequest) throws InvalidPluginRequestException {
        pluginRequest = applyTestSettings(pluginRequest);

        PluginUseMetaData metaData = portalClient.queryPluginMetadata(pluginRequest, portalUrl);
        if (metaData == null) { return null; }
        ClassPath classPath = resolvePluginDependencies(metaData);
        return new ClassPathPluginResolution(instantiator, pluginRequest.getId(), Factories.constant(classPath));
    }

    // that's how desperate I am
    private PluginRequest applyTestSettings(PluginRequest pluginRequest) {
        Matcher matcher = TEST_PLUGIN_ID_PATTERN.matcher(pluginRequest.getVersion());
        if (matcher.matches()) {
            portalUrl = "http://" + matcher.group(1) + ":" + matcher.group(2);
            return new DefaultPluginRequest(pluginRequest.getId(), matcher.group(3), pluginRequest.getLineNumber(), pluginRequest.getScriptSource());
        }
        return pluginRequest;
    }

    private ClassPath resolvePluginDependencies(PluginUseMetaData metadata) {
        if (!metadata.implementationType.equals("M2_JAR")) {
            throw new GradleException("Unsupported plugin implementation type: " + metadata.implementationType);
        }

        ResolverResults results = new ResolverResults();
        MavenArtifactRepository repository = repositoryFactory.createMavenRepository();
        repository.setUrl(metadata.implementation.get("repo"));
        Dependency dependency = dependencyHandler.create(metadata.implementation.get("gav"));
        ConfigurationInternal configuration = (ConfigurationInternal) configurationContainer.detachedConfiguration(dependency);

        resolver.resolve(configuration, Collections.singletonList((ResolutionAwareRepository) repository), new NoopMetadataProcessor(), results);
        Set<File> files = results.getResolvedConfiguration().getFiles(Specs.satisfyAll());
        return new DefaultClassPath(files);
    }

    public String getDescriptionForNotFoundMessage() {
        return "Plugin Portal " + portalUrl;
    }

    private static class NoopMetadataProcessor implements ModuleMetadataProcessor {
        public void process(ModuleVersionMetaData metadata) {
            // do nothing
        }
    }
}
