/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.internal.artifacts.configurations;

import org.gradle.api.internal.artifacts.configurations.ResolveConfigurationDependenciesBuildOperationType.Repository;

import javax.annotation.Nullable;
import java.util.List;

class ResolveConfigurationResolutionBuildOperationDetails implements ResolveConfigurationDependenciesBuildOperationType.Details {

    private final String configurationName;
    private final boolean isScriptConfiguration;
    private final String configurationDescription;
    private final String buildPath;
    private final String projectPath;
    private final boolean isConfigurationVisible;
    private final boolean isConfigurationTransitive;
    private final List<Repository> repositories;

    ResolveConfigurationResolutionBuildOperationDetails(
        String configurationName,
        boolean isScriptConfiguration,
        String configurationDescription,
        String buildPath,
        String projectPath, boolean isConfigurationVisible,
        boolean isConfigurationTransitive,
        List<Repository> repositories
    ) {
        this.configurationName = configurationName;
        this.isScriptConfiguration = isScriptConfiguration;
        this.configurationDescription = configurationDescription;
        this.buildPath = buildPath;
        this.projectPath = projectPath;
        this.isConfigurationVisible = isConfigurationVisible;
        this.isConfigurationTransitive = isConfigurationTransitive;
        this.repositories = repositories;
    }

    @Override
    public String getConfigurationName() {
        return configurationName;
    }

    @Nullable
    @Override
    public String getProjectPath() {
        return projectPath;
    }

    @Override
    public boolean isScriptConfiguration() {
        return isScriptConfiguration;
    }

    @Override
    public String getConfigurationDescription() {
        return configurationDescription;
    }

    @Override
    public String getBuildPath() {
        return buildPath;
    }

    @Override
    public boolean isConfigurationVisible() {
        return isConfigurationVisible;
    }

    @Override
    public boolean isConfigurationTransitive() {
        return isConfigurationTransitive;
    }

    @Override
    public List<Repository> getRepositories() {
        return repositories;
    }
}
