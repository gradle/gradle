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
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.tooling.BuildLauncher
import org.gradle.tooling.ProjectConnection

import static org.gradle.integtests.fixtures.BuildScanUserInputFixture.*

@TargetGradleVersion(">=4.3")
class CapturingUserInputCrossVersionSpec extends ToolingApiSpecification {

    def outputStream = new ByteArrayOutputStream()

    def setup() {
        if (!dist.toolingApiStdinInEmbeddedModeSupported) {
            // Did not work in embedded mode in older versions
            toolingApi.requireDaemons()
        }

        file('buildSrc/src/main/java/BuildScanPlugin.java') << buildScanPlugin()
        file('build.gradle') << buildScanPluginApplication()
    }

    def "can capture user input if standard input was provided"() {
        when:
        withConnection { ProjectConnection connection ->
            runBuildWithStandardInput(connection)
        }

        then:
        output.contains(PROMPT)
        output.contains(answerOutput(true))
    }

    def "cannot capture user input if standard in was not provided"() {
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
        build.standardInput = new ByteArrayInputStream((YES + System.getProperty('line.separator')).bytes)
        runBuild(build)
    }

    private void runBuildWithoutStandardInput(ProjectConnection connection) {
        runBuild(basicBuildConfiguration(connection))
    }

    private BuildLauncher basicBuildConfiguration(ProjectConnection connection) {
        def build = connection.newBuild()
        build.standardOutput = outputStream
        build.forTasks(DUMMY_TASK_NAME)
        build
    }

    private void runBuild(BuildLauncher build) {
        build.run()
    }

    private String getOutput() {
        outputStream.toString()
    }
}
