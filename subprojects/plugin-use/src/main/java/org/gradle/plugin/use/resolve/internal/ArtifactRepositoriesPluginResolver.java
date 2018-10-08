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

import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.ModuleVersionSelector;
import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.api.artifacts.repositories.ArtifactRepository;
import org.gradle.api.artifacts.result.DependencyResult;
import org.gradle.api.artifacts.result.ResolutionResult;
import org.gradle.api.artifacts.result.ResolvedDependencyResult;
import org.gradle.api.internal.artifacts.DependencyResolutionServices;
import org.gradle.api.internal.artifacts.dependencies.DefaultExternalModuleDependency;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelectorScheme;
import org.gradle.api.internal.artifacts.repositories.ArtifactRepositoryInternal;
import org.gradle.plugin.management.internal.InvalidPluginRequestException;
import org.gradle.plugin.management.internal.PluginRequestInternal;
import org.gradle.plugin.use.PluginId;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Iterator;
import java.util.Set;

import static com.google.common.base.Strings.isNullOrEmpty;
import static com.google.common.collect.Iterables.getOnlyElement;

public class ArtifactRepositoriesPluginResolver implements PluginResolver {

    public static final String PLUGIN_MARKER_SUFFIX = ".gradle.plugin";

    @VisibleForTesting
    static final String SOURCE_NAME = "Plugin Repositories";

    public static ArtifactRepositoriesPluginResolver createWithDefaults(DependencyResolutionServices dependencyResolutionServices, VersionSelectorScheme versionSelectorScheme) {
        RepositoryHandler repositories = dependencyResolutionServices.getResolveRepositoryHandler();
        if (repositories.isEmpty()) {
            repositories.gradlePluginPortal();
        }
        return new ArtifactRepositoriesPluginResolver(dependencyResolutionServices, versionSelectorScheme);
    }

    private final DependencyResolutionServices resolution;
    private final VersionSelectorScheme versionSelectorScheme;

    private ArtifactRepositoriesPluginResolver(DependencyResolutionServices dependencyResolutionServices, VersionSelectorScheme versionSelectorScheme) {
        this.resolution = dependencyResolutionServices;
        this.versionSelectorScheme = versionSelectorScheme;
    }

    @Override
    public void resolve(PluginRequestInternal pluginRequest, PluginResolutionResult result) throws InvalidPluginRequestException {
        ModuleVersionSelector moduleSelector = pluginRequest.getModule();
        if (validateVersion(moduleSelector != null ? moduleSelector.getVersion() : pluginRequest.getVersion(), result)) {
            if (moduleSelector == null) {
                resolveFromPluginMarker(pluginRequest, result);
            } else {
                resolveFromModule(pluginRequest, moduleSelector, result);
            }
        }
    }

    private boolean validateVersion(@Nullable String version, PluginResolutionResult result) {
        return validateVersion(versionSelectorScheme, version, result);
    }

    @VisibleForTesting
    static boolean validateVersion(VersionSelectorScheme scheme, @Nullable String version, PluginResolutionResult result) {
        if (isNullOrEmpty(version)) {
            result.notFound(SOURCE_NAME, "plugin dependency must include a version number for this source");
            return false;
        }
        if (scheme.parseSelector(version).isDynamic()) {
            result.notFound(SOURCE_NAME, "dynamic plugin versions are not supported");
            return false;
        }
        return true;
    }

    private void resolveFromPluginMarker(PluginRequestInternal pluginRequest, PluginResolutionResult result) {
        ModuleDependency markerDependency = pluginMarkerDependencyFor(pluginRequest);
        PluginFromMarkerResult pluginResult = resolvePluginDependencyFrom(markerDependency);
        if (pluginResult.pluginDependency != null) {
            handleFound(pluginRequest, pluginResult.pluginDependency, result);
        } else {
            handleNotFound(pluginResult.notFound, markerDependency, result);
        }
    }

    private void resolveFromModule(PluginRequestInternal pluginRequest, ModuleVersionSelector moduleSelector, PluginResolutionResult result) {
        ModuleDependency moduleDependency = moduleDependencyFor(moduleSelector);
        if (moduleDependencyExists(moduleDependency)) {
            handleFound(pluginRequest, moduleDependency, result);
        } else {
            handleNotFound("module", moduleDependency, result);
        }
    }

