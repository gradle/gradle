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
import spock.lang.Issue

@Requires(value = TestExecutionPreconditions.NotEmbeddedExecutor, reason = "Environment variables are only set for a forked build")
@Issue("https://github.com/gradle/gradle/issues/39562")
class RichConsoleLinuxConsoleFunctionalTest extends AbstractIntegrationSpec {

    private static final String ASCII_BAR = /\[[#.]{15}\] \d+% /

    def setup() {
        executer.withConsole(ConsoleOutput.Rich)
    }

    def "draws the ASCII progress bar on the Linux console when TERM is #term"() {
        given:
        // With a UTF-8 locale, Unicode is supported
        executer.withEnvironmentVars(TERM: term, LANG: "en_US.UTF-8")

        when:
        succeeds('help')

        then:
        def plainOutput = outputWithoutAnsiCodes()
        plainOutput =~ ASCII_BAR
        !plainOutput.contains(ProgressBar.UNICODE_PROGRESS_BAR_PREFIX)

        where:
        term << ["linux", "screen.linux"]
    }

    def "draws the Unicode progress bar on the Linux console with --console-unicode=enable"() {
        given:
        executer.withEnvironmentVars(TERM: "linux", LANG: "en_US.UTF-8")
        executer.withArgument("--console-unicode=enable")

        when:
        succeeds('help')

        then:
        def plainOutput = outputWithoutAnsiCodes()
        plainOutput.contains(ProgressBar.UNICODE_PROGRESS_BAR_PREFIX)
        !(plainOutput =~ ASCII_BAR)
    }

    // Not LogContent.ansiCharsToPlainText(): with the codes applied, the status bar is erased at the end of the build
    private String outputWithoutAnsiCodes() {
        output.replaceAll(/\u001B\[[\d;]*[A-Za-z]/, "")
    }
}
