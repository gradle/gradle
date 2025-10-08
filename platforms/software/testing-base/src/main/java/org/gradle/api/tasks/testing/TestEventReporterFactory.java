/*
 * Copyright 2024 the original author or authors.
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
import org.gradle.api.file.Directory;
import org.gradle.internal.HasInternalProtocol;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

/**
 * A service that provides access to the test event reporting API.
 *
 * @since 8.12
 */
@ServiceScope(Scope.Build.class)
@Incubating
@HasInternalProtocol
public interface TestEventReporterFactory {
    /**
     * Returns an object that can be used to report test events with the default behavior of failing the test task if there are test failures.
     *
     * @param rootName the name for the root node of the test tree
     * @param binaryResultsDirectory the directory to write binary test results to
     * @param htmlReportDirectory the directory to write HTML test reports to
     *
     * @return the test event reporter
     *
     * @since 8.13
     */
    default GroupTestEventReporter createTestEventReporter(
        String rootName,
        Directory binaryResultsDirectory,
        Directory htmlReportDirectory
    ) {
        return createTestEventReporter(rootName, binaryResultsDirectory, htmlReportDirectory, true);
    }

    /**
     * Returns an object that can be used to report test events.
     *
     * @param rootName the name for the root node of the test tree
     * @param binaryResultsDirectory the directory to write binary test results to
     * @param htmlReportDirectory the directory to write HTML test reports to
     * @param closeThrowsOnTestFailures determines if this reporter should throw upon close if the root node has been failed, or do nothing
     *
     * @return the test event reporter
     *
     * @since 9.3.0
     */
    GroupTestEventReporter createTestEventReporter(
        String rootName,
        Directory binaryResultsDirectory,
        Directory htmlReportDirectory,
        boolean closeThrowsOnTestFailures
    );
}
