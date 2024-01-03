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

import org.gradle.internal.os.OperatingSystem
import org.gradle.language.swift.SwiftTaskNames
import org.gradle.nativeplatform.fixtures.RequiresInstalledToolChain
import org.gradle.nativeplatform.fixtures.ToolChainRequirement
import org.gradle.nativeplatform.fixtures.app.XCTestSourceElement
import org.gradle.nativeplatform.test.AbstractNativeUnitTestIntegrationTest
import org.junit.Assume

@RequiresInstalledToolChain(ToolChainRequirement.SWIFTC)
abstract class AbstractSwiftXCTestIntegrationTest extends AbstractNativeUnitTestIntegrationTest implements XCTestExecutionResult, SwiftTaskNames {
    def setup() {
        // TODO: Temporarily disable XCTests with Swift3 on macOS
        Assume.assumeFalse(OperatingSystem.current().isMacOsX() && toolChain.version.major == 3)
    }

    @Override
    protected void writeTests() {
        settingsFile << "rootProject.name = '${passingTestFixture.projectName}'"
        passingTestFixture.writeToProject(testDirectory)
    }

    @Override
    protected void changeTestImplementation() {
        file(passingTestFixture.testSuites.first().sourceFile.withPath("src/test")) << """
            func test() -> Int32 { return 1; }
        """
    }

    @Override
    protected void assertTestCasesRan() {
        passingTestFixture.assertTestCasesRan(testExecutionResult)
    }

    @Override
    String[] getTasksToBuildAndRunUnitTest() {
        return tasks.test.allToInstall + [":xcTest"]
    }

    @Override
    protected String getTestComponentDsl() {
        return "xctest"
    }

    @Override
    protected String getComponentUnderTestDsl() {
        return null
    }

    @Override
    protected String[] getTasksToBuildAndRunUnitTest(String architecture) {
        return tasksToBuildAndRunUnitTest
    }

    @Override
    protected String[] getTasksToCompileComponentUnderTest(String architecture) {
        return tasksToCompileComponentUnderTest
    }

    @Override
    protected String[] getTasksToRelocate() {
        return getTasksToRelocate(null) + renameLinuxMainTasks()
    }

    protected abstract XCTestSourceElement getPassingTestFixture()

    @Override
    String getLanguageTaskSuffix() {
        return "Swift"
    }
}
