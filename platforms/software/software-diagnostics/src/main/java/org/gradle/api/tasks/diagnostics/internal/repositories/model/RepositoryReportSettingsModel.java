/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.api.tasks.diagnostics.internal.repositories.model;

import com.google.common.collect.ImmutableList;
import org.jspecify.annotations.NullMarked;

import java.util.List;

/**
 * Settings-level slice of the repositories report: holds the three settings-scoped
 * repository buckets in the same order that Gradle actually resolves them during a build
 * (settings.buildscript, pluginManagement, dependencyResolutionManagement).
 */
@NullMarked
public final class RepositoryReportSettingsModel {
    private final List<ReportRepository> settingsBuildscriptRepositories;
    private final List<ReportRepository> pluginManagementRepositories;
    private final List<ReportRepository> dependencyResolutionManagementRepositories;

    public RepositoryReportSettingsModel(
        List<ReportRepository> settingsBuildscriptRepositories,
        List<ReportRepository> pluginManagementRepositories,
        List<ReportRepository> dependencyResolutionManagementRepositories
    ) {
        this.settingsBuildscriptRepositories = ImmutableList.copyOf(settingsBuildscriptRepositories);
        this.pluginManagementRepositories = ImmutableList.copyOf(pluginManagementRepositories);
        this.dependencyResolutionManagementRepositories = ImmutableList.copyOf(dependencyResolutionManagementRepositories);
    }

    public List<ReportRepository> getSettingsBuildscriptRepositories() {
        return settingsBuildscriptRepositories;
    }

    public List<ReportRepository> getPluginManagementRepositories() {
        return pluginManagementRepositories;
    }

    public List<ReportRepository> getDependencyResolutionManagementRepositories() {
        return dependencyResolutionManagementRepositories;
    }
}
