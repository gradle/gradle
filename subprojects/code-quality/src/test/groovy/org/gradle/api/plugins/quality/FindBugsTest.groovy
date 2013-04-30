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

import spock.lang.Specification

import org.gradle.api.plugins.quality.internal.findbugs.FindBugsResult
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.api.GradleException

class FindBugsTest extends Specification {
    private FindBugs findbugs

    def setup() {
        def project = ProjectBuilder.builder().build()
        findbugs = project.tasks.create("findbugs", FindBugs)
    }

    def "fails when errorCount greater than zero"() {
        def result = Mock(FindBugsResult)
        result.errorCount >> 1

        when:
        findbugs.evaluateResult(result)

        then:
        def e = thrown(GradleException)
        e.message == "FindBugs encountered an error. Run with --debug to get more information."
    }

    def "fails when bugCount greater than zero"() {
        def result = Mock(FindBugsResult)
        result.bugCount >> 1

        when:
        findbugs.evaluateResult(result)

        then:
        def e = thrown(GradleException)
        e.message == "FindBugs rule violations were found."
    }

    def "fails when errorCount greater than zero and ignoreFailures is true"() {
        def result = Mock(FindBugsResult)
        result.errorCount >> 1
        findbugs.ignoreFailures = true

        when:
        findbugs.evaluateResult(result)

        then:
        def e = thrown(GradleException)
        e.message == "FindBugs encountered an error. Run with --debug to get more information."
    }

    def "does not fail when bugCount greater than zero and ignoreFailures is true"() {
        def result = Mock(FindBugsResult)
        result.bugCount >> 1
        findbugs.ignoreFailures = true

        when:
        findbugs.evaluateResult(result)

        then:
        noExceptionThrown()
    }

    def "error message refers to report if report is configured"() {
        def result = Mock(FindBugsResult)
        result.bugCount >> 1
        findbugs.reports {
            xml {
                enabled = true
                destination "build/findbugs.xml"
            }
        }

        when:
        findbugs.evaluateResult(result)

        then:
        def e = thrown(GradleException)
        e.message.startsWith("FindBugs rule violations were found. See the report at:")
    }

    def "does not fail when missingClassCount greater than zero (keeps behavior in sync with Ant task)"() {
        def result = Mock(FindBugsResult)
        result.missingClassCount >> 1

        when:
        findbugs.evaluateResult(result)

        then:
        noExceptionThrown()
    }
}
