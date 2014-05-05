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

import com.google.common.collect.ImmutableSet;
import com.google.gson.Gson;
import org.gradle.api.Transformer;
import org.gradle.api.UncheckedIOException;
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
import org.gradle.api.internal.artifacts.repositories.DefaultPasswordCredentials;
import org.gradle.api.internal.artifacts.repositories.transport.RepositoryTransport;
import org.gradle.api.internal.artifacts.repositories.transport.RepositoryTransportFactory;
import org.gradle.api.internal.externalresource.ExternalResource;
import org.gradle.api.internal.externalresource.transport.http.HttpResponseResource;
import org.gradle.internal.Factories;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.internal.classpath.DefaultClassPath;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.plugin.resolve.internal.*;
import org.gradle.util.GradleVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PluginPortalResolver implements PluginResolver {
    private static final Logger LOGGER = LoggerFactory.getLogger(PluginPortalResolver.class);
    private static final String REQUEST_URL = "/api/gradle/%s/plugin/use/%s/%s";
    private static final Pattern TEST_PLUGIN_ID_PATTERN = Pattern.compile("test_(\\w+)_([0-9]+)_(.+)");

    private final ArtifactDependencyResolver resolver;
    private final BaseRepositoryFactory repositoryFactory;
    private final DependencyHandler dependencyHandler;
    private final ConfigurationContainer configurationContainer;
    private final RepositoryTransportFactory transportFactory;
    private final Instantiator instantiator;

    private String portalUrl = "http://plugins.gradle.org"; // will eventually be provided by plugin mappings

    public PluginPortalResolver(ArtifactDependencyResolver resolver, BaseRepositoryFactory repositoryFactory, DependencyHandler dependencyHandler, ConfigurationContainer configurationHandler,
                                RepositoryTransportFactory transportFactory, Instantiator instantiator) {
        this.resolver = resolver;

        this.dependencyHandler = dependencyHandler;
        this.repositoryFactory = repositoryFactory;
        this.configurationContainer = configurationHandler;
        this.transportFactory = transportFactory;
        this.instantiator = instantiator;
    }

    public PluginResolution resolve(PluginRequest pluginRequest) throws InvalidPluginRequestException {
        RepositoryTransport transport = transportFactory.createTransport(ImmutableSet.of("http"), "Plugin Portal", new DefaultPasswordCredentials());
        pluginRequest = applyTestSettings(pluginRequest);
        String requestUrl = String.format(portalUrl + REQUEST_URL, GradleVersion.current().getVersion(), pluginRequest.getId(), pluginRequest.getVersion());
        URI requestUri = toUri(requestUrl);

        PluginUseMetaData metaData = queryPluginMetadata(transport, pluginRequest, requestUri);
        ClassPath classPath = resolvePluginClassPath(metaData);
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

    private PluginUseMetaData queryPluginMetadata(RepositoryTransport transport, PluginRequest pluginRequest, URI requestUri) {
        ExternalResource resource = null;
        try {
            resource = transport.getRepository().getResource(requestUri);
            HttpResponseResource response = (HttpResponseResource) resource;
            if (response.getStatusCode() != 200) {
                throw new UncheckedIOException(String.format("Failed to resolve plugin %s:%s from portal %s. HTTP status code: %d",
                        pluginRequest.getId(), pluginRequest.getVersion(), portalUrl, response.getStatusCode()));
            }
            return resource.withContent(new Transformer<PluginUseMetaData, InputStream>() {
                public PluginUseMetaData transform(InputStream inputStream) {
                    Reader reader;
                    try {
                        reader = new InputStreamReader(inputStream, "utf-8");
                    } catch (UnsupportedEncodingException e) {
                        throw new AssertionError(e);
                    }
                    return new Gson().fromJson(reader, PluginUseMetaData.class);
                }
            });
        } catch (IOException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        } finally {
            try {
                if (resource != null) {
                    resource.close();
                }
            } catch (IOException e) {
                LOGGER.warn("Error closing HTTP resource", e);
            }
        }
    }

    private ClassPath resolvePluginClassPath(PluginUseMetaData metadata) {
        if (!metadata.implementationType.equals("M2_JAR")) {
            throw new RuntimeException("Cannot resolve plugins with implementation type " + metadata.implementationType);
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

    private URI toUri(String requestUrl) {
        try {
            return new URI(requestUrl);
        } catch (URISyntaxException e) {
            throw new RuntimeException("Invalid request URL: " + requestUrl, e);
        }
    }

    public String getDescriptionForNotFoundMessage() {
        return "Plugin Portal " + portalUrl;
    }

    private static class NoopMetadataProcessor implements ModuleMetadataProcessor {
        public void process(ModuleVersionMetaData metadata) {
            // do nothing
        }
    }

    private static class PluginUseMetaData {
        String id;
        String version;
        Map<String, String> implementation;
        String implementationType;
    }
}
