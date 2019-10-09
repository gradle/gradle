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

import com.google.common.collect.Lists;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.artifacts.ModuleVersionSelector;
import org.gradle.api.initialization.Settings;
import org.gradle.api.internal.BuildDefinition;
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier;
import org.gradle.api.internal.artifacts.DefaultModuleVersionSelector;
import org.gradle.initialization.StartParameterBuildOptions;
import org.gradle.plugin.management.internal.DefaultPluginRequest;
import org.gradle.plugin.management.internal.DefaultPluginRequests;
import org.gradle.plugin.management.internal.PluginRequestInternal;
import org.gradle.plugin.management.internal.PluginRequests;

import java.util.List;

import static org.gradle.initialization.StartParameterBuildOptions.BuildScanOption;

/**
 * A hardcoded {@link AutoAppliedPluginRegistry} that only knows about the build-scan plugin for now.
 */
public class DefaultAutoAppliedPluginRegistry implements AutoAppliedPluginRegistry {
    private final static ModuleIdentifier AUTO_APPLIED_ID = DefaultModuleIdentifier.newId(AutoAppliedBuildScanPlugin.GROUP, AutoAppliedBuildScanPlugin.NAME);
    private final BuildDefinition buildDefinition;

    public DefaultAutoAppliedPluginRegistry(BuildDefinition buildDefinition) {
        this.buildDefinition = buildDefinition;
    }

    @Override
    public PluginRequests getAutoAppliedPlugins(Project target) {
        List<PluginRequestInternal> requests = null;
        if (shouldApplyScanPlugin(target)) {
            requests = Lists.newArrayList(createScanPluginRequest());
        }
        requests = appendStartParameterPluginRequests(requests);
        if (requests != null) {
            return new DefaultPluginRequests(requests);
        }
        return DefaultPluginRequests.EMPTY;
    }

    private List<PluginRequestInternal> appendStartParameterPluginRequests(List<PluginRequestInternal> requests) {
        // only include plugins for the main build, not the included ones
        if (buildDefinition.getFromBuild() == null) {
            List<String> additionalPlugins = buildDefinition.getStartParameter().getAdditionalPlugins();
            for (String pluginAndVersion : additionalPlugins) {
                if (requests == null) {
                    requests = Lists.newArrayListWithExpectedSize(additionalPlugins.size());
                }
                requests.add(createAdditionalPluginRequest(pluginAndVersion));
            }
        }
        return requests;
    }

    @Override
    public PluginRequests getAutoAppliedPlugins(Settings target) {
        return buildDefinition.getInjectedPluginRequests();
    }

    // We temporally disable auto apply functionality to fix a chicken egg problem with the gradle enterprise plugin that is
    //    converted to be a settings plugin
    private boolean shouldApplyScanPlugin(Project target) {
//        StartParameter startParameter = buildDefinition.getStartParameter();
//        return startParameter.isBuildScan() && target.getParent() == null && target.getGradle().getParent() == null;
        return false;
    }

    private static DefaultPluginRequest createScanPluginRequest() {
        ModuleVersionSelector artifact = DefaultModuleVersionSelector.newSelector(AUTO_APPLIED_ID, AutoAppliedBuildScanPlugin.VERSION);
        return new DefaultPluginRequest(AutoAppliedBuildScanPlugin.ID, AutoAppliedBuildScanPlugin.VERSION, true, null, getScriptDisplayName(), artifact);
    }

    private static DefaultPluginRequest createAdditionalPluginRequest(String pluginAndVersion) {
        String[] parts = pluginAndVersion.split(":");
        String id = parts[0];
        String version = "";
        if (parts.length == 2) {
            version = parts[1];
        }
        return new DefaultPluginRequest(id, version, true, null, getCommandLineDisplayName(pluginAndVersion));
    }

    private static String getScriptDisplayName() {
        return String.format("auto-applied by using --%s", BuildScanOption.LONG_OPTION);
    }

    private static String getCommandLineDisplayName(String pluginRequest) {
        return String.format("Applied using command-line option --%s %s", StartParameterBuildOptions.ADD_PLUGIN, pluginRequest);
    }
}
