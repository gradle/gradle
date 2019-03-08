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

import org.fusesource.jansi.Ansi
import org.gradle.api.logging.LogLevel
import org.gradle.integtests.fixtures.console.AbstractConsoleGroupedTaskFunctionalTest
import org.gradle.integtests.fixtures.executer.LogContent
import spock.lang.Unroll

abstract class AbstractConsoleBuildResultFunctionalTest extends AbstractConsoleGroupedTaskFunctionalTest {
    protected final String buildFailed = 'BUILD FAILED'
    protected final String buildSuccess = 'BUILD SUCCESSFUL'
    protected final StyledOutput buildFailedStyled = styled(Ansi.Color.RED, Ansi.Attribute.INTENSITY_BOLD).text(buildFailed).off()
    protected final StyledOutput buildSuccessStyled = styled(Ansi.Color.GREEN, Ansi.Attribute.INTENSITY_BOLD).text(buildSuccess).off()

    abstract String getFailureMessage()

    abstract String getSuccessMessage()

    def "outcome for successful build is logged with appropriate styling"() {
        given:
        buildFile << """
            task noActions
            task notUpToDate {
                doLast { }
            }
            task upToDate {
                outputs.file "file"
                doLast { }
            }
            task all { dependsOn noActions, notUpToDate, upToDate }
        """

        when:
        succeeds('all')

        then:
        LogContent.of(result.output).ansiCharsToColorText().withNormalizedEol().contains(successMessage)
        LogContent.of(result.output).ansiCharsToPlainText().withNormalizedEol().matches """(?s).*
BUILD SUCCESSFUL in \\d+s
2 actionable tasks: 2 executed
.*"""

        when:
        succeeds('all')

        then:
        LogContent.of(result.output).ansiCharsToColorText().withNormalizedEol().contains(successMessage)
        LogContent.of(result.output).ansiCharsToPlainText().withNormalizedEol().matches """(?s).*
BUILD SUCCESSFUL in \\d+s
2 actionable tasks: 1 executed, 1 up-to-date
.*"""
    }

    def "outcome for successful build is not logged with --quiet"() {
        given:
        buildFile << """
            task success
        """

        when:
        executer.withArgument("--quiet")
        succeeds('success')

        then:
        result.assertNotOutput("BUILD SUCCESSFUL")
        result.assertNotOutput("actionable task")
    }

    def "outcome for successful build is logged after user logic has completed"() {
        given:
        buildFile << """
            task success { doLast { } }
            gradle.buildFinished { 
                println "build finished"
            }
        """

        when:
        succeeds('success')

        then:
        LogContent.of(result.output).ansiCharsToPlainText().withNormalizedEol().matches """(?s).*build finished

BUILD SUCCESSFUL in \\d+s
1 actionable task: 1 executed
.*"""
    }

    @Unroll
    def "outcome for failed build is logged with appropriate styling for log level #level"() {
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
        def outputWithFailure = errorsShouldAppearOnStdout() ? failure.output : failure.error
        def outputWithoutFailure = errorsShouldAppearOnStdout() ? failure.error : failure.output
        def outputWithFailureAndNoDebugging = LogContent.of(outputWithFailure).ansiCharsToColorText().removeDebugPrefix().withNormalizedEol()

        outputWithFailureAndNoDebugging.contains("FAILURE: Build failed with an exception.")
        outputWithFailureAndNoDebugging.contains("""
            * What went wrong:
            Execution failed for task ':broken'.
        """.stripIndent().trim())

        !outputWithoutFailure.contains("Build failed with an exception.")
        !outputWithoutFailure.contains("* What went wrong:")

        outputWithFailureAndNoDebugging.contains(failureMessage)

        where:
        level << [LogLevel.DEBUG, LogLevel.INFO, LogLevel.LIFECYCLE, LogLevel.WARN, LogLevel.QUIET]
    }

    @Unroll
    def "reports task execution statistics on build failure with log level #level"() {
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
        LogContent.of(result.output).ansiCharsToPlainText().withNormalizedEol().contains("1 actionable task: 1 executed")

        where:
        level << [LogLevel.DEBUG, LogLevel.INFO, LogLevel.LIFECYCLE]
    }
}
