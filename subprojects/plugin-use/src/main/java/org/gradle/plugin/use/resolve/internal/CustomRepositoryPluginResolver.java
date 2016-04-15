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
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.internal.artifacts.DependencyResolutionServices;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelectorScheme;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.internal.Factory;
import org.gradle.plugin.internal.PluginId;
import org.gradle.plugin.use.internal.InvalidPluginRequestException;
import org.gradle.plugin.use.internal.PluginRequest;

public class CustomRepositoryPluginResolver implements PluginResolver {
    static final String REPO_SYSTEM_PROPERTY = "org.gradle.plugin.repoUrl";
    private static final String UNSET_REPO_SYSTEM_PROPERTY = "repo-url-unset-in-system-properties";

    private final VersionSelectorScheme versionSelectorScheme;
    private final Factory<DependencyResolutionServices> dependencyResolutionServicesFactory;
    private final FileResolver fileResolver;
    private String repoUrl;

    public CustomRepositoryPluginResolver(VersionSelectorScheme versionSelectorScheme, FileResolver fileResolver,
                                          Factory<DependencyResolutionServices> dependencyResolutionServicesFactory) {
        this.versionSelectorScheme = versionSelectorScheme;
        this.fileResolver = fileResolver;
        this.dependencyResolutionServicesFactory = dependencyResolutionServicesFactory;
    }

    @Override
    public void resolve(final PluginRequest pluginRequest, PluginResolutionResult result) throws InvalidPluginRequestException {
        if (getRepoUrl().equals(UNSET_REPO_SYSTEM_PROPERTY)) {
            return;
        }
        if (pluginRequest.getVersion() == null) {
            result.notFound(getDescription(), "plugin dependency must include a version number for this source");
            return;
        }
        if (pluginRequest.getVersion().endsWith("-SNAPSHOT")) {
            result.notFound(getDescription(), "snapshot plugin versions are not supported");
            return;
        }
        if (versionSelectorScheme.parseSelector(pluginRequest.getVersion()).isDynamic()) {
            result.notFound(getDescription(), "dynamic plugin versions are not supported");
            return;
        }
        if (exists(pluginRequest)) {
            handleFound(pluginRequest, result);
        } else {
            handleNotFound(pluginRequest, result);
        }
    }

    private boolean exists(PluginRequest request) {
        DependencyResolutionServices resolution = dependencyResolutionServicesFactory.create();

        RepositoryHandler repositories = resolution.getResolveRepositoryHandler();
        repositories.maven(new Action<MavenArtifactRepository>() {
            public void execute(MavenArtifactRepository mavenArtifactRepository) {
                mavenArtifactRepository.setUrl(getRepoUrl());
            }
        });

        Dependency dependency = resolution.getDependencyHandler().create(getMarkerCoordinates(request));

        ConfigurationContainer configurations = resolution.getConfigurationContainer();
        Configuration configuration = configurations.detachedConfiguration(dependency);
        configuration.setTransitive(false);

        return !configuration.getResolvedConfiguration().hasError();
    }

    private void handleFound(final PluginRequest pluginRequest, PluginResolutionResult result) {
        result.found(getDescription(), new PluginResolution() {
            @Override
            public PluginId getPluginId() {
                return pluginRequest.getId();
            }

            public void execute(PluginResolveContext context) {
                context.addLegacy(pluginRequest.getId(), getRepoUrl(), getMarkerCoordinates(pluginRequest));
            }
        });
    }

    private void handleNotFound(PluginRequest pluginRequest, PluginResolutionResult result) {
        result.notFound(getDescription(), String.format("Could not resolve plugin artifact '%s'", getMarkerCoordinates(pluginRequest)));
    }

    private String getMarkerCoordinates(PluginRequest pluginRequest) {
        return pluginRequest.getId() + ":" + pluginRequest.getId() + ":" + pluginRequest.getVersion();
    }

    // Caches the repoUrl so that we create minimal lock contention on System.getProperty() calls.
    private String getRepoUrl() {
        if (repoUrl == null) {
            repoUrl = System.getProperty(REPO_SYSTEM_PROPERTY, UNSET_REPO_SYSTEM_PROPERTY);
            if (!repoUrl.equals(UNSET_REPO_SYSTEM_PROPERTY)) {
                repoUrl = fileResolver.resolveUri(repoUrl).toString();
            }
        }
        return repoUrl;
    }

    public static String getDescription() {
        return "User-defined Plugin Repository";
    }
}
