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

import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.internal.artifacts.DependencyResolutionServices;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelectorScheme;
import org.gradle.plugin.internal.PluginId;
import org.gradle.plugin.use.internal.InvalidPluginRequestException;
import org.gradle.plugin.use.internal.PluginRequest;

import java.net.URI;

public class CustomRepositoryPluginResolver implements PluginResolver {
    private final DependencyResolutionServices resolution;
    private final VersionSelectorScheme versionSelectorScheme;

    public CustomRepositoryPluginResolver(DependencyResolutionServices resolution, VersionSelectorScheme versionSelectorScheme) {
        this.resolution = resolution;
        this.versionSelectorScheme = versionSelectorScheme;
    }

    @Override
    public void resolve(final PluginRequest pluginRequest, PluginResolutionResult result) throws InvalidPluginRequestException {
        if (pluginRequest.getVersion() == null) {
            result.notFound(getName(), "plugin dependency must include a version number for this source");
            return;
        }
        if (pluginRequest.getVersion().endsWith("-SNAPSHOT")) {
            result.notFound(getName(), "snapshot plugin versions are not supported");
            return;
        }
        if (versionSelectorScheme.parseSelector(pluginRequest.getVersion()).isDynamic()) {
            result.notFound(getName(), "dynamic plugin versions are not supported");
            return;
        }
        if (exists(pluginRequest)) {
            handleFound(pluginRequest, result);
        } else {
            handleNotFound(pluginRequest, result);
        }
    }

    private boolean exists(PluginRequest request) {
        Dependency dependency = resolution.getDependencyHandler().create(getMarkerCoordinates(request));

        ConfigurationContainer configurations = resolution.getConfigurationContainer();
        Configuration configuration = configurations.detachedConfiguration(dependency);
        configuration.setTransitive(false);

        return !configuration.getResolvedConfiguration().hasError();
    }

    private void handleFound(final PluginRequest pluginRequest, PluginResolutionResult result) {
        result.found(getName(), new PluginResolution() {
            @Override
            public PluginId getPluginId() {
                return pluginRequest.getId();
            }

            public void execute(PluginResolveContext context) {
                context.addLegacy(pluginRequest.getId(), getUrl().toString(), getMarkerCoordinates(pluginRequest));
            }
        });
    }

    private void handleNotFound(PluginRequest pluginRequest, PluginResolutionResult result) {
        result.notFound(getName(), String.format("Could not resolve plugin artifact '%s'", getMarkerCoordinates(pluginRequest)));
    }

    private String getMarkerCoordinates(PluginRequest pluginRequest) {
        return pluginRequest.getId() + ":" + pluginRequest.getId() + ":" + pluginRequest.getVersion();
    }

    private URI getUrl() {
        return getRepository().getUrl();
    }

    private String getName() {
        return getRepository().getName();
    }

    /*
     * Right now we only support a single Maven repository.
     * This will be changed soon, so that this class just takes
     * an existing repository and does not need to inspect its URL or name.
     */
    private MavenArtifactRepository getRepository() {
        return (MavenArtifactRepository) resolution.getResolveRepositoryHandler().get(0);
    }
}
