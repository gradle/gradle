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

import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule

import static org.gradle.testkit.runner.TaskOutcome.*

class GradleRunnerCustomGradleUserHomeIntegrationTest extends AbstractGradleRunnerIntegrationTest {

    @Rule
    TestNameTestDirectoryProvider gradleUserHomeDir = new TestNameTestDirectoryProvider()

    def "can execute build with provided custom Gradle user home directory multiple times"() {
        given:
        buildFile << helloWorldTask()

        when:
        GradleRunner gradleRunner = runner('helloWorld')
        gradleRunner.withGradleUserHomeDir(gradleUserHomeDir.testDirectory)
        BuildResult result = gradleRunner.build()

        then:
        noExceptionThrown()
        result.standardOutput.contains(':helloWorld')
        !result.standardError
        result.tasks.collect { it.path } == [':helloWorld']
        result.taskPaths(SUCCESS) == [':helloWorld']
        result.taskPaths(SKIPPED).empty
        result.taskPaths(UP_TO_DATE).empty
        result.taskPaths(FAILED).empty
        gradleRunner.gradleUserHomeDir == gradleUserHomeDir.testDirectory

        when:
        result = gradleRunner.build()

        then:
        result.standardOutput.contains(':helloWorld')
        !result.standardError
        result.tasks.collect { it.path } == [':helloWorld']
        result.taskPaths(SUCCESS) == [':helloWorld']
        result.taskPaths(SKIPPED).empty
        result.taskPaths(UP_TO_DATE).empty
        result.taskPaths(FAILED).empty
        gradleRunner.gradleUserHomeDir == gradleUserHomeDir.testDirectory
    }
}