    private void handleFound(final PluginRequestInternal pluginRequest, final Dependency pluginDependency, PluginResolutionResult result) {
        result.found("Plugin Repositories", new PluginResolution() {
            @Override
            public PluginId getPluginId() {
                return pluginRequest.getId();
            }

            public void execute(@Nonnull PluginResolveContext context) {
                context.addLegacy(pluginRequest.getId(), pluginDependency);
            }
        });
    }

    private void handleNotFound(String description, ModuleDependency dependency, PluginResolutionResult result) {
        String message = "could not resolve plugin " + description + " '" + getNotation(dependency) + "'";
        result.notFound(SOURCE_NAME, message, buildNotFoundDetailMessage());
    }

    private PluginFromMarkerResult resolvePluginDependencyFrom(ModuleDependency markerDependency) {
        ResolutionResult resolutionResult = resolution
            .getConfigurationContainer()
            .detachedConfiguration(markerDependency)
            .getIncoming()
            .getResolutionResult();
        DependencyResult markerResult = getOnlyElement(resolutionResult.getRoot().getDependencies());
        if (!(markerResult instanceof ResolvedDependencyResult)) {
            return PluginFromMarkerResult.MARKER;
        }
        ResolvedDependencyResult resolvedMarker = (ResolvedDependencyResult) markerResult;
        Set<? extends DependencyResult> markerDependencies = resolvedMarker.getSelected().getDependencies();
        if (markerDependencies.size() != 1) {
            return PluginFromMarkerResult.MODULE_DEPENDENCY;
        }
        DependencyResult pluginResult = getOnlyElement(markerDependencies);
        if (!(pluginResult instanceof ResolvedDependencyResult)) {
            return PluginFromMarkerResult.MODULE_DEPENDENCY;
        }
        ResolvedDependencyResult resolvedPlugin = (ResolvedDependencyResult) pluginResult;
        ModuleVersionIdentifier resolvedPluginVersion = resolvedPlugin.getSelected().getModuleVersion();
        if (resolvedPluginVersion == null) {
            return PluginFromMarkerResult.MODULE_DEPENDENCY;
        }
        return new PluginFromMarkerResult(moduleDependencyFor(resolvedPluginVersion));
    }

    private boolean moduleDependencyExists(ModuleDependency moduleDependency) {
        ResolutionResult resolutionResult = resolution
            .getConfigurationContainer()
            .detachedConfiguration(moduleDependency)
            .setTransitive(false)
            .getIncoming()
            .getResolutionResult();
        DependencyResult moduleResult = getOnlyElement(resolutionResult.getRoot().getDependencies());
        return moduleResult instanceof ResolvedDependencyResult;
    }

    private ModuleDependency pluginMarkerDependencyFor(PluginRequestInternal pluginRequest) {
        String id = pluginRequest.getId().getId();
        return new DefaultExternalModuleDependency(id, id + PLUGIN_MARKER_SUFFIX, pluginRequest.getVersion());
    }

    private ModuleDependency moduleDependencyFor(ModuleVersionIdentifier moduleIdentifier) {
        return new DefaultExternalModuleDependency(moduleIdentifier.getGroup(), moduleIdentifier.getName(), moduleIdentifier.getVersion());
    }

    private ModuleDependency moduleDependencyFor(ModuleVersionSelector moduleSelector) {
        return new DefaultExternalModuleDependency(moduleSelector.getGroup(), moduleSelector.getName(), moduleSelector.getVersion());
    }

    private String getNotation(Dependency dependency) {
        return Joiner.on(':').join(dependency.getGroup(), dependency.getName(), dependency.getVersion());
    }

    private String buildNotFoundDetailMessage() {
        StringBuilder detail = new StringBuilder("Searched in the following repositories:\n");
        for (Iterator<ArtifactRepository> it = resolution.getResolveRepositoryHandler().iterator(); it.hasNext();) {
            detail.append("  ").append(((ArtifactRepositoryInternal) it.next()).getDisplayName());
            if (it.hasNext()) {
                detail.append("\n");
            }
        }
        return detail.toString();
    }

    private static class PluginFromMarkerResult {

        static final PluginFromMarkerResult MARKER = new PluginFromMarkerResult("marker");
        static final PluginFromMarkerResult MODULE_DEPENDENCY = new PluginFromMarkerResult("module from found marker");

        @Nullable
        final ModuleDependency pluginDependency;

        @Nullable
        final String notFound;

        PluginFromMarkerResult(ModuleDependency pluginDependency) {
            this.pluginDependency = pluginDependency;
            this.notFound = null;
        }

        private PluginFromMarkerResult(String notFound) {
            this.pluginDependency = null;
            this.notFound = notFound;
        }
    }
}
