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

import org.gradle.api.reporting.DirectoryReport;
import org.gradle.api.reporting.Report;
import org.gradle.api.reporting.ReportContainer;
import org.gradle.api.tasks.Nested;

/**
 * The reports produced by the {@link Test} task.
 */
public interface TestTaskReports extends ReportContainer<Report> {

    /**
     * A HTML report indicate the results of the test execution.
     *
     * @return The HTML report
     */
    @Nested
    DirectoryReport getHtml();

    /**
     * The test results in “JUnit XML” format.
     *
     * @return The test results in “JUnit XML” format
     */
    @Nested
    JUnitXmlReport getJunitXml();

}
