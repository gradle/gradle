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

import org.gradle.api.reporting.ReportContainer;
import org.gradle.api.reporting.SingleFileReport;

/**
 * The reporting configuration for the {@link FindBugs} task.
 *
 * Only one of the reports can be enabled when the task executes. If more than one is enabled, an {@link org.gradle.api.InvalidUserDataException}
 * will be thrown.
 */
public interface FindBugsReports extends ReportContainer<SingleFileReport> {

    /**
     * The findbugs XML report
     *
     * @return The findbugs XML report
     */
    FindBugsXmlReport getXml();

    /**
     * The findbugs HTML report
     *
     * @return The findbugs HTML report
     */
    SingleFileReport getHtml();
    
    /**
     * The findbugs Text report
     *
     * @return The findbugs Text report
     */
    SingleFileReport getText();
    
    /**
     * The findbugs Emacs report
     *
     * @return The findbugs Emacs report
     */
    SingleFileReport getEmacs();
}
