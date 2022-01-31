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

import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.internal.os.OperatingSystem
import org.gradle.language.swift.SwiftVersion
import org.gradle.nativeplatform.fixtures.RequiresInstalledToolChain
import org.gradle.nativeplatform.fixtures.ToolChainRequirement
import org.gradle.nativeplatform.fixtures.app.MainWithXCTestSourceElement
import org.gradle.nativeplatform.fixtures.app.Swift3WithSwift4XCTest
import org.gradle.nativeplatform.fixtures.app.Swift4WithSwift3XCTest
import org.gradle.nativeplatform.fixtures.app.Swift5WithSwift4XCTest

import static org.junit.Assume.assumeTrue

abstract class AbstractSwiftXCTestComponentWithTestedComponentIntegrationTest extends AbstractSwiftXCTestComponentIntegrationTest implements XCTestExecutionResult {
    // TODO: This test can be generalized so it's not opinionated on Swift 4.x but could also work on Swift 5.x
    @RequiresInstalledToolChain(ToolChainRequirement.SWIFTC_4)
    @ToBeFixedForConfigurationCache
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
                        assert it.targetPlatform.sourceCompatibility == SwiftVersion.SWIFT3
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

    @ToBeFixedForConfigurationCache
    def "honors Swift source compatibility difference on both tested component (#componentSourceCompatibility) and XCTest component (#xctestSourceCompatibility)"() {
        given:
        assumeSwiftCompilerSupportsLanguageVersion(componentSourceCompatibility)
        assumeSwiftCompilerSupportsLanguageVersion(xctestSourceCompatibility)
        makeSingleProject()
        fixture.writeToProject(testDirectory)
        buildFile << """
            ${testedComponentDsl}.sourceCompatibility = SwiftVersion.${componentSourceCompatibility.name()}
            ${componentUnderTestDsl}.sourceCompatibility = SwiftVersion.${xctestSourceCompatibility.name()}

            task verifyBinariesSwiftVersion {
                doLast {
                    ${testedComponentDsl}.binaries.get().each {
                        assert it.targetPlatform.sourceCompatibility == SwiftVersion.${componentSourceCompatibility.name()}
                    }
                    ${componentUnderTestDsl}.binaries.get().each {
                        assert it.targetPlatform.sourceCompatibility == SwiftVersion.${xctestSourceCompatibility.name()}
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
        fixture                               | componentSourceCompatibility | xctestSourceCompatibility
        new Swift3WithSwift4XCTest('project') | SwiftVersion.SWIFT3          | SwiftVersion.SWIFT4
        new Swift4WithSwift3XCTest('project') | SwiftVersion.SWIFT4          | SwiftVersion.SWIFT3
        new Swift5WithSwift4XCTest('project') | SwiftVersion.SWIFT5          | SwiftVersion.SWIFT4
    }

    void assumeSwiftCompilerSupportsLanguageVersion(SwiftVersion swiftVersion) {
        assert toolChain != null, "You need to specify Swift tool chain requirement with 'requireSwiftToolChain()'"
        assumeTrue((toolChain.version.major == 5 && swiftVersion.version in [5, 4]) || (toolChain.version.major == 4 && swiftVersion.version in [4, 3]) || (toolChain.version.major == 3 && swiftVersion.version == 3))
    }

    abstract String getTestedComponentDsl()

    @Override
    protected String getComponentUnderTestDsl() {
        return "xctest"
    }

    @Override
    protected configureTargetMachines(String... targetMachines) {
        return """
            ${testedComponentDsl} {
                targetMachines = [${targetMachines.join(",")}]
            }
        """ + super.configureTargetMachines(targetMachines)
    }

    @Override
    protected abstract MainWithXCTestSourceElement getComponentUnderTest()

    @Override
    void assertComponentUnderTestWasBuilt() {
        if (OperatingSystem.current().linux) {
            executable("build/exe/test/${componentUnderTest.test.moduleName}").assertExists()
            installation("build/install/test").assertInstalled()
        } else {
            machOBundle("build/exe/test/${componentUnderTest.test.moduleName}").assertExists()
            file("build/install/test/${componentUnderTest.test.moduleName}").assertIsFile()
            file("build/install/test/${componentUnderTest.test.moduleName}.xctest").assertIsDir()
        }
    }
}
