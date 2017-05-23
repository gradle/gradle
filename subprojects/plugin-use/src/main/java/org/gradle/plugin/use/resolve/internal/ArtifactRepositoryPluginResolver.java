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

import com.google.common.base.Joiner;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ModuleVersionSelector;
import org.gradle.api.internal.artifacts.DependencyResolutionServices;
import org.gradle.api.internal.artifacts.dependencies.DefaultExternalModuleDependency;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelectorScheme;
import org.gradle.plugin.management.internal.PluginRequestInternal;
import org.gradle.plugin.management.internal.InvalidPluginRequestException;
import org.gradle.plugin.use.PluginId;

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
    public void resolve(final ContextAwarePluginRequest pluginRequest, PluginResolutionResult result) throws InvalidPluginRequestException {
        if (pluginRequest.getId() == null) {
            result.notFound(name, "plugin dependency must include a plugin id for this source");
            return;
        }
        String markerVersion = getMarkerDependency(pluginRequest).getVersion();
        if (markerVersion == null) {
            result.notFound(name, "plugin dependency must include a version number for this source");
            return;
        }

        if (markerVersion.endsWith("-SNAPSHOT")) {
            result.notFound(name, "snapshot plugin versions are not supported");
            return;
        }

        if (versionSelectorScheme.parseSelector(markerVersion).isDynamic()) {
            result.notFound(name, "dynamic plugin versions are not supported");
            return;
        }

        if (exists(pluginRequest)) {
            handleFound(pluginRequest, result);
        } else {
            handleNotFound(pluginRequest, result);
        }
    }

    /*
     * Checks whether the implementation artifact exists in the backing artifact repository.
     */
    private boolean exists(PluginRequestInternal request) {
        Dependency dependency = resolution.getDependencyHandler().create(getMarkerDependency(request));

        ConfigurationContainer configurations = resolution.getConfigurationContainer();
        Configuration configuration = configurations.detachedConfiguration(dependency);
        configuration.setTransitive(false);

        return !configuration.getResolvedConfiguration().hasError();
    }

    private void handleFound(final PluginRequestInternal pluginRequest, PluginResolutionResult result) {
        result.found(name, new PluginResolution() {
            @Override
            public PluginId getPluginId() {
                return pluginRequest.getId();
            }

            public void execute(PluginResolveContext context) {
                context.addLegacy(pluginRequest.getId(), getMarkerDependency(pluginRequest));
            }
        });
    }

    private void handleNotFound(PluginRequestInternal pluginRequest, PluginResolutionResult result) {
        result.notFound(name, String.format("Could not resolve plugin artifact '%s'", getNotation(getMarkerDependency(pluginRequest))));
    }

    private Dependency getMarkerDependency(PluginRequestInternal pluginRequest) {
        ModuleVersionSelector selector = pluginRequest.getModule();
        if (selector == null) {
            String id = pluginRequest.getId().getId();
            return new DefaultExternalModuleDependency(id, id + PLUGIN_MARKER_SUFFIX, pluginRequest.getVersion());
        } else {
            return new DefaultExternalModuleDependency(selector.getGroup(), selector.getName(), selector.getVersion());
        }
    }

    private String getNotation(Dependency dependency) {
        return Joiner.on(':').join(dependency.getGroup(), dependency.getName(), dependency.getVersion());
    }

}
