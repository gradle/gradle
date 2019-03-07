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
    protected final StyledOutput buildFailedStyled = styled(buildFailed, Ansi.Color.RED, Ansi.Attribute.INTENSITY_BOLD)
    protected final StyledOutput buildSuccessStyled = styled(buildSuccess, Ansi.Color.GREEN, Ansi.Attribute.INTENSITY_BOLD)

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
        result.assertRawOutputContains(successMessage)
        LogContent.of(result.output).removeAnsiChars().withNormalizedEol().matches """(?s).*
BUILD SUCCESSFUL in \\d+s\\n*
2 actionable tasks: 2 executed
.*"""

        when:
        succeeds('all')

        then:
        result.assertRawOutputContains(successMessage)
        LogContent.of(result.output).removeAnsiChars().withNormalizedEol().matches """(?s).*
BUILD SUCCESSFUL in \\d+s\\n*
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
        LogContent.of(result.output).removeAnsiChars().withNormalizedEol().matches """(?s).*build finished\\n*
BUILD SUCCESSFUL in \\d+s\\n*
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
        def outputWithFailureAndNoDebugging = LogContent.of(outputWithFailure).removeAnsiChars().removeDebugPrefix().removeBlankLines().withNormalizedEol()

        outputWithFailure.contains("Build failed with an exception.")
        outputWithFailureAndNoDebugging.contains("""
            * What went wrong:
            Execution failed for task ':broken'.
        """.stripIndent().trim())

        !outputWithoutFailure.contains("Build failed with an exception.")
        !outputWithoutFailure.contains("* What went wrong:")

        outputWithFailure.contains("BUILD FAILED")
        failure.assertHasRawErrorOutput(failureMessage)

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
        result.assertRawOutputContains("1 actionable task: 1 executed")

        where:
        level << [LogLevel.DEBUG, LogLevel.INFO, LogLevel.LIFECYCLE]
    }
}
