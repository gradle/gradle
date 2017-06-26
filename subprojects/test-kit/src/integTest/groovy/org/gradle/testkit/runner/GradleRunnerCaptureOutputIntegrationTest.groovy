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
import org.gradle.test.fixtures.server.http.CyclicBarrierHttpServer
import org.gradle.testkit.runner.fixtures.InspectsBuildOutput
import org.gradle.testkit.runner.fixtures.NoDebug
import org.gradle.tooling.GradleConnectionException
import org.gradle.util.GradleVersion
import org.gradle.util.RedirectStdOutAndErr
import org.junit.Rule

@InspectsBuildOutput
class GradleRunnerCaptureOutputIntegrationTest extends BaseGradleRunnerIntegrationTest {

    static final String OUT = "-- out --"
    static final String ERR = "-- err --"

    @Rule
    RedirectStdOutAndErr stdStreams = new RedirectStdOutAndErr()

    @Rule
    CyclicBarrierHttpServer server = new CyclicBarrierHttpServer()

    def "can capture stdout and stderr"() {
        given:
        def standardOutput = new StringWriter()
        def standardError = new StringWriter()
        buildScript helloWorldWithStandardOutputAndError()

        when:
        def result = runner('helloWorld')
            .forwardStdOutput(standardOutput)
            .forwardStdError(standardError)
            .build()

        then:
        result.output.findAll(OUT).size() == 1
        result.output.findAll(ERR).size() == 1
        standardOutput.toString().findAll(OUT).size() == 1
        standardError.toString().findAll(ERR).size() == 1

        // isn't empty if version < 2.8 or potentially contains Gradle dist download progress output
        if (isCompatibleVersion('2.8') && !crossVersion) {
            assert stdStreams.stdOut.empty
            assert stdStreams.stdErr.empty
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
        standardError.toString().findAll(ERR).size() == 1
    }

    @NoDebug
    def "output is captured if mechanical failure occurs"() {
        // todo: should we keep this test altogether? It was supposed to test that if a mechanical
        // failure occurs, we're receiving all the messages that were sent before the failure, but
        // in this case, we're now sending events asynchronously, so there's a chance that those
        // messages are not received. The test works around by waiting for the client to get the
        // messages before killing the daemon, but it defeats the concept of this test...
        given:
        boolean foundOut
        boolean foundErr
        def release = { char[] cbuf, int off, int len ->
            def str = new String(cbuf, off, len)
            if (str.contains(OUT)) {
                foundOut = true
            } else if (str.contains(ERR)) {
                foundErr = true
            }
            if (foundOut && foundErr) {
                server.release()
            }
        }
        Writer standardOutput = new StringWriter() {
            @Override
            void write(char[] cbuf, int off, int len) {
                super.write(cbuf, off, len)
                release(cbuf, off, len)
            }
        }
        Writer standardError = new StringWriter() {
            @Override
            void write(char[] cbuf, int off, int len) {
                super.write(cbuf, off, len)
                release(cbuf, off, len)
            }
        }


        buildFile << helloWorldWithStandardOutputAndError() << """
            helloWorld.doLast {
                new URL("${server.uri}").text
                Runtime.runtime.halt(0)
            }
        """

        when:
        Thread.start { server.waitFor() }
        runner('helloWorld')
            .forwardStdOutput(standardOutput)
            .forwardStdError(standardError)
            .build()

        then:
        def t = thrown IllegalStateException
        t.cause instanceof GradleConnectionException
        t.cause.cause.class.name == DaemonDisappearedException.name // not the same class because it's coming from the tooling client
        standardOutput.toString().findAll(OUT).size() == 1
        standardError.toString().findAll(ERR).size() == 1
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

    static boolean isCompatibleVersion(String minCompatibleVersion) {
        gradleVersion.compareTo(GradleVersion.version(minCompatibleVersion)) >= 0
    }
}
