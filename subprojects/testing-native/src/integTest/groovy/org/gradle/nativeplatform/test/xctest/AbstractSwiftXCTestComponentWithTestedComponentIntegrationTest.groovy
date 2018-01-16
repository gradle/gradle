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

import org.gradle.language.swift.SwiftVersion
import org.gradle.nativeplatform.fixtures.RequiresInstalledToolChain
import org.gradle.nativeplatform.fixtures.ToolChainRequirement
import org.gradle.nativeplatform.fixtures.app.Swift3WithSwift4XCTest
import org.gradle.nativeplatform.fixtures.app.Swift4WithSwift3XCTest
import spock.lang.Unroll

abstract class AbstractSwiftXCTestComponentWithTestedComponentIntegrationTest extends AbstractSwiftXCTestComponentIntegrationTest implements XCTestExecutionResult {
    @RequiresInstalledToolChain(ToolChainRequirement.SWIFT4)
    def "take swift source compatibility from tested component"() {
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

    @RequiresInstalledToolChain(ToolChainRequirement.SWIFT4)
    @Unroll
    def "honors Swift source compatibility difference on both tested component (#componentSourceCompatibility) and XCTest component (#xctestSourceCompatibility)"() {
        given:
        makeSingleProject()
        fixture.writeToProject(testDirectory)
        buildFile << """
            ${testedComponentDsl}.sourceCompatibility = SwiftVersion.${componentSourceCompatibility.name()}
            ${componentUnderTestDsl}.sourceCompatibility = SwiftVersion.${xctestSourceCompatibility.name()}

            task verifyBinariesSwiftVersion {
                doLast {
                    ${testedComponentDsl}.binaries.get().each {
                        assert it.sourceCompatibility.get() == SwiftVersion.${componentSourceCompatibility.name()}
                    }
                    ${componentUnderTestDsl}.binaries.get().each {
                        assert it.sourceCompatibility.get() == SwiftVersion.${xctestSourceCompatibility.name()}
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

        where:
        fixture                                           | componentSourceCompatibility | xctestSourceCompatibility
        new Swift3WithSwift4XCTest('project') | SwiftVersion.SWIFT3          | SwiftVersion.SWIFT4
        new Swift4WithSwift3XCTest('project') | SwiftVersion.SWIFT4          | SwiftVersion.SWIFT3
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
