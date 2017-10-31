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

import org.gradle.StartParameter;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ModuleVersionSelector;
import org.gradle.api.internal.artifacts.DefaultModuleVersionSelector;
import org.gradle.plugin.management.internal.DefaultPluginRequest;
import org.gradle.plugin.management.internal.DefaultPluginRequests;
import org.gradle.plugin.management.internal.PluginRequestInternal;
import org.gradle.plugin.management.internal.PluginRequests;

import java.util.Collections;

import static org.gradle.initialization.StartParameterBuildOptions.BuildScanOption;

/**
 * A hardcoded {@link AutoAppliedPluginRegistry} that only knows about the build-scan plugin for now.
 */
public class DefaultAutoAppliedPluginRegistry implements AutoAppliedPluginRegistry {

    private final StartParameter startParameter;

    public DefaultAutoAppliedPluginRegistry(StartParameter startParameter) {
        this.startParameter = startParameter;
    }

    @Override
    public PluginRequests getAutoAppliedPlugins(Project target) {
        if (shouldApplyScanPlugin(target)) {
            return new DefaultPluginRequests(Collections.<PluginRequestInternal>singletonList(createScanPluginRequest()));
        }
        return DefaultPluginRequests.EMPTY;
    }

    private boolean shouldApplyScanPlugin(Project target) {
        return startParameter.isBuildScan() && target.getParent() == null && target.getGradle().getParent() == null;
    }

    private DefaultPluginRequest createScanPluginRequest() {
        ModuleVersionSelector artifact = DefaultModuleVersionSelector.newSelector(AutoAppliedBuildScanPlugin.GROUP, AutoAppliedBuildScanPlugin.NAME, AutoAppliedBuildScanPlugin.VERSION);
        return new DefaultPluginRequest(AutoAppliedBuildScanPlugin.ID, AutoAppliedBuildScanPlugin.VERSION, true, null, getScriptDisplayName(), artifact);
    }

    private static String getScriptDisplayName() {
        return String.format("auto-applied by using --%s", BuildScanOption.LONG_OPTION);
    }
}
