/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.plugins;

import org.gradle.api.Project;
import org.gradle.api.reporting.ReportingExtension;
import org.gradle.util.WrapUtil;

import java.io.File;
import java.util.Set;

/**
 * The conventional configuration for the `ProjectReportsPlugin`.
 */
public class ProjectReportsPluginConvention {
    private String projectReportDirName = "project";
    private final Project project;

    public ProjectReportsPluginConvention(Project project) {
        this.project = project;
    }

    /**
     * The name of the directory to generate the project reports into, relative to the project's reports dir.
     */
    public String getProjectReportDirName() {
        return projectReportDirName;
    }

    public void setProjectReportDirName(String projectReportDirName) {
        this.projectReportDirName = projectReportDirName;
    }

    /**
     * Returns the directory to generate the project reports into.
     */
    public File getProjectReportDir() {
        return project.getExtensions().getByType(ReportingExtension.class).file(projectReportDirName);
    }

    public Set<Project> getProjects() {
        return WrapUtil.toSet(project);
    }
}
