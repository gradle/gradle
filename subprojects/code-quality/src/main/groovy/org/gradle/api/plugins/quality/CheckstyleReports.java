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

package org.gradle.api.plugins.quality;

import org.gradle.api.reporting.CustomizableHtmlReport;
import org.gradle.api.reporting.ReportContainer;
import org.gradle.api.reporting.SingleFileReport;
import org.gradle.api.tasks.Internal;

/**
 * The reporting configuration for the {@link Checkstyle} task.
 */
public interface CheckstyleReports extends ReportContainer<SingleFileReport> {

    /**
     * The checkstyle HTML report.
     * <p>
     * This report IS enabled by default.
     * <p>
     * Enabling this report will also cause the XML report to be generated, as the HTML is derived from the XML.
     *
     * @return The checkstyle HTML report
     * @since 2.10
     */
    @Internal
    CustomizableHtmlReport getHtml();

    /**
     * The checkstyle XML report
     * <p>
     * This report IS enabled by default.
     *
     * @return The checkstyle XML report
     */
    @Internal
    SingleFileReport getXml();
}
