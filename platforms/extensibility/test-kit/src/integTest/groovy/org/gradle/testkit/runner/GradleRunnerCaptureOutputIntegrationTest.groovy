/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.testkit.runner

import org.gradle.integtests.fixtures.executer.OutputScrapingExecutionResult
import org.gradle.testkit.runner.fixtures.InspectsBuildOutput
import org.gradle.util.internal.RedirectStdOutAndErr
import org.junit.Rule

@InspectsBuildOutput
class GradleRunnerCaptureOutputIntegrationTest extends BaseGradleRunnerIntegrationTest {

    static final String OUT = "-- out --"
    static final String ERR = "-- err --"

    @Rule
    RedirectStdOutAndErr stdStreams = new RedirectStdOutAndErr()

    def "can capture stdout and stderr"() {
        given:
        def standardOutput = new StringWriter()
        def standardError = new StringWriter()
        buildFile helloWorldWithStandardOutputAndError()

        when:
        def result = runner('helloWorld', "-d", "-s")
            .forwardStdOutput(standardOutput)
            .forwardStdError(standardError)
            .build()

        then:
        result.output.findAll(OUT).size() == 1
        result.output.findAll(ERR).size() == 1
        standardOutput.toString().findAll(OUT).size() == 1
        standardError.toString().findAll(OUT).size() == 0
        if (isCompatibleVersion('4.7')) {
            // Handling of error log messages changed
            standardOutput.toString().findAll(ERR).size() == 1
            standardError.toString().findAll(ERR).size() == 0
        } else {
            standardError.toString().findAll(ERR).size() == 1
            standardOutput.toString().findAll(ERR).size() == 0
        }

        // isn't empty if version < 2.8 or potentially contains Gradle dist download progress output
        if (isCompatibleVersion('2.8') && !crossVersion) {
            def output = OutputScrapingExecutionResult.from(stdStreams.stdOut, stdStreams.stdErr)
            output.normalizedOutput.empty
            output.error.empty
        }
    }

    def "can forward test execution output to System.out and System.err"() {
        given:
        buildFile << helloWorldWithStandardOutputAndError()

        when:
        def result = runner('helloWorld')
            .forwardOutput()
            .build()

        then:
        noExceptionThrown()
        result.output.findAll(OUT).size() == 1
        result.output.findAll(ERR).size() == 1

        // prints out System.out twice for version < 2.3
        if (isCompatibleVersion('2.3')) {
            assert stdStreams.stdOut.findAll(OUT).size() == 1
            assert stdStreams.stdOut.findAll(ERR).size() == 1
        } else {
            assert stdStreams.stdOut.findAll(OUT).size() == 2
            assert stdStreams.stdOut.findAll(ERR).size() == 2
        }
    }

    def "output is captured if unexpected build exception is thrown"() {
        given:
        Writer standardOutput = new StringWriter()
        Writer standardError = new StringWriter()
        buildFile << helloWorldWithStandardOutputAndError()

        when:
        runner('helloWorld')
            .forwardStdOutput(standardOutput)
            .forwardStdError(standardError)
            .buildAndFail()

        then:
        def t = thrown UnexpectedBuildSuccess
        def result = t.buildResult
        result.output.findAll(OUT).size() == 1
        result.output.findAll(ERR).size() == 1
        standardOutput.toString().findAll(OUT).size() == 1
        standardError.toString().findAll(OUT).size() == 0
        if (isCompatibleVersion('4.7')) {
            // Handling of error log messages changed
            standardOutput.toString().findAll(ERR).size() == 1
            standardError.toString().findAll(ERR).size() == 0
        } else {
            standardError.toString().findAll(ERR).size() == 1
            standardOutput.toString().findAll(ERR).size() == 0
        }
    }

    static String helloWorldWithStandardOutputAndError() {
        """
            task helloWorld {
                doLast {
                    println '$OUT'
                    System.err.println '$ERR'
                }
            }
        """
    }
}
