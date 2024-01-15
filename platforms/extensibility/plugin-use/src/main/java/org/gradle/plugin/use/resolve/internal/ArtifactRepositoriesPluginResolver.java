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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import org.gradle.api.artifacts.ArtifactView;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.artifacts.ModuleVersionSelector;
import org.gradle.api.artifacts.repositories.ArtifactRepository;
import org.gradle.api.internal.artifacts.DependencyResolutionServices;
import org.gradle.api.internal.artifacts.dependencies.DefaultExternalModuleDependency;
import org.gradle.api.internal.artifacts.repositories.ArtifactRepositoryInternal;
import org.gradle.api.internal.plugins.PluginManagerInternal;
import org.gradle.plugin.management.internal.PluginRequestInternal;
import org.gradle.plugin.use.PluginId;

import javax.annotation.Nullable;
import java.util.Iterator;

import static com.google.common.base.Strings.isNullOrEmpty;

public class ArtifactRepositoriesPluginResolver implements PluginResolver {

    public static final String PLUGIN_MARKER_SUFFIX = ".gradle.plugin";

    @VisibleForTesting
    static final String SOURCE_NAME = "Plugin Repositories";

    private final DependencyResolutionServices resolutionServices;

    public ArtifactRepositoriesPluginResolver(DependencyResolutionServices resolutionServices) {
        this.resolutionServices = resolutionServices;
    }

    @Override
    public PluginResolutionResult resolve(PluginRequestInternal pluginRequest) {
        ModuleDependency markerDependency = getMarkerDependency(pluginRequest);
        String markerVersion = markerDependency.getVersion();
        if (isNullOrEmpty(markerVersion)) {
            return PluginResolutionResult.notFound(SOURCE_NAME, "plugin dependency must include a version number for this source");
        }

        if (exists(markerDependency)) {
            return PluginResolutionResult.found(new ExternalPluginResolution(pluginRequest.getId(), markerDependency));
        } else {
            return handleNotFound("could not resolve plugin artifact '" + getNotation(markerDependency) + "'");
        }
    }

    static class ExternalPluginResolution implements PluginResolution {
        private final PluginId pluginId;
        private final Dependency markerDependency;

        public ExternalPluginResolution(PluginId pluginId, Dependency markerDependency) {
            this.pluginId = pluginId;
            this.markerDependency = markerDependency;
        }

        @Override
        public PluginId getPluginId() {
            return pluginId;
        }

        @Nullable
        @Override
        public String getPluginVersion() {
            return markerDependency.getVersion();
        }

        @Override
        public void accept(PluginResolutionVisitor visitor) {
            visitor.visitDependency(markerDependency);
        }

        @Override
        public void applyTo(PluginManagerInternal pluginManager) {
            pluginManager.apply(pluginId.getId());
        }
    }

    private PluginResolutionResult handleNotFound(String message) {
        StringBuilder detail = new StringBuilder("Searched in the following repositories:\n");
        for (Iterator<ArtifactRepository> it = resolutionServices.getResolveRepositoryHandler().iterator(); it.hasNext();) {
            detail.append("  ").append(((ArtifactRepositoryInternal) it.next()).getDisplayName());
            if (it.hasNext()) {
                detail.append("\n");
            }
        }
        return PluginResolutionResult.notFound(SOURCE_NAME, message, detail.toString());
    }

    /*
     * Checks whether the plugin marker artifact exists in the backing artifacts repositories.
     */
    private boolean exists(ModuleDependency dependency) {
        ConfigurationContainer configurations = resolutionServices.getConfigurationContainer();
        Configuration configuration = configurations.detachedConfiguration(dependency);
        configuration.setTransitive(false);
        ArtifactView lenientView = configuration.getIncoming().artifactView(view -> {
            view.setLenient(true);
        });
        return lenientView.getArtifacts().getFailures().isEmpty();
    }

    private ModuleDependency getMarkerDependency(PluginRequestInternal pluginRequest) {
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
