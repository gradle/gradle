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
import spock.lang.Timeout

import static org.gradle.integtests.fixtures.BuildScanUserInputFixture.DUMMY_TASK_NAME
import static org.gradle.integtests.fixtures.BuildScanUserInputFixture.PROMPT
import static org.gradle.integtests.fixtures.BuildScanUserInputFixture.YES
import static org.gradle.integtests.fixtures.BuildScanUserInputFixture.answerOutput
import static org.gradle.integtests.fixtures.BuildScanUserInputFixture.buildScanPlugin
import static org.gradle.integtests.fixtures.BuildScanUserInputFixture.buildScanPluginApplication

// 8.8 is excluded: it shipped the new client-side prompt protocol (PromptOutputEvent/UserResponse) but
// still had the daemon greedily consume the client's stdin, so the forwarded answer is swallowed as raw
// stdin before the prompt response is read and askYesNoQuestion returns null. The greedy consumption was
// removed in 8.9 (commit 66832d04eff), and 8.7 and earlier use the older protocol that is unaffected.
@TargetGradleVersion(">=4.3 !8.8")
// The first compile against the Gradle API in a fresh user home is slow on EBS-backed CI agents
@Timeout(300)
class CapturingUserInputCrossVersionSpec extends ToolingApiSpecification {
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

    def "cannot capture user input if standard input was not provided"() {
        when:
        withConnection { connection ->
            basicBuildConfiguration(connection).run()
        }

        then:
        !output.contains(PROMPT)
        output.contains(answerOutput(null))
    }

    private void runBuildWithStandardInput(ProjectConnection connection) {
        def stdin = new ByteArrayInputStream((YES + System.getProperty('line.separator')).bytes)

        basicBuildConfiguration(connection)
            .setStandardInput(stdin)
            .run()
    }

    private static BuildLauncher basicBuildConfiguration(ProjectConnection connection) {
        connection.newBuild().forTasks(DUMMY_TASK_NAME)
    }

    private String getOutput() {
        stdout.toString()
    }
}
