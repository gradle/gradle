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
import org.gradle.api.artifacts.ExternalModuleDependency;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.artifacts.ModuleVersionSelector;
import org.gradle.api.artifacts.dsl.DependencyFactory;
import org.gradle.api.artifacts.repositories.ArtifactRepository;
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier;
import org.gradle.api.internal.artifacts.DependencyResolutionServices;
import org.gradle.api.internal.artifacts.repositories.ArtifactRepositoryInternal;
import org.gradle.api.internal.plugins.PluginManagerInternal;
import org.gradle.plugin.management.internal.PluginCoordinates;
import org.gradle.plugin.management.internal.PluginRequestInternal;
import org.gradle.plugin.use.PluginId;

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

        boolean autoApplied = pluginRequest.getOrigin() == PluginRequestInternal.Origin.AUTO_APPLIED;
        if (exists(markerDependency) || autoApplied) {
            // Even if we don't find the auto-applied plugin version, continue trying to resolve it with a preferred version,
            // in case the user provides an explicit or transitive required version.
            // The resolution will fail if there is no user-provided required version, however it avoids us failing here
            // if the weak version is not present but never selected.
            return PluginResolutionResult.found(new ExternalPluginResolution(getDependencyFactory(), pluginRequest, autoApplied));
        } else {
            return handleNotFound("could not resolve plugin artifact '" + getNotation(markerDependency) + "'");
        }
    }

    static class ExternalPluginResolution implements PluginResolution {
        private final DependencyFactory dependencyFactory;
        private final PluginRequestInternal pluginRequest;
        private final boolean useWeakVersion;

        /**
         * @param dependencyFactory Creates dependency instances
         * @param pluginRequest The original plugin request.
         * @param useWeakVersion Whether a preferred version should be used for the plugin dependency.
         */
        public ExternalPluginResolution(DependencyFactory dependencyFactory, PluginRequestInternal pluginRequest, boolean useWeakVersion) {
            this.dependencyFactory = dependencyFactory;
            this.pluginRequest = pluginRequest;
            this.useWeakVersion = useWeakVersion;
        }

        @Override
        public PluginId getPluginId() {
            return pluginRequest.getId();
        }

        @Override
        public String getPluginVersion() {
            if (pluginRequest.getModule() != null) {
                return pluginRequest.getModule().getVersion();
            } else {
                return pluginRequest.getVersion();
            }
        }

        @Override
        public void accept(PluginResolutionVisitor visitor) {
            String id = pluginRequest.getId().getId();

            ModuleVersionSelector selector = pluginRequest.getModule();
            ModuleIdentifier module = selector != null
                ? selector.getModule()
                : DefaultModuleIdentifier.newId(id, id + PLUGIN_MARKER_SUFFIX);

            visitDependency(visitor, module);
            pluginRequest.getAlternativeCoordinates().ifPresent(altCoords ->
                visitModuleReplacements(visitor, altCoords, id, module)
            );
        }

        private void visitDependency(PluginResolutionVisitor visitor, ModuleIdentifier module) {
            ExternalModuleDependency dependency = dependencyFactory.create(module.getGroup(), module.getName(), null);
            dependency.version(version -> {
                if (useWeakVersion) {
                    version.prefer(getPluginVersion());
                } else {
                    version.require(getPluginVersion());
                }
            });
            visitor.visitDependency(dependency);
        }

        private static void visitModuleReplacements(PluginResolutionVisitor visitor, PluginCoordinates altCoords, String id, ModuleIdentifier module) {
            String altId = altCoords.getId().getId();
            visitor.visitReplacement(
                DefaultModuleIdentifier.newId(id, id + PLUGIN_MARKER_SUFFIX),
                DefaultModuleIdentifier.newId(altId, altId + PLUGIN_MARKER_SUFFIX)
            );

            if (altCoords.getModule() != null) {
                visitor.visitReplacement(module, altCoords.getModule().getModule());
            }
        }

        @Override
        public void applyTo(PluginManagerInternal pluginManager) {
            PluginCoordinates altCoords = pluginRequest.getAlternativeCoordinates().orElse(null);
            if (altCoords != null && pluginManager.hasPlugin(altCoords.getId().getId())) {
                return;
            }

            pluginManager.apply(pluginRequest.getId().getId());
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

    /**
     * Checks whether the plugin marker artifact exists in the backing artifacts repositories.
     *
     * TODO: Performing resolution here is likely quite inefficient. This performs resolution
     * for each plugin request that is not already found on the classpath. Doing this allows
     * us to produce a better error message at the cost of performance. We should limit the
     * number of resolutions we perform in the buildscript context in order to avoid IO and
     * serial bottlenecks before the build can start configuration and thus perform actual work.
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
            return getDependencyFactory().create(id, id + PLUGIN_MARKER_SUFFIX, pluginRequest.getVersion());
        } else {
            return getDependencyFactory().create(selector.getGroup(), selector.getName(), selector.getVersion());
        }
    }

    private String getNotation(Dependency dependency) {
        return Joiner.on(':').join(dependency.getGroup(), dependency.getName(), dependency.getVersion());
    }

    private DependencyFactory getDependencyFactory() {
        return resolutionServices.getDependencyFactory();
    }
}
