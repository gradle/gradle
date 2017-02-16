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

import org.gradle.BuildAdapter;
import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.invocation.Gradle;
import org.gradle.api.specs.Spec;
import org.gradle.util.CollectionUtils;

public final class BuildScanRequestListener extends BuildAdapter {

    private static final String BUILD_SCAN_PLUGIN_NAME_PATTERN = "com\\.gradle\\..*\\.BuildScanPlugin";

    @Override
    public void projectsEvaluated(Gradle gradle) {
        Project rootProject = gradle.getRootProject();
        if(!hasBuildScanPluginApplied(rootProject)){
            throw new GradleException("Build scan cannot be created since the build scan plugin has not been applied.\n"
                + "For more information on how to apply the build scan plugin, please visit https://gradle.com/get-started.");
        }
    }

    private boolean hasBuildScanPluginApplied(Project rootProject) {
        return CollectionUtils.any(rootProject.getPlugins(), new Spec<Plugin>() {
            @Override
            public boolean isSatisfiedBy(Plugin plugin) {
                return plugin.getClass().getName().matches(BUILD_SCAN_PLUGIN_NAME_PATTERN);
            }
        });
    }

}
