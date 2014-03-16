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

import org.gradle.api.internal.file.FileLookup;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.reporting.ReportingExtension;
import org.gradle.util.DeprecationLogger;

import java.io.File;
import java.util.concurrent.Callable;

/**
 * <p>A {@code BasePluginConvention} defines the convention properties and methods used by the {@link
 * ReportingBasePlugin}.</p>
 * <p>
 * This convention has been deprecated. Use the reporting extension instead:
 * </p>
 * <pre>
 * reporting {
 *     baseDir "the-reports"
 * }
 * </pre>
 *
 * @deprecated This convention has been deprecated and replaced by {@link ReportingExtension}
 */
@Deprecated
public class ReportingBasePluginConvention {
    private ReportingExtension extension;
    private final ProjectInternal project;

    public ReportingBasePluginConvention(ProjectInternal project, ReportingExtension extension) {
        this.project = project;
        this.extension = extension;
    }

    /**
     * Returns the name of the reports directory, relative to the project's build directory.
     *
     * @deprecated use {@link org.gradle.api.reporting.ReportingExtension#getBaseDir()}
     * @return The reports directory name. Never returns null.
     */
    @Deprecated
    public String getReportsDirName() {
        DeprecationLogger.nagUserOfReplacedProperty("reportsDirName", "reporting.baseDir");
        return extension.getBaseDir().getName();
    }

    /**
     * Sets the name of the reports directory, relative to the project's build directory.
     *
     * @deprecated use {@link ReportingExtension#setBaseDir(Object)}
     * @param reportsDirName The reports directory name. Should not be null.
     */
    @Deprecated
    public void setReportsDirName(final String reportsDirName) {
        DeprecationLogger.nagUserOfReplacedProperty("reportsDirName", "reporting.baseDir");
        extension.setBaseDir(new Callable<File>() {
            public File call() throws Exception {
                return project.getServices().get(FileLookup.class).getFileResolver(project.getBuildDir()).resolve(reportsDirName);
            }
        });
    }

    /**
     * Returns the directory containing all reports for this project.
     *
     * @deprecated use {@link org.gradle.api.reporting.ReportingExtension#getBaseDir()}
     * @return The reports directory. Never returns null.
     */
    @Deprecated
    public File getReportsDir() {
        DeprecationLogger.nagUserOfReplacedProperty("reportsDir", "reporting.baseDir");
        return extension.getBaseDir();
    }

    /**
     * Returns the title for API documentation for the project.
     *
     * @deprecated use {@link org.gradle.api.reporting.ReportingExtension#getApiDocTitle()}
     * @return The title. Never returns null.
     */
    @Deprecated
    public String getApiDocTitle() {
        DeprecationLogger.nagUserOfReplacedProperty("apiDocTitle", "reporting.apiDocTitle");
        return extension.getApiDocTitle();
    }
}
