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

package org.gradle.api.internal.buildevents

import org.gradle.api.logging.configuration.ConsoleOutput
import org.gradle.integtests.fixtures.AbstractIntegrationSpec

abstract class AbstractBuildResultLoggerFunctionalTest extends AbstractIntegrationSpec {
    def setup() {
        executer.withStackTraceChecksDisabled()
    }

    def "Failure status is logged even in --quiet"() {
        given:
        buildFile << "task fail { doFirst { assert false } }"

        when:
        executer.withConsole(consoleType)
        executer.withQuietLogging()
        fails('fail')

        then:
        result.output.contains(failureMessage)
    }

    def "Failure message is logged with appropriate styling"() {
        given:
        buildFile << "task fail { doFirst { assert false } }"

        when:
        executer.withConsole(consoleType)
        fails('fail')

        then:
        result.output.contains(failureMessage)
    }

    def "Success message is logged with appropriate styling"() {
        given:
        buildFile << "task success"

        when:
        executer.withConsole(consoleType)
        succeeds('success')

        then:
        result.output.contains(successMessage)
    }

    abstract ConsoleOutput getConsoleType()

    abstract String getFailureMessage()

    abstract String getSuccessMessage()
}
