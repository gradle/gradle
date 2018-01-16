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

package org.gradle.nativeplatform.test.xctest

import org.gradle.nativeplatform.fixtures.RequiresInstalledToolChain
import org.gradle.nativeplatform.fixtures.ToolChainRequirement
import org.gradle.nativeplatform.fixtures.app.XCTestSourceElement
import org.gradle.nativeplatform.test.AbstractNativeUnitTestIntegrationTest
import spock.lang.Unroll

@RequiresInstalledToolChain(ToolChainRequirement.SWIFT)
abstract class AbstractSwiftXCTestIntegrationTest extends AbstractNativeUnitTestIntegrationTest implements XCTestExecutionResult {
    @Unroll
    def "runs tests when #task lifecycle task executes"() {
        given:
        def fixture = getPassingTestFixture()
        makeSingleProject()
        settingsFile << "rootProject.name = '${fixture.projectName}'"
        fixture.writeToProject(testDirectory)

        when:
        succeeds(task)

        then:
        result.assertTasksExecuted(tasksToBuildAndRunUnitTest, expectedLifecycleTasks)
        fixture.assertTestCasesRan(testExecutionResult)

        where:
        task    | expectedLifecycleTasks
        "test"  | [":test"]
        "check" | [":test", ":check"]
        "build" | [":test", ":check", ":build", taskToAssembleComponentUnderTest, ":assemble"]
    }

    def "skips test tasks as up-to-date when nothing changes between invocation"() {
        given:
        def fixture = getPassingTestFixture()
        makeSingleProject()
        settingsFile << "rootProject.name = '${fixture.projectName}'"
        fixture.writeToProject(testDirectory)

        succeeds("test")

        when:
        succeeds("test")

        then:
        result.assertTasksExecuted(tasksToBuildAndRunUnitTest, ":test")
        result.assertTasksSkipped(tasksToBuildAndRunUnitTest, ":test")
    }

    @Override
    String[] getTasksToBuildAndRunUnitTest() {
        return tasksToCompileComponentUnderTest + [":compileTestSwift", ":linkTest", ":installTest", ":xcTest"]
    }

    protected abstract String[] getTaskToAssembleComponentUnderTest()

    protected abstract String[] getTasksToCompileComponentUnderTest()

    protected abstract XCTestSourceElement getPassingTestFixture()
}
