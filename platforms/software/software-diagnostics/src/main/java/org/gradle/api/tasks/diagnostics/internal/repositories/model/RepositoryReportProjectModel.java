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
import org.gradle.util.Path;
import org.jspecify.annotations.NullMarked;

import java.util.List;
import java.util.Objects;

@NullMarked
public final class RepositoryReportProjectModel {
    private final Path projectPath;
    private final String projectName;
    private final List<ReportRepository> buildscriptRepositories;
    private final List<ReportRepository> projectRepositories;

    public RepositoryReportProjectModel(
        Path projectPath,
        String projectName,
        List<ReportRepository> buildscriptRepositories,
        List<ReportRepository> projectRepositories
    ) {
        this.projectPath = Objects.requireNonNull(projectPath);
        this.projectName = Objects.requireNonNull(projectName);
        this.buildscriptRepositories = ImmutableList.copyOf(buildscriptRepositories);
        this.projectRepositories = ImmutableList.copyOf(projectRepositories);
    }

    public Path getProjectPath() {
        return projectPath;
    }

    public String getProjectName() {
        return projectName;
    }

    public List<ReportRepository> getBuildscriptRepositories() {
        return buildscriptRepositories;
    }

    public List<ReportRepository> getProjectRepositories() {
        return projectRepositories;
    }
}
