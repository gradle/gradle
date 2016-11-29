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

package org.gradle.plugin.use.resolve.service.internal;

import org.gradle.StartParameter;
import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.internal.artifacts.DependencyResolutionServices;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelectorScheme;
import org.gradle.internal.Factories;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.plugin.internal.DefaultPluginId;
import org.gradle.plugin.use.internal.InvalidPluginRequestException;
import org.gradle.plugin.use.internal.InternalPluginRequest;
import org.gradle.plugin.use.resolve.internal.PluginResolution;
import org.gradle.plugin.use.resolve.internal.PluginResolutionResult;
import org.gradle.plugin.use.resolve.internal.PluginResolveContext;
import org.gradle.plugin.use.resolve.internal.PluginResolver;

public class PluginPortalResolver implements PluginResolver {

    public static final String OVERRIDE_URL_PROPERTY = PluginPortalResolver.class.getName() + ".repo.override";
    private static final String DEFAULT_API_URL = "https://plugins.gradle.org/api/gradle";

    private final PluginResolutionServiceClient portalClient;
    private final VersionSelectorScheme versionSelectorScheme;
    private final StartParameter startParameter;
    private final ResolutionServiceResolver resolutionServiceResolver;

    public PluginPortalResolver(
        PluginResolutionServiceClient portalClient,
        VersionSelectorScheme versionSelectorScheme, StartParameter startParameter,
        ResolutionServiceResolver resolutionServiceResolver) {
        this.portalClient = portalClient;
        this.versionSelectorScheme = versionSelectorScheme;
        this.startParameter = startParameter;
        this.resolutionServiceResolver = resolutionServiceResolver;
    }

    private static String getUrl() {
        return System.getProperty(OVERRIDE_URL_PROPERTY, DEFAULT_API_URL);
    }

    public void resolve(InternalPluginRequest pluginRequest, PluginResolutionResult result) throws InvalidPluginRequestException {
        if (pluginRequest.getVersion() == null) {
            result.notFound(getDescription(), "plugin dependency must include a version number for this source");
        } else {
            if (pluginRequest.getVersion().endsWith("-SNAPSHOT")) {
                result.notFound(getDescription(), "snapshot plugin versions are not supported");
            } else if (isDynamicVersion(pluginRequest.getVersion())) {
                result.notFound(getDescription(), "dynamic plugin versions are not supported");
            } else {
                HttpPluginResolutionServiceClient.Response<PluginUseMetaData> response = portalClient.queryPluginMetadata(getUrl(), startParameter.isRefreshDependencies(), pluginRequest);
                if (response.isError()) {
                    ErrorResponse errorResponse = response.getErrorResponse();
                    if (response.getStatusCode() == 404) {
                        result.notFound(getDescription(), errorResponse.message);
                    } else {
                        throw new GradleException(String.format("Plugin resolution service returned HTTP %d with message '%s' (url: %s)", response.getStatusCode(), errorResponse.message, response.getUrl()));
                    }
                } else {
                    PluginUseMetaData metaData = response.getResponse();
                    if (metaData.legacy) {
                        handleLegacy(metaData, result);
                    } else {
                        ClassPath classPath = resolvePluginDependencies(metaData);
                        PluginResolution resolution = resolutionServiceResolver.buildPluginResolution(pluginRequest.getId(), Factories.constant(classPath));
                        result.found(getDescription(), resolution);
                    }
                }
            }
        }
    }

    private ClassPath resolvePluginDependencies(final PluginUseMetaData metadata) {
        DependencyResolutionServices resolution = resolutionServiceResolver.getDependencyResolutionServices();

        RepositoryHandler repositories = resolution.getResolveRepositoryHandler();
        final String repoUrl = metadata.implementation.get("repo");
        repositories.maven(new Action<MavenArtifactRepository>() {
            public void execute(MavenArtifactRepository mavenArtifactRepository) {
                mavenArtifactRepository.setUrl(repoUrl);
            }
        });

        return resolutionServiceResolver.resolvePluginDependencies(metadata.implementation.get("gav"), repoUrl);
    }

    private void handleLegacy(final PluginUseMetaData metadata, PluginResolutionResult result) {
        final DefaultPluginId pluginId = DefaultPluginId.of(metadata.id);
        result.found(getDescription(), new PluginResolution() {
            @Override
            public DefaultPluginId getPluginId() {
                return pluginId;
            }

            public void execute(PluginResolveContext context) {
                context.addLegacy(pluginId, metadata.implementation.get("repo"), metadata.implementation.get("gav"));
            }
        });
    }

    private boolean isDynamicVersion(String version) {
        return versionSelectorScheme.parseSelector(version).isDynamic();
    }

    public String getDescription() {
        return "Gradle Central Plugin Repository";
    }

}
