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
import org.gradle.api.tasks.Internal;

/**
 * The reporting configuration for the {@link JDepend} task.
 *
 * Exactly one of the XML or HTML reports can be enabled when the task executes. If more than one or none is enabled, an {@link org.gradle.api.InvalidUserDataException}
 * will be thrown.
 */
public interface JDependReports extends ReportContainer<SingleFileReport> {

    /**
     * The jdepend XML report
     *
     * @return The jdepend XML report
     */
    @Internal
    SingleFileReport getXml();

    /**
     * The jdepend text report
     *
     * @return The jdepend text report
     */
    @Internal
    SingleFileReport getText();
}
