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

import org.gradle.nativeplatform.fixtures.app.XCTestCaseElement
import org.gradle.nativeplatform.fixtures.app.XCTestSourceElement
import org.gradle.nativeplatform.fixtures.app.XCTestSourceFileElement
import org.gradle.testing.AbstractTestFrameworkIntegrationTest
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition

@Requires([TestPrecondition.SWIFT_SUPPORT, TestPrecondition.MAC_OS_X])
class XCTestTestFrameworkIntegrationTest extends AbstractTestFrameworkIntegrationTest {
    def setup() {
        settingsFile << "rootProject.name = 'app'"
        buildFile << """
            apply plugin: 'xctest'
        """
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
    }

    @Override
    String getTestTaskName() {
        return "xcTest"
    }

    class SwiftXCTestTestFrameworkBundle extends XCTestSourceElement {
        SwiftXCTestTestFrameworkBundle(String projectName) {
            super(projectName)
            withInfoPlist()
        }

        List<XCTestSourceFileElement> testSuites = [
            new XCTestSourceFileElement("SomeTest") {
                List<XCTestCaseElement> testCases = [
                    testCase("testFail", FAILING_TEST, true)
                ]
            }.withImport("Darwin"),

            new XCTestSourceFileElement("SomeOtherTest") {
                List<XCTestCaseElement> testCases = [passingTestCase("testPass")]
            },
        ]

        private static final String FAILING_TEST = """
            fputs("some error output", __stderrp)
            XCTAssert(false, "test failure message")
        """
    }

    @Override
    String getPassingTestCaseName() {
        return "testPass"
    }

    @Override
    String getFailingTestCaseName() {
        return "testFail"
    }
}
