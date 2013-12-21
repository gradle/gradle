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
    def "default configuration"() {
        def project = ProjectBuilder.builder().build()
        def checkstyle = project.tasks.create("checkstyle", Checkstyle)

        expect:
        with(checkstyle) {
            checkstyleClasspath == null
            classpath == null
            configFile == null
            configProperties == [:]
            !reports.xml.enabled
            reports.xml.destination == null
            reports.xml.outputType == Report.OutputType.FILE
            !ignoreFailures
            showViolations
        }
    }
}
