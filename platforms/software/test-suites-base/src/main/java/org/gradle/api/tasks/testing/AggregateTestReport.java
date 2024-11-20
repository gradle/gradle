/*
 * Copyright 2021 the original author or authors.
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

import org.gradle.api.Action;
import org.gradle.api.Buildable;
import org.gradle.api.Incubating;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.reporting.ReportSpec;

/**
 * A test report that aggregates the results of multiple test results.
 * <p>
 * This report is {@link Buildable}, meaning it wraps an executable operation that performs the aggregation.
 * <p>
 * This report is intended to be used as a finalizer, using the {@link org.gradle.api.Task#finalizedBy(Buildable, Action)}
 * method. This allows the aggregation to be performed after all test tasks have completed, and only
 * if the test tasks was executed. See the following example for how to use this report:
 *
 * <pre>
 *      plugins {
 *          id("test-suite-base")
 *      }
 *
 *      def report = reporting.reports.
 *
 *      tasks.register("myTest", Test) {
 *          finalizedBy(testing.results) {
 *              binaryResults.from(binaryResultsDirectory)
 *          }
 *      }
 * </pre>
 *
 * Running the {@code test} task will cause the results to be aggregated with any other
 * executed test and reported.
 *
 * @since 7.4
 */
@Incubating
public interface AggregateTestReport extends ReportSpec, Buildable {

    /**
     * The binary results that are to be aggregated and reported.
     *
     * @return the binary results.
     */
    ConfigurableFileCollection getBinaryTestResults();

    /**
     * The directory where the HTML report will be generated.
     *
     * @return the report directory.
     */
    DirectoryProperty getHtmlReportDirectory();

    /**
     * Contains a value representing the type of test suite this task belongs to.  See static constants on {@link org.gradle.api.attributes.TestSuiteType} for examples.
     * <p>
     * May be non-present, in which case cross-project test report auto-aggregation will not be performed.
     *
     * @return this report's test type
     */
    Property<String> getTestType();

}
