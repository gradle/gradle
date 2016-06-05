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
import org.gradle.api.internal.artifacts.DependencyResolutionServices;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelectorScheme;
import org.gradle.plugin.internal.PluginId;
import org.gradle.plugin.use.internal.InvalidPluginRequestException;
import org.gradle.plugin.use.internal.PluginRequest;

public class ArtifactRepositoryPluginResolver implements PluginResolver {
    public static final String PLUGIN_MARKER_SUFFIX = ".gradle.plugin";

    private String name;
    private final DependencyResolutionServices resolution;
    private final VersionSelectorScheme versionSelectorScheme;

    public ArtifactRepositoryPluginResolver(String name, DependencyResolutionServices resolution, VersionSelectorScheme versionSelectorScheme) {
        this.name = name;
        this.resolution = resolution;
        this.versionSelectorScheme = versionSelectorScheme;
    }

    @Override
    public void resolve(final PluginRequest pluginRequest, PluginResolutionResult result) throws InvalidPluginRequestException {
        if (pluginRequest.getVersion() == null) {
            result.notFound(name, "plugin dependency must include a version number for this source");
            return;
        }
        if (pluginRequest.getVersion().endsWith("-SNAPSHOT")) {
            result.notFound(name, "snapshot plugin versions are not supported");
            return;
        }
        if (versionSelectorScheme.parseSelector(pluginRequest.getVersion()).isDynamic()) {
            result.notFound(name, "dynamic plugin versions are not supported");
            return;
        }
        if (exists(pluginRequest)) {
            handleFound(pluginRequest, result);
        } else {
            handleNotFound(pluginRequest, result);
        }
    }

    private boolean exists(PluginRequest request) {
        // This works because the corresponding BackedByArtifactRepository PluginRepository sets
        // registers an ArtifactRepository in the DependencyResolutionServices instance which is
        // exclusively used by this ArtifactRepositoryPluginResolver. If the plugin marker
        // doesn't exist in that isolated ArtifactRepository, this resolver won't look anywhere else.
        Dependency dependency = resolution.getDependencyHandler().create(getMarkerCoordinates(request));

        ConfigurationContainer configurations = resolution.getConfigurationContainer();
        Configuration configuration = configurations.detachedConfiguration(dependency);
        configuration.setTransitive(false);

        return !configuration.getResolvedConfiguration().hasError();
    }

    private void handleFound(final PluginRequest pluginRequest, PluginResolutionResult result) {
        result.found(name, new PluginResolution() {
            @Override
            public PluginId getPluginId() {
                return pluginRequest.getId();
            }

            public void execute(PluginResolveContext context) {
                context.addLegacy(pluginRequest.getId(), getMarkerCoordinates(pluginRequest));
            }
        });
    }

    private void handleNotFound(PluginRequest pluginRequest, PluginResolutionResult result) {
        result.notFound(name, String.format("Could not resolve plugin artifact '%s'", getMarkerCoordinates(pluginRequest)));
    }

    private String getMarkerCoordinates(PluginRequest pluginRequest) {
        return pluginRequest.getId() + ":" + pluginRequest.getId() + PLUGIN_MARKER_SUFFIX +  ":" + pluginRequest.getVersion();
    }

}
