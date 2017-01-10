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

package org.gradle.internal.scan;

import org.gradle.BuildListener;
import org.gradle.BuildResult;
import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.initialization.Settings;
import org.gradle.api.invocation.Gradle;
import org.gradle.api.specs.Spec;
import org.gradle.util.CollectionUtils;

public class BuildScanRequestEvaluationListener implements BuildListener {

    public static final String BUILD_SCAN_PLUGIN_NAME = "BuildScanPlugin";

    public BuildScanRequestEvaluationListener() {
    }

    @Override
    public void buildStarted(Gradle gradle) {
    }

    @Override
    public void settingsEvaluated(Settings settings) {
    }

    @Override
    public void projectsLoaded(Gradle gradle) {
    }

    @Override
    public void projectsEvaluated(Gradle gradle) {
        Project rootProject = gradle.getRootProject();
        if(!hasBuildScanPluginApplied(rootProject)){
            throw new GradleException("Build scan cannot be requested as build scan plugin is not applied.\n"
                + "For more information, please visit: https://gradle.com/get-started");
        }
    }

    private boolean hasBuildScanPluginApplied(Project rootProject) {
        return CollectionUtils.any(rootProject.getPlugins(), new Spec<Plugin>() {
            @Override
            public boolean isSatisfiedBy(Plugin plugin) {
                return plugin.getClass().getName().endsWith(BUILD_SCAN_PLUGIN_NAME);
            }
        });
    }

    @Override
    public void buildFinished(BuildResult result) {

    }
}
