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

package org.gradle.integtests.fixtures.executer

import spock.lang.Specification

/**
 * by Szczepan Faber, created at: 12/21/12
 */
class OutputScraperTest extends Specification {

    def output = """Included projects: [root project 'unknown-test-122', project ':api', project ':impl', project ':util']
Evaluating root project 'unknown-test-122' using build file '/Users/szczepan/gradle/gradle.src/build/tmp/test files/AbstractIntegrationSpec/unknown-test-122/build.gradle'.
Evaluating project ':api' using build file '/Users/szczepan/gradle/gradle.src/build/tmp/test files/AbstractIntegrationSpec/unknown-test-122/api/build.gradle'.
Selected primary task ':api:build'
Evaluating project ':foo:bar' using build file '/Users/szczepan/gradle/gradle.src/build/tmp/test files/AbstractIntegrationSpec/unknown-test-122/api/build.gradle'.
Selected primary task ':foo:bar:build'
All projects evaluated.
Tasks to be executed: ...
"""

    def "finds evaluated projects"() {
        expect:
        new OutputScraper("").evaluatedProjects == []
        new OutputScraper(output).evaluatedProjects == [':', ':api', ':foo:bar']
    }

    def "sucessfully asserts projects evaluated"() {
        expect:
        new OutputScraper("").assertProjectsEvaluated([])
        new OutputScraper(output).assertProjectsEvaluated([':', ':api', ':foo:bar'])
    }

    def "asserts projects evaluated"() {
        when:
        new OutputScraper(output).assertProjectsEvaluated(expected)
        then:
        thrown(AssertionError)

        where:
        expected << [
            [':', ':api', ':foo:bar', 'extra'], //extra
            ['extra', ':', ':api', ':foo:bar'], //extra
            [':api', ':', ':foo:bar'],    //order
            ['root', ':api', ':foo:bar'], //no match
            []
        ]
    }

    def "provides decent exception when no evaluated projects found"() {
        when:
        new OutputScraper("").assertProjectsEvaluated([":"])
        then:
        def ex = thrown(AssertionError)
        ex.message == OutputScraper.EVAL_NOT_FOUND
    }

    def "all evaluated hook must be fired after all projects evaluated"() {
        def output = """...
Evaluating project ':foo' using build file...
All projects evaluated.
Evaluating project ':api' using build file...
...
"""

        when:
        new OutputScraper(output).assertProjectsEvaluated([":foo", ":api"])
        then:
        def ex = thrown(AssertionError)
        ex.message.contains OutputScraper.ALL_EVAL_ERROR
    }

    def "all evaluated hook must be fired after root project evaluated"() {
        def output = """...
All projects evaluated.
Evaluating root project 'foo' using build file...
...
"""

        when:
        new OutputScraper(output).assertProjectsEvaluated([":"])
        then:
        def ex = thrown(AssertionError)
        ex.message.contains OutputScraper.ALL_EVAL_ERROR
    }
}