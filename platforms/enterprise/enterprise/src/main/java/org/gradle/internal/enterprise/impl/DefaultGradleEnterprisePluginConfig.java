/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.internal.enterprise.impl;

import org.gradle.StartParameter;
import org.gradle.api.internal.BuildType;
import org.gradle.internal.enterprise.GradleEnterprisePluginConfig;

public class DefaultGradleEnterprisePluginConfig implements GradleEnterprisePluginConfig {

    private final BuildScanRequest buildScanRequest;
    private final boolean taskExecutingBuild;
    private final boolean autoApplied;

    public DefaultGradleEnterprisePluginConfig(StartParameter startParameter, BuildType buildType, GradleEnterprisePluginAutoAppliedStatus autoAppliedStatus) {
        this.buildScanRequest = buildScanRequest(startParameter);
        this.taskExecutingBuild = buildType == BuildType.TASKS;
        this.autoApplied = autoAppliedStatus.isAutoApplied();
    }

    @Override
    public BuildScanRequest getBuildScanRequest() {
        return buildScanRequest;
    }

    @Override
    public boolean isTaskExecutingBuild() {
        return taskExecutingBuild;
    }

    @Override
    public boolean isAutoApplied() {
        return autoApplied;
    }

    private BuildScanRequest buildScanRequest(StartParameter startParameter) {
        if (startParameter.isNoBuildScan()) {
            return BuildScanRequest.SUPPRESSED;
        } else if (startParameter.isBuildScan()) {
            return BuildScanRequest.REQUESTED;
        } else {
            return BuildScanRequest.NONE;
        }
    }
}
