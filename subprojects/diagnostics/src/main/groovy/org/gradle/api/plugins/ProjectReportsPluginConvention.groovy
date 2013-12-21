/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.plugins

import org.gradle.api.Project
import org.gradle.api.reporting.ReportingExtension
import org.gradle.util.WrapUtil

public class ProjectReportsPluginConvention {
    /**
     * The name of the directory to generate the project reports into, relative to the project's reports dir.
     */
    String projectReportDirName = 'project'
    private final Project project

    def ProjectReportsPluginConvention(Project project) {
        this.project = project;
    }

    /**
     * Returns the directory to generate the project reports into.
     */
    File getProjectReportDir() {
        project.extensions.getByType(ReportingExtension).file(projectReportDirName)
    }

    Set<Project> getProjects() {
        WrapUtil.toSet(project)
    }
}