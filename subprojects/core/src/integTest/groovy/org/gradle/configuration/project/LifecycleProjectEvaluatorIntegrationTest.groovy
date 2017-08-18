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
import spock.lang.Unroll

class LifecycleProjectEvaluatorIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        settingsFile << "rootProject.name='root'"
    }

    @Unroll
    def "if two exceptions occur, prints both without stacktrace (with message=#message)"() {
        given:
        buildFile << """
            afterEvaluate { throw new RuntimeException(${message? "'$message'" :''}) }
            throw new RuntimeException("configure failure")
        """
        executer.withStacktraceDisabled()

        when:
        fails 'help'

        then:
        errorOutput.contains("Failed to notify ProjectEvaluationListener.afterEvaluate(), but primary configuration failure takes precedence.\n> ${message? message : "RuntimeException"}")
        !errorOutput.contains("java.lang.RuntimeException: after evaluate failure")
        errorOutput.contains("* What went wrong:\nA problem occurred evaluating root project 'root'.\n> configure failure")
        !errorOutput.contains("* Exception is:\norg.gradle.api.GradleScriptException: A problem occurred evaluating root project 'root'.")

        where:
        message                    | _
        "after evaluate failure"   | _
        null                       | _
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
        errorOutput.contains("Failed to notify ProjectEvaluationListener.afterEvaluate(), but primary configuration failure takes precedence.\njava.lang.RuntimeException: after evaluate failure")
        errorOutput.contains("* What went wrong:\nA problem occurred evaluating root project 'root'.\n> configure failure")
        errorOutput.contains("* Exception is:\norg.gradle.api.GradleScriptException: A problem occurred evaluating root project 'root'.")
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
        !errorOutput.contains("Failed to notify ProjectEvaluationListener.afterEvaluate(), but primary configuration failure takes precedence.")
        errorOutput.contains("* What went wrong:\nA problem occurred configuring root project 'root'.\n> after evaluate failure")
    }
}
