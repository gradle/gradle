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

package org.gradle.integtests.tooling.r43

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.TestResultHandler
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.test.fixtures.Flaky
import org.gradle.tooling.BuildLauncher
import org.gradle.tooling.ProjectConnection

import static org.gradle.integtests.fixtures.BuildScanUserInputFixture.DUMMY_TASK_NAME
import static org.gradle.integtests.fixtures.BuildScanUserInputFixture.PROMPT
import static org.gradle.integtests.fixtures.BuildScanUserInputFixture.YES
import static org.gradle.integtests.fixtures.BuildScanUserInputFixture.answerOutput
import static org.gradle.integtests.fixtures.BuildScanUserInputFixture.buildScanPlugin
import static org.gradle.integtests.fixtures.BuildScanUserInputFixture.buildScanPluginApplication
import static org.gradle.test.fixtures.ConcurrentTestUtil.poll

@TargetGradleVersion(">=4.3")
class CapturingUserInputCrossVersionSpec extends ToolingApiSpecification {
    def setup() {
        if (!dist.toolingApiStdinInEmbeddedModeSupported) {
            // Did not work in embedded mode in older versions
            toolingApi.requireDaemons()
        }

        file('buildSrc/src/main/java/BuildScanPlugin.java') << buildScanPlugin()
        file('build.gradle') << buildScanPluginApplication()
    }

    @Flaky(because = "https://github.com/gradle/gradle-private/issues/4145")
    def "can capture user input if standard input was provided"() {
        when:
        withConnection { ProjectConnection connection ->
            runBuildWithStandardInput(connection)
        }

        then:
        output.contains(PROMPT)
        output.contains(answerOutput(true))
    }

    def "cannot capture user input if standard input was not provided"() {
        when:
        withConnection { ProjectConnection connection ->
            runBuildWithoutStandardInput(connection)
        }

        then:
        !output.contains(PROMPT)
        output.contains(answerOutput(null))
    }

    private void runBuildWithStandardInput(ProjectConnection connection) {
        def build = basicBuildConfiguration(connection)

        def stdin = new PipedInputStream()
        def stdinWriter = new PipedOutputStream(stdin)

        build.standardInput = stdin

        def resultHandler = new TestResultHandler()
        build.run(resultHandler)

        poll(60) {
            assert getOutput().contains(PROMPT)
        }

        stdinWriter.write((YES + System.getProperty('line.separator')).bytes)
        stdinWriter.close()

        resultHandler.finished()
        resultHandler.assertNoFailure()
    }

    private void runBuildWithoutStandardInput(ProjectConnection connection) {
        def build = basicBuildConfiguration(connection)
        build.run()
    }

    private BuildLauncher basicBuildConfiguration(ProjectConnection connection) {
        def build = connection.newBuild()
        collectOutputs(build)
        build.forTasks(DUMMY_TASK_NAME)
        build
    }

    private String getOutput() {
        stdout.toString()
    }
}
