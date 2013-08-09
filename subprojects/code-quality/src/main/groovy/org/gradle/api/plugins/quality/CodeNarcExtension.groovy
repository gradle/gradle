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
package org.gradle.api.plugins.quality

import org.gradle.api.InvalidUserDataException

class CodeNarcExtension extends CodeQualityExtension {
    /**
     * The CodeNarc configuration file to use.
     */
    File configFile

    /**
     * The maximum number of priority 1 violations allowed before failing the build.
     */
    int maxPriority1Violations

    /**
     * The maximum number of priority 2 violations allowed before failing the build.
     */
    int maxPriority2Violations

    /**
     * The maximum number of priority 3 violations allowed before failing the build.
     */
    int maxPriority3Violations

    /**
     * The format type of the CodeNarc report. One of <tt>html</tt>, <tt>xml</tt>, <tt>text</tt>, <tt>console</tt>.
     */
    String reportFormat

    void setReportFormat(String reportFormat) {
        if (reportFormat in ["xml", "html", "console", "text"]) {
            this.reportFormat = reportFormat    
        } else {
            throw new InvalidUserDataException("'$reportFormat' is not a valid codenarc report format")
        }
    }
}
