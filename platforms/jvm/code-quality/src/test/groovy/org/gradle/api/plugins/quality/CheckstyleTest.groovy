/*
 * Copyright 2012 the original author or authors.
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

import org.gradle.api.reporting.Report
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

class CheckstyleTest extends Specification {
    def project = ProjectBuilder.builder().build()
    def checkstyle = project.tasks.create("checkstyle", Checkstyle)

    def "default configuration"() {
        expect:
        with(checkstyle) {
            checkstyleClasspath.empty
            classpath.empty
            configFile.getOrNull() == null
            config == null
            configProperties.get() == [:]
            !reports.xml.required.get()
            !reports.xml.outputLocation.isPresent()
            reports.xml.outputType == Report.OutputType.FILE
            !reports.html.required.get()
            !reports.html.outputLocation.isPresent()
            reports.html.outputType == Report.OutputType.FILE
            !reports.sarif.required.get()
            !reports.sarif.outputLocation.isPresent()
            reports.sarif.outputType == Report.OutputType.FILE
            !ignoreFailures
            showViolations
            maxErrors.get() == 0
            maxWarnings.get() == Integer.MAX_VALUE
            !minHeapSize.isPresent()
            !maxHeapSize.isPresent()
        }
    }

    def "can use legacy configFile property"() {
        checkstyle.configFile = project.file("config/file.txt")

        expect:
        checkstyle.configFile.getOrNull().getAsFile() == project.file("config/file.txt")
        checkstyle.config.inputFiles.singleFile == project.file("config/file.txt")
    }
}
