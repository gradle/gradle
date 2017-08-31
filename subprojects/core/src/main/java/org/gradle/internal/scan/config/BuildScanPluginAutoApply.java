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
package org.gradle.internal.scan.config;

import com.google.common.collect.Lists;
import org.gradle.StartParameter;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.initialization.dsl.ScriptHandler;
import org.gradle.plugin.management.internal.DefaultPluginRequest;
import org.gradle.plugin.management.internal.DefaultPluginRequests;
import org.gradle.plugin.management.internal.PluginRequestInternal;
import org.gradle.plugin.management.internal.PluginRequests;
import org.gradle.plugin.management.internal.PluginRequestsTransformer;
import org.gradle.plugin.use.PluginId;
import org.gradle.plugin.use.internal.DefaultPluginId;
import org.gradle.util.CollectionUtils;

import java.util.List;

/**
 * Automatically adds a plugin request for the build scan plugin when `--scan` is detected.
 *
 * - Plugin request is only added for the root project.
 * - No request is added if the project has already requested the build scan plugin, via `plugins` or `buildscript`.
 * - The plugin request is inserted before any other plugin requests.
 * - A fixed version of the build scan plugin is requested.
 */
public class BuildScanPluginAutoApply implements PluginRequestsTransformer {
    private static final PluginId BUILD_SCAN_PLUGIN_ID = DefaultPluginId.of("com.gradle.build-scan");
    private static final String BUILD_SCAN_PLUGIN_VERSION = "1.9";
    private static final String BUILD_SCAN_PLUGIN_GROUP = "com.gradle";
    private static final String BUILD_SCAN_PLUGIN_NAME = "build-scan-plugin";

    private final StartParameter startParameter;

    public BuildScanPluginAutoApply(StartParameter startParameter) {
        this.startParameter = startParameter;
    }

    @Override
    public PluginRequests transformPluginRequests(PluginRequests requests, Object pluginTarget) {
        if (!startParameter.isBuildScan()) {
            return requests;
        }

        if (!isRootProject(pluginTarget)) {
            return requests;
        }

        if (isPluginAlreadyRequested(requests, (Project) pluginTarget)) {
            return requests;
        }

        DefaultPluginRequest buildScanPluginRequest = new DefaultPluginRequest(BUILD_SCAN_PLUGIN_ID, BUILD_SCAN_PLUGIN_VERSION, true, 0, "auto-apply", null);
        return prependRequest(buildScanPluginRequest, requests);
    }

    private PluginRequests prependRequest(PluginRequestInternal buildScanPluginRequest, PluginRequests requests) {
        List<PluginRequestInternal> copyRequests = Lists.newArrayList();
        copyRequests.add(buildScanPluginRequest);
        CollectionUtils.addAll(copyRequests, requests);
        return new DefaultPluginRequests(copyRequests);
    }

    private boolean isRootProject(Object pluginTarget) {
        if (!(pluginTarget instanceof Project)) {
            return false;
        }

        Project project = (Project) pluginTarget;
        return project.getGradle().getParent() == null
            && project == project.getRootProject();
    }

    private boolean isPluginAlreadyRequested(PluginRequests requests, Project project) {
        for (PluginRequestInternal request : requests) {
            if (BUILD_SCAN_PLUGIN_ID.equals(request.getId())) {
                // Build scan plugin already requested in `plugins`
                return true;
            }
        }

        Configuration classpathConfiguration = project.getBuildscript().getConfigurations().getByName(ScriptHandler.CLASSPATH_CONFIGURATION);
        for (Dependency dependency : classpathConfiguration.getDependencies()) {
            // Build scan plugin already included as `classpath` dependency
            if (BUILD_SCAN_PLUGIN_GROUP.equals(dependency.getGroup()) && BUILD_SCAN_PLUGIN_NAME.equals(dependency.getName())) {
                return true;
            }
        }
        return false;
    }
}
