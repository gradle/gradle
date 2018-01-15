/*
 * Copyright 2018 the original author or authors.
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

import org.gradle.language.AbstractNativeLanguageComponentIntegrationTest
import org.gradle.nativeplatform.fixtures.app.Swift3XCTest
import org.hamcrest.Matchers
import org.junit.Assume

abstract class AbstractSwiftXCTestComponentWithTestedComponentIntegrationTest extends AbstractSwiftXCTestComponentIntegrationTest implements XCTestExecutionResult {
    def "take swift source compatibility from tested component"() {
        Assume.assumeThat(AbstractNativeLanguageComponentIntegrationTest.toolChain.version.major, Matchers.equalTo(4))

        given:
        makeSingleProject()
        swift3Component.writeToProject(testDirectory)
        buildFile << """
            ${testedComponentDsl} {
                sourceCompatibility = SwiftVersion.SWIFT3
            }

            task verifyBinariesSwiftVersion {
                doLast {
                    ${componentUnderTestDsl}.binaries.get().each {
                        assert it.sourceCompatibility.get() == SwiftVersion.SWIFT3
                    }
                }
            }
        """
        settingsFile << "rootProject.name = '$swift3Component.projectName'"

        when:
        succeeds "verifyBinariesSwiftVersion"
        succeeds taskNameToAssembleDevelopmentBinary

        then:
        result.assertTasksExecuted(tasksToAssembleDevelopmentBinaryOfComponentUnderTest, ":$taskNameToAssembleDevelopmentBinary")
        swift3Component.assertTestCasesRan(testExecutionResult)
    }

    def "ignores swift source compatibility from tested component when set on test component"() {
        Assume.assumeThat(AbstractNativeLanguageComponentIntegrationTest.toolChain.version.major, Matchers.equalTo(4))

        given:
        makeSingleProject()
        def fixture = new Swift3XCTest('project')
        fixture.writeToProject(testDirectory)
        buildFile << """
            ${testedComponentDsl} {
                sourceCompatibility = SwiftVersion.SWIFT4
            }

            ${componentUnderTestDsl} {
                sourceCompatibility = SwiftVersion.SWIFT3
            }

            task verifyBinariesSwiftVersion {
                doLast {
                    ${testedComponentDsl}.binaries.get().each {
                        assert it.sourceCompatibility.get() == SwiftVersion.SWIFT4
                    }
                    ${componentUnderTestDsl}.binaries.get().each {
                        assert it.sourceCompatibility.get() == SwiftVersion.SWIFT3
                    }
                }
            }
        """
        settingsFile << "rootProject.name = '$fixture.projectName'"

        when:
        succeeds "verifyBinariesSwiftVersion"
        succeeds taskNameToAssembleDevelopmentBinary

        then:
        result.assertTasksExecuted(tasksToAssembleDevelopmentBinaryOfComponentUnderTest, ":$taskNameToAssembleDevelopmentBinary")
        fixture.assertTestCasesRan(testExecutionResult)
    }

    abstract String getTestedComponentDsl()

    @Override
    protected String getComponentUnderTestDsl() {
        return "xctest"
    }

    @Override
    String getTaskNameToAssembleDevelopmentBinary() {
        return "test"
    }

    @Override
    List<String> getTasksToAssembleDevelopmentBinaryOfComponentUnderTest() {
        return [":compileTestSwift", ":linkTest", ":installTest", ":xcTest"]
    }
}
