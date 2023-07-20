/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.plugin.management.internal.autoapply;

import com.google.common.collect.ImmutableList;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.artifacts.ModuleVersionSelector;
import org.gradle.api.initialization.Settings;
import org.gradle.api.initialization.dsl.ScriptHandler;
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier;
import org.gradle.api.plugins.PluginContainer;
import org.gradle.plugin.management.internal.PluginRequestInternal;
import org.gradle.plugin.management.internal.PluginRequests;

import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.gradle.plugin.use.resolve.internal.ArtifactRepositoriesPluginResolver.PLUGIN_MARKER_SUFFIX;

public class DefaultAutoAppliedPluginHandler implements AutoAppliedPluginHandler {

    private final AutoAppliedPluginRegistry registry;

    public DefaultAutoAppliedPluginHandler(AutoAppliedPluginRegistry registry) {
        this.registry = registry;
    }

    public PluginRequests getAutoAppliedPlugins(PluginRequests initialRequests, Object pluginTarget) {
        if (pluginTarget instanceof Project) {
            Project project = (Project) pluginTarget;

            PluginRequests autoAppliedPlugins = registry.getAutoAppliedPlugins(project);
            if (autoAppliedPlugins.isEmpty()) {
                return PluginRequests.EMPTY;
            }
            return filterAlreadyAppliedOrRequested(autoAppliedPlugins, initialRequests, project.getPlugins(), project.getBuildscript());
        } else if (pluginTarget instanceof Settings) {
            Settings settings = (Settings) pluginTarget;

            PluginRequests autoAppliedPlugins = registry.getAutoAppliedPlugins(settings);
            if (autoAppliedPlugins.isEmpty()) {
                return PluginRequests.EMPTY;
            }
            return filterAlreadyAppliedOrRequested(autoAppliedPlugins, initialRequests, settings.getPlugins(), settings.getBuildscript());
        } else {
            // No auto-applied plugins available
            return PluginRequests.EMPTY;
        }

    }

    private static PluginRequests filterAlreadyAppliedOrRequested(PluginRequests autoAppliedPlugins, final PluginRequests initialRequests, final PluginContainer pluginContainer, final ScriptHandler scriptHandler) {
        return PluginRequests.of(ImmutableList.copyOf(StreamSupport.stream(autoAppliedPlugins.spliterator(), false).filter(autoAppliedPlugin -> !isAlreadyAppliedOrRequested(autoAppliedPlugin, initialRequests, pluginContainer, scriptHandler)).collect(Collectors.toList())));
    }

    private static boolean isAlreadyAppliedOrRequested(PluginRequestInternal autoAppliedPlugin, PluginRequests requests, PluginContainer pluginContainer, ScriptHandler scriptHandler) {
        return isAlreadyApplied(autoAppliedPlugin, pluginContainer) || isAlreadyRequestedInPluginsBlock(autoAppliedPlugin, requests) || isAlreadyRequestedInBuildScriptBlock(autoAppliedPlugin, scriptHandler);
    }

    private static boolean isAlreadyApplied(PluginRequestInternal autoAppliedPlugin, PluginContainer pluginContainer) {
        return pluginContainer.hasPlugin(autoAppliedPlugin.getId().getId());
    }

    private static boolean isAlreadyRequestedInPluginsBlock(PluginRequestInternal autoAppliedPlugin, PluginRequests requests) {
        for (PluginRequestInternal request : requests) {
            if (autoAppliedPlugin.getId().equals(request.getId())) {
                return true;
            }
        }
        return false;
    }

    private static boolean isAlreadyRequestedInBuildScriptBlock(PluginRequestInternal autoAppliedPlugin, ScriptHandler scriptHandler) {
        String pluginId = autoAppliedPlugin.getId().getId();
        ModuleIdentifier pluginMarker = DefaultModuleIdentifier.newId(pluginId, pluginId + PLUGIN_MARKER_SUFFIX);
        Predicate<Dependency> predicate = dependency -> hasMatchingCoordinates(dependency, pluginMarker);

        ModuleVersionSelector moduleSelector = autoAppliedPlugin.getModule();
        if (moduleSelector != null) {
            predicate = predicate.or(dependency -> hasMatchingCoordinates(dependency, moduleSelector.getModule()));
        }

        Configuration classpathConfiguration = scriptHandler.getConfigurations().getByName(ScriptHandler.CLASSPATH_CONFIGURATION);
        return classpathConfiguration.getDependencies().stream().anyMatch(predicate);
    }

    private static boolean hasMatchingCoordinates(Dependency dependency, ModuleIdentifier module) {
        return module.getGroup().equals(dependency.getGroup()) && module.getName().equals(dependency.getName());
    }
}
