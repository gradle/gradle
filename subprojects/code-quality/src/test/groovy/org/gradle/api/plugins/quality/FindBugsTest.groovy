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
import org.gradle.api.Project
import org.gradle.api.GradleException

class FindBugsTest extends Specification {
    private FindBugs findbugs

    def setup() {
        Project project = ProjectBuilder.builder().build()
        findbugs = project.task("findbugTask", type: FindBugs)
    }

    def "errorCount > 0 causes failing FindBugsTask"() {
        setup:
        FindBugsResult result = Mock(FindBugsResult)
        result.errorCount >> 1
        when:
        findbugs.evaluateResult(result)
        then:
        def e = thrown(GradleException)
        e.message == "FindBugs encountered an error. Run with --debug to get more information."
    }

    def "bugsCount > 0 does causes failing FindBugsTask"() {
        setup:
        FindBugsResult result = Mock(FindBugsResult)
        result.bugCount >> 1
        when:
        findbugs.evaluateResult(result)
        then:
        def e = thrown(GradleException)
        e.message == "FindBugs rule violations were found."
    }

    def "task not failing for bugsCount>0 when ignoreFailures is set to true"() {
        setup:
        FindBugsResult result = Mock(FindBugsResult)
        result.bugCount >> 1
        when:
        findbugs.ignoreFailures = true
        then:
        findbugs.evaluateResult(result)
    }

    def "task error message refer to findbugs reports if reports are configured"() {
        setup:
        FindBugsResult result = Mock(FindBugsResult)
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
        e.message.startsWith("FindBugs rule violations were found. See the report at")
    }

    def "ignoreFailures flag is ignored for errorCount"() {
        setup:
        FindBugsResult result = Mock(FindBugsResult)
        result.errorCount >> 1
        findbugs.ignoreFailures = true
        when:
        findbugs.evaluateResult(result)
        then:
        def e = thrown(GradleException)
        e.message == "FindBugs encountered an error. Run with --debug to get more information."
    }

    /**
     * keep behaviour in sync with findbugs ant task
     * */
    def "missingClassCount > 0 does not cause failing FindBugsTask"() {
        setup:
        FindBugsResult result = Mock(FindBugsResult)
        result.missingClassCount >> 1

        expect:
        findbugs.evaluateResult(result);
    }
}
