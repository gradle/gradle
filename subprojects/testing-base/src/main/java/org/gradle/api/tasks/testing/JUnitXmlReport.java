/*
 * Copyright 2013 the original author or authors.
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

import org.gradle.api.provider.Property;
import org.gradle.api.reporting.DirectoryReport;
import org.gradle.api.tasks.Input;

/**
 * The JUnit XML files, commonly used to communicate results to CI servers.
 *
 * @see TestTaskReports#getJunitXml()
 */
public interface JUnitXmlReport extends DirectoryReport {

    /**
     * Should the output be associated with individual test cases instead of at the suite level.
     */
    @Input
    boolean isOutputPerTestCase();

    /**
     * Should the output be associated with individual test cases instead of at the suite level.
     */
    void setOutputPerTestCase(boolean outputPerTestCase);

    /**
     * Whether reruns or retries of a test should be merged into a combined testcase.
     *
     * When enabled, the XML output will be very similar to the surefire plugin of Apache Maven™ when enabling reruns.
     * If a test fails but is then retried and succeeds, its failures will be recorded as {@code <flakyFailure>}
     * instead of {@code <failure>}, within one {@code <testcase>}.
     * This can be important for build tooling that uses this XML to understand test results,
     * and where distinguishing such passed-on-retry outcomes is important.
     * This is the case for the Jenkins CI server and its Flaky Test Handler plugin.
     *
     * This value defaults to {@code false}, causing each test execution to be a discrete {@code <testcase>}.
     *
     * @see <a href="https://maven.apache.org/components/surefire/maven-surefire-plugin/examples/rerun-failing-tests.html">https://maven.apache.org/components/surefire/maven-surefire-plugin/examples/rerun-failing-tests.html</a>
     * @see <a href="https://plugins.jenkins.io/flaky-test-handler">https://plugins.jenkins.io/flaky-test-handler</a>
     * @since 6.8
     */
    @Input
    Property<Boolean> getMergeReruns();

}
