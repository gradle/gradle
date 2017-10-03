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
import org.gradle.plugin.management.internal.autoapply.AutoAppliedPluginHandler;
import org.gradle.plugin.management.internal.autoapply.AutoAppliedPluginRegistry;
import org.gradle.plugin.use.PluginId;
import org.gradle.plugin.use.internal.DefaultPluginId;

import java.util.List;

/**
 * Creates plugin requests for the build scan plugin when {@code --scan} is detected.
 *
 * <ol>
 * <li>Plugin request is only added for the root project.</li>
 * <li>No request is added if the project has already requested the build scan plugin, via {@code plugins} or {@code buildscript}.</li>
 * <li>The plugin request is inserted before any other plugin requests.</li>
 * <li>A fixed version of the build scan plugin is requested.</li>
 * </ol>
 */
public class BuildScanAutoAppliedPluginHandler implements AutoAppliedPluginHandler {
    public static final PluginId BUILD_SCAN_PLUGIN_ID = DefaultPluginId.of("com.gradle.build-scan");
    public static final String BUILD_SCAN_PLUGIN_AUTO_APPLY_VERSION = AutoAppliedPluginRegistry.lookupVersion(BUILD_SCAN_PLUGIN_ID);
    private static final String BUILD_SCAN_PLUGIN_GROUP = "com.gradle";
    private static final String BUILD_SCAN_PLUGIN_NAME = "build-scan-plugin";

    private final StartParameter startParameter;

    public BuildScanAutoAppliedPluginHandler(StartParameter startParameter) {
        this.startParameter = startParameter;
    }

    @Override
    public PluginRequests create(PluginRequests initialRequests, Object pluginTarget) {
        if (!startParameter.isBuildScan()) {
            return createEmptyPluginRequests();
        }

        if (!isRootProject(pluginTarget)) {
            return createEmptyPluginRequests();
        }

        if (isPluginAlreadyRequested(initialRequests, (Project) pluginTarget)) {
            return createEmptyPluginRequests();
        }

        return createBuildScanPluginRequests();
    }

    private PluginRequests createEmptyPluginRequests() {
        return new DefaultPluginRequests(Lists.<PluginRequestInternal>newArrayList());
    }

    private PluginRequests createBuildScanPluginRequests() {
        List<PluginRequestInternal> copyRequests = Lists.newArrayList();
        copyRequests.add(new DefaultPluginRequest(BUILD_SCAN_PLUGIN_ID, BUILD_SCAN_PLUGIN_AUTO_APPLY_VERSION, true, 0, "auto-apply", null));
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

    private static boolean isPluginAlreadyRequested(PluginRequests requests, Project project) {
        // Build scan plugin applied via init script
        if (project.getPlugins().hasPlugin(BUILD_SCAN_PLUGIN_ID.getId())) {
            return true;
        }

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
