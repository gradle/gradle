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

package org.gradle.api.tasks.testing;

import org.gradle.api.Incubating;
import org.jspecify.annotations.NullMarked;

import java.util.List;

/**
 * Options for creating a test event reporter with
 * {@link TestEventReporterFactory#createTestEventReporter(String, org.gradle.api.file.Directory, org.gradle.api.file.Directory, org.gradle.api.Action)}.
 *
 * <p>
 * The options are read once the configuration action returns. Changes made after that have no effect.
 * </p>
 *
 * @since 9.10.0
 */
@Incubating
@NullMarked
public interface TestEventReporterSpec {
    /**
     * Sets if the reporter should throw upon close if the root node has been failed, or do nothing.
     *
     * <p>
     * Defaults to {@code true}.
     * </p>
     *
     * @param closeThrowsOnTestFailures {@code true} to throw upon close if the root node has been failed
     * @since 9.10.0
     */
    void setCloseThrowsOnTestFailures(boolean closeThrowsOnTestFailures);

    /**
     * Sets the contributors of custom entries in the HTML test report.
     *
     * <p>
     * Defaults to no contributors.
     * </p>
     *
     * @param htmlReportEntryContributors the contributors, which replace any previously set
     * @since 9.10.0
     */
    void setHtmlReportEntryContributors(List<? extends TestReportEntryContributor> htmlReportEntryContributors);
}
