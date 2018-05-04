/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.configuration.project

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class LifecycleProjectEvaluatorIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        settingsFile << "rootProject.name='root'"
    }

    def "nested afterEvaluate is honored asynchronously"() {
        given:
        buildFile << """
            afterEvaluate {
                println "> Outer"
                afterEvaluate {
                    println "Inner"
                }
                println "< Outer"
            }
        """

        when:
        succeeds 'help'

        then:
        output =~ /> Outer\s+< Outer\s+Inner/
    }

    def "if two exceptions occur, prints an info about both without stacktrace"() {
        given:
        buildFile << """
            afterEvaluate { throw new RuntimeException("after evaluate failure") }
            throw new RuntimeException("configure failure")
        """
        executer.withStacktraceDisabled()

        when:
        fails 'help'

        then:
        failure.assertHasErrorOutput("Project evaluation failed including an error in afterEvaluate {}. Run with --stacktrace for details of the afterEvaluate {} error.")
        failure.assertNotOutput("after evaluate failure")
        !failure.hasErrorOutput("configure failure")
        failure.assertHasDescription("A problem occurred evaluating root project 'root'.")
        failure.assertHasCause("configure failure")
        failure.assertHasNoCause("after evaluate failure")
    }

    def "if two exceptions occur with --stacktrace, prints both with stacktrace"() {
        given:
        buildFile << """
            afterEvaluate { throw new RuntimeException("after evaluate failure") }
            throw new RuntimeException("configure failure")
        """
        executer.withStackTraceChecksDisabled()

        when:
        fails 'help'

        then:
        failure.assertHasErrorOutput("Project evaluation failed including an error in afterEvaluate {}.\njava.lang.RuntimeException: after evaluate failure")
        !failure.hasErrorOutput("configure failure")
        failure.assertHasDescription("A problem occurred evaluating root project 'root'.")
        failure.assertHasCause("configure failure")
        failure.assertHasNoCause("after evaluate failure")
    }

    def "if only one exception occurs in afterEvaluate, prints it as primary"() {
        given:
        buildFile << """
            afterEvaluate { throw new RuntimeException("after evaluate failure") }
        """
        executer.withStacktraceDisabled()

        when:
        fails 'help'

        then:
        failure.assertNotOutput("Project evaluation failed including an error in afterEvaluate {}.")
        failure.assertHasDescription("A problem occurred configuring root project 'root'.")
        failure.assertHasCause("after evaluate failure")
    }
}
