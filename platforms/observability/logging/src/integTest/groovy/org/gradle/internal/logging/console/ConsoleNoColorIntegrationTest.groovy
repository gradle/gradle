/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.internal.logging.console

import org.gradle.api.logging.configuration.ConsoleOutput
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.TestExecutionPreconditions

@Requires(TestExecutionPreconditions.NotEmbeddedExecutor.class)
class ConsoleNoColorIntegrationTest extends AbstractIntegrationSpec {

    private static final ANSI_BOLD = '\u001B[1m'
    private static final ANSI_FOREGROUND_COLOR = /\u001B\[[\d;]*3[0-7][;m]/
    private static final ANSI_BRIGHT_FOREGROUND_COLOR = /\u001B\[[\d;]*9[0-7][;m]/

    def "empty NO_COLOR does not suppress styling"() {
        given:
        executer.withEnvironmentVars(NO_COLOR: "")
        executer.withConsole(ConsoleOutput.Rich)

        when:
        succeeds('help')

        then:
        output.contains(ANSI_BOLD)
        output =~ ANSI_FOREGROUND_COLOR
    }

    def "NO_COLOR strips foreground colors with --console=#consoleOutput"() {
        given:
        executer.withEnvironmentVars(NO_COLOR: "1")
        executer.withConsole(consoleOutput)

        when:
        succeeds('help')

        then:
        !(output =~ ANSI_FOREGROUND_COLOR)
        !(output =~ ANSI_BRIGHT_FOREGROUND_COLOR)

        where:
        consoleOutput << ConsoleOutput.values()
    }

    def "NO_COLOR preserves emphasis styling with --console=#consoleOutput"() {
        given:
        executer.withEnvironmentVars(NO_COLOR: "1")
        executer.withConsole(consoleOutput)

        when:
        succeeds('help')

        then:
        output.contains(ANSI_BOLD)

        where:
        consoleOutput << [ConsoleOutput.Rich, ConsoleOutput.Verbose, ConsoleOutput.Colored]
    }
}
