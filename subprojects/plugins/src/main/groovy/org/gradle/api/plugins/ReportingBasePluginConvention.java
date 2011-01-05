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
package org.gradle.api.plugins;

import org.gradle.api.Project;
import org.gradle.api.internal.project.ProjectInternal;

import java.io.File;

/**
 * <p>A {@code BasePluginConvention} defines the convention properties and methods used by the {@link
 * ReportingBasePlugin}.</p>
 */
public class ReportingBasePluginConvention {
    private String reportsDirName = "reports";
    private ProjectInternal project;

    public ReportingBasePluginConvention(ProjectInternal project) {
        this.project = project;
    }

    /**
     * Returns the name of the reports directory, relative to the project's build directory.
     *
     * @return The reports directory name. Never returns null.
     */
    public String getReportsDirName() {
        return reportsDirName;
    }

    /**
     * Sets the name of the reports directory, relative to the project's build directory.
     *
     * @param reportsDirName The reports directory name. Should not be null.
     */
    public void setReportsDirName(String reportsDirName) {
        this.reportsDirName = reportsDirName;
    }

    /**
     * Returns the directory containing all reports for this project.
     *
     * @return The reports directory. Never returns null.
     */
    public File getReportsDir() {
        return project.getFileResolver().withBaseDir(project.getBuildDir()).resolve(reportsDirName);
    }

    /**
     * Returns the title for API documentation for the project.
     *
     * @return The title. Never returns null.
     */
    public String getApiDocTitle() {
        Object version = project.getVersion();
        if (Project.DEFAULT_VERSION.equals(version)) {
            return String.format("%s API", project.getName());
        } else {
            return String.format("%s %s API", project.getName(), version);
        }
    }
}
