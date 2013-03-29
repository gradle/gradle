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

package org.gradle.testing.jacoco.tasks;

import org.gradle.api.Incubating;
import org.gradle.api.reporting.ReportContainer;
import org.gradle.api.reporting.SingleFileReport;

/**
 * The reporting configuration for the the {@link JacocoReport} task.
 */
@Incubating
public interface JacocoReportsContainer extends ReportContainer<SingleFileReport> {
    /**
     * The jacoco (single file) HTML report
     *
     * @return The jacoco (single file) HTML report
     */
    SingleFileReport getHtml();

    /**
     * The jacoco (single file) XML report
     *
     * @return The jacoco (single file) XML report
     */
    SingleFileReport getXml();

    /**
     * The jacoco (single file) CSV report
     *
     * @return The jacoco (single file) CSV report
     */
    SingleFileReport getCsv();
}
