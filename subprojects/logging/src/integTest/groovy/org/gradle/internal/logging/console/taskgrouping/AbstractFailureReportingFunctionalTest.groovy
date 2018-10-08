/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.internal.logging.console.taskgrouping

import org.gradle.api.logging.LogLevel
import org.gradle.integtests.fixtures.console.AbstractConsoleGroupedTaskFunctionalTest
import org.gradle.integtests.fixtures.executer.LogContent
import spock.lang.Unroll


abstract class AbstractFailureReportingFunctionalTest extends AbstractConsoleGroupedTaskFunctionalTest {
    @Unroll
    def "reports build failure at the end of the build with log level #level"() {
        buildFile << """
            task broken {
                doLast {
                    throw new RuntimeException("broken")
                }
            }
        """

        expect:
        executer.withArguments("-Dorg.gradle.logging.level=${level}")
        fails("broken")

        and:
        // Ensure the failure is a location that the fixtures can see
        failure.assertHasDescription("Execution failed for task ':broken'")
        failure.assertHasCause("broken")

        // Check that the failure text appears either stdout or stderr
        def outputWithFailure = consoleAttachment.isStderrAttached() ? failure.output : failure.error
        def outputWithoutFailure = consoleAttachment.isStderrAttached() ? failure.error : failure.output
        def outputWithFailureAndNoDebugging = LogContent.of(outputWithFailure).removeEmptyLines().removeAnsiChars().removeDebugPrefix().withNormalizedEol()

        outputWithFailure.contains("Build failed with an exception.")
        outputWithFailureAndNoDebugging.contains("""
            * What went wrong:
            Execution failed for task ':broken'.
        """.stripIndent().trim())

        !outputWithoutFailure.contains("Build failed with an exception.")
        !outputWithoutFailure.contains("* What went wrong:")

        outputWithFailure.contains("BUILD FAILED")

        where:
        level << [LogLevel.DEBUG, LogLevel.INFO, LogLevel.LIFECYCLE, LogLevel.WARN, LogLevel.QUIET]
    }
}
