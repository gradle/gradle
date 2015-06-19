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

import org.gradle.api.GradleException
import org.gradle.api.plugins.quality.internal.findbugs.FindBugsResult
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

class FindBugsTest extends Specification {
    def project = ProjectBuilder.builder().build()
    FindBugs findbugs = project.tasks.create("findbugs", FindBugs)

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

    def "can use legacy includeFilter property"() {
        findbugs.includeFilter = project.file("config/file.txt")

        expect:
        findbugs.includeFilter == project.file("config/file.txt")
        findbugs.includeFilterConfig.inputFiles.singleFile == project.file("config/file.txt")
    }

    def "can use legacy excludeFilter property"() {
        findbugs.excludeFilter = project.file("config/file.txt")

        expect:
        findbugs.excludeFilter == project.file("config/file.txt")
        findbugs.excludeFilterConfig.inputFiles.singleFile == project.file("config/file.txt")
    }

    def "can use legacy excludeBugsFilter property"() {
        findbugs.excludeBugsFilter = project.file("config/file.txt")

        expect:
        findbugs.excludeBugsFilter == project.file("config/file.txt")
        findbugs.excludeBugsFilterConfig.inputFiles.singleFile == project.file("config/file.txt")
    }

    def "can add extra args"() {
        given:
        findbugs.extraArgs = [ 'abc' ]
        expect:
        findbugs.extraArgs == [ 'abc' ]

        when:
        findbugs.extraArgs 'def'
        then:
        findbugs.extraArgs == [ 'abc', 'def' ]

        when:
        findbugs.extraArgs([ 'ghi', 'jkl' ])
        then:
        findbugs.extraArgs == [ 'abc', 'def', 'ghi', 'jkl' ]
    }
}
