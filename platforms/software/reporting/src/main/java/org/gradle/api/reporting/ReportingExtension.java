/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.reporting;

import org.gradle.api.Action;
import org.gradle.api.ExtensiblePolymorphicDomainObjectContainer;
import org.gradle.api.Incubating;
import org.gradle.api.Project;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.reporting.internal.ReportUtilities;
import org.gradle.internal.deprecation.DeprecationLogger;
import org.gradle.internal.instrumentation.api.annotations.NotToBeReplacedByLazyProperty;

import javax.inject.Inject;
import java.io.File;

/**
 * A project extension named "reporting" that provides basic reporting settings and utilities.
 * <p>
 * Example usage:
 * <pre class='autoTested'>
 * plugins {
 *     id("org.gradle.reporting-base")
 * }
 *
 * reporting {
 *     // change the base directory where all reports are generated
 *     baseDirectory = layout.buildDirectory.dir("our-reports")
 * }
 *
 * // A directory for test reports
 * reporting.baseDirectory.dir("test-reports")
 *
 * // A report file
 * reporting.baseDirectory.file("index.html")
 * </pre>
 * <p>
 * When implementing a task that produces reports, the location of where to generate reports should be obtained from {@link #getBaseDirectory()}.
 */
public abstract class ReportingExtension {

    /**
     * The name of this extension ("{@value}")
     */
    public static final String NAME = "reporting";

    /**
     * The default name of the base directory for all reports, relative to {@link org.gradle.api.file.ProjectLayout#getBuildDirectory()} ({@value}).
     */
    public static final String DEFAULT_REPORTS_DIR_NAME = "reports";

    private final Project project;

    @Inject
    public ReportingExtension(Project project) {
        this.project = project;
    }

    /**
     * Returns base directory property to use for all reports.
     *
     * @since 4.4
     */
    public abstract DirectoryProperty getBaseDirectory();

    /**
     * Creates a file object for the given path, relative to {@link #getBaseDirectory()}.
     * <p>
     * The reporting base dir can be changed, so users of this method should use it on demand where appropriate.
     *
     * @param path the relative path
     * @return a file object at the given path relative to {@link #getBaseDirectory()}.
     *
     * @deprecated Use {@code getBaseDirectory().file(path)} or {@code getBaseDirectory().dir(path)} instead.
     *
     * @see DirectoryProperty#file(String)
     * @see DirectoryProperty#dir(String)
     */
    @Deprecated
    public File file(String path) {
        DeprecationLogger.deprecateMethod(ReportingExtension.class, "file(String)")
            .replaceWith("getBaseDirectory().file(String) or getBaseDirectory().dir(String)")
            .willBeRemovedInGradle10()
            .withUpgradeGuideSection(9, "reporting_extension_file")
            .nagUser();
        return getBaseDirectory().file(path).get().getAsFile();
    }

    /**
     * Provides a default title for API documentation based on the project's name and version.
     *
     * @deprecated Use your own way of generating a title for API documentation.
     */
    @NotToBeReplacedByLazyProperty(because="this method is deprecated")
    @Deprecated
    public String getApiDocTitle() {
        DeprecationLogger.deprecateMethod(ReportingExtension.class, "getApiDocTitle()")
            .willBeRemovedInGradle10()
            .withUpgradeGuideSection(9, "reporting_extension_api_doc_title")
            .nagUser();
        return ReportUtilities.getApiDocTitleFor(project);
    }

    /**
     * Container for aggregation reports, which may be configured automatically in reaction to the presence of the jvm-test-suite plugin.
     *
     * @return A container of known aggregation reports
     * @since 7.4
     */
    @Incubating
    public abstract ExtensiblePolymorphicDomainObjectContainer<ReportSpec> getReports();

    /**
     * Add more reports or configure the available reports.
     *
     * @param action configuration action for the reports container
     * @since 9.1.0
     */
    @Incubating
    public void reports(Action<? super ExtensiblePolymorphicDomainObjectContainer<ReportSpec>> action) {
        action.execute(getReports());
    }
}
