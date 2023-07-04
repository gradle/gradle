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
import org.gradle.nativeplatform.fixtures.AvailableToolChains
import org.gradle.nativeplatform.fixtures.ToolChainRequirement
import org.gradle.nativeplatform.fixtures.app.XCTestCaseElement
import org.gradle.nativeplatform.fixtures.app.XCTestSourceElement
import org.gradle.nativeplatform.fixtures.app.XCTestSourceFileElement
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions
import org.gradle.testing.AbstractTestFrameworkIntegrationTest

import static org.junit.Assume.assumeTrue

@Requires(UnitTestPreconditions.NotMacOsM1)
class XCTestTestFrameworkIntegrationTest extends AbstractTestFrameworkIntegrationTest {
    def setup() {
        def toolChain = AvailableToolChains.getToolChain(ToolChainRequirement.SWIFTC)
        assumeTrue(toolChain != null)

        File initScript = file("init.gradle") << """
allprojects { p ->
    apply plugin: ${toolChain.pluginClass}

    model {
          toolChains {
            ${toolChain.buildScriptConfig}
          }
    }
}
"""
        executer.beforeExecute({
            usingInitScript(initScript)
        })

        settingsFile << "rootProject.name = 'app'"
        buildFile << """
            apply plugin: 'xctest'
        """
    }

    @Override
    protected boolean capturesTestOutput() {
        // The driver application for XCTest behave very differently on macOS and Linux.
        // It was hard to get the desired outcome on macOS and impossible for Linux. It
        // all seems to be related to https://bugs.swift.org/browse/SR-1127. On Linux,
        // we just can't assert that test output is captured correctly. Until we roll out
        // our own driver app, test output capture only works on macOS.
        return OperatingSystem.current().macOsX
    }

    @Override
    void createPassingFailingTest() {
        def testBundle = new SwiftXCTestTestFrameworkBundle('app')
        testBundle.writeToProject(testDirectory)
    }

    @Override
    void createEmptyProject() {
        file("src/test/swift/NoTests.swift") << """
            func someSwiftCode() {
            }
        """
    }

    @Override
    void renameTests() {
        def newTest = file("src/test/swift/NewTest.swift")
        file("src/test/swift/SomeOtherTest.swift").renameTo(newTest)
        newTest.text = newTest.text.replaceAll("SomeOtherTest", "NewTest")
        def linuxMain = file("src/test/swift/LinuxMain.swift")
        linuxMain.text = linuxMain.text.replaceAll("SomeOtherTest", "NewTest")
    }

    @Override
    String getTestTaskName() {
        return "xcTest"
    }

    class SwiftXCTestTestFrameworkBundle extends XCTestSourceElement {
        SwiftXCTestTestFrameworkBundle(String projectName) {
            super(projectName)
        }

        List<XCTestSourceFileElement> testSuites = [
            new XCTestSourceFileElement("SomeTest") {
                List<XCTestCaseElement> testCases = [
                    testCase(failingTestCaseName, FAILING_TEST, true),
                    passingTestCase(passingTestCaseName)
                ]
            }.withImport(libcModuleName),

            new XCTestSourceFileElement("SomeOtherTest") {
                List<XCTestCaseElement> testCases = [
                    passingTestCase(passingTestCaseName)
                ]
            },
        ]

        private static final String FAILING_TEST = """
            fputs("some error output\\n", stderr)
            XCTAssert(false, "test failure message")
        """

        private static String getLibcModuleName() {
            if (OperatingSystem.current().macOsX) {
                return "Darwin"
            }
            return "Glibc"
        }
    }

    @Override
    String getPassingTestCaseName() {
        return "testPass"
    }

    @Override
    String getFailingTestCaseName() {
        return "testFail"
    }

    @Override
    String testSuite(String testSuite) {
        return "AppTest.$testSuite"
    }
}
