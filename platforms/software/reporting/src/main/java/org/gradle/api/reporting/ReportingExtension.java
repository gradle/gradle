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
import org.gradle.api.file.DirectoryProperty;

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

    /**
     * Returns base directory property to use for all reports.
     *
     * @since 4.4
     */
    public abstract DirectoryProperty getBaseDirectory();

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
