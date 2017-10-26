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
import org.gradle.StartParameter;
import org.gradle.api.Project;
import org.gradle.api.internal.artifacts.DefaultModuleVersionSelector;
import org.gradle.plugin.management.internal.DefaultPluginRequests;
import org.gradle.plugin.management.internal.ImplementationClassAwarePluginRequest;
import org.gradle.plugin.management.internal.PluginRequestInternal;
import org.gradle.plugin.management.internal.PluginRequests;
import org.gradle.plugin.use.PluginId;
import org.gradle.plugin.use.internal.DefaultPluginId;

import java.util.List;

import static org.gradle.initialization.StartParameterBuildOptions.BuildScanOption;

/**
 * A hardcoded {@link AutoAppliedPluginRegistry} that only knows about the build-scan plugin for now.
 */
public class DefaultAutoAppliedPluginRegistry implements AutoAppliedPluginRegistry {

    private static final PluginId BUILD_SCAN_PLUGIN_ID = new DefaultPluginId("com.gradle.build-scan");
    private static final String BUILD_SCAN_PLUGIN_AUTO_APPLY_VERSION = "1.9.1";
    private static final String BUILD_SCAN_PLUGIN_GROUP = "com.gradle";
    private static final String BUILD_SCAN_PLUGIN_NAME = "build-scan-plugin";
    private static final String BUILD_SCAN_PLUGIN_IMPL_CLASS = "com.gradle.scan.plugin.BuildScanPlugin";
    private final StartParameter startParameter;

    public DefaultAutoAppliedPluginRegistry(StartParameter startParameter) {
        this.startParameter = startParameter;
    }

    @Override
    public PluginRequests getAutoAppliedPlugins(Project target) {
        List<PluginRequestInternal> requests = Lists.newArrayList();
        if (shouldApplyScanPlugin(target)) {
            requests.add(createScanPluginRequest());
        }
        return new DefaultPluginRequests(requests);
    }

    private boolean shouldApplyScanPlugin(Project target) {
        return startParameter.isBuildScan() && target.getParent() == null && target.getGradle().getParent() == null;
    }

    private PluginRequestInternal createScanPluginRequest() {
        DefaultModuleVersionSelector artifact = new DefaultModuleVersionSelector(BUILD_SCAN_PLUGIN_GROUP, BUILD_SCAN_PLUGIN_NAME, BUILD_SCAN_PLUGIN_AUTO_APPLY_VERSION);
        return new ImplementationClassAwarePluginRequest(BUILD_SCAN_PLUGIN_ID, BUILD_SCAN_PLUGIN_AUTO_APPLY_VERSION, true, null, getScriptDisplayName(), artifact, BUILD_SCAN_PLUGIN_IMPL_CLASS);
    }

    private static String getScriptDisplayName() {
        return String.format("auto-applied by using --%s", BuildScanOption.LONG_OPTION);
    }
}
