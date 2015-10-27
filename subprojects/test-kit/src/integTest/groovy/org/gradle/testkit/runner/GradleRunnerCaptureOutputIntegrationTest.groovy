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

import org.gradle.launcher.daemon.client.DaemonDisappearedException
import org.gradle.testkit.runner.fixtures.NoDebug
import org.gradle.tooling.GradleConnectionException
import org.gradle.util.RedirectStdOutAndErr
import org.junit.Rule

class GradleRunnerCaptureOutputIntegrationTest extends AbstractGradleRunnerIntegrationTest {

    static final String OUT = "out"
    static final String ERR = "err"

    @Rule
    RedirectStdOutAndErr stdStreams = new RedirectStdOutAndErr()

    def "can capture stdout and stderr"() {
        given:
        def standardOutput = new StringWriter()
        def standardError = new StringWriter()
        buildFile << helloWorldWithStandardOutputAndError()

        when:
        def result = runner('helloWorld')
            .forwardStdOutput(standardOutput)
            .forwardStdError(standardError)
            .build()

        then:
        noExceptionThrown()
        result.output.contains(OUT)
        result.output.contains(ERR)
        standardOutput.toString().contains(OUT)
        standardError.toString().contains(ERR)
        stdStreams.stdErr.empty
        stdStreams.stdOut.empty
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
        result.output.contains(OUT)
        result.output.contains(ERR)
        stdStreams.stdOut.contains(OUT)
        stdStreams.stdOut.contains(ERR)
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
        result.output.contains(OUT)
        result.output.contains(ERR)
        standardOutput.toString().contains(OUT)
        standardError.toString().contains(ERR)
    }

    @NoDebug
    def "output is captured if mechanical failure occurs"() {
        given:
        Writer standardOutput = new StringWriter()
        Writer standardError = new StringWriter()

        buildFile << helloWorldWithStandardOutputAndError() << """
            helloWorld.doLast { Runtime.runtime.halt(0) }
        """

        when:
        runner('helloWorld')
            .forwardStdOutput(standardOutput)
            .forwardStdError(standardError)
            .build()

        then:
        def t = thrown IllegalStateException
        t.cause instanceof GradleConnectionException
        t.cause.cause.class.name == DaemonDisappearedException.name // not the same class because it's coming from the tooling client
        standardOutput.toString().contains(OUT)
        standardError.toString().contains(ERR)
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
