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

package org.gradle.language.swift

import org.gradle.language.AbstractNativeLanguageComponentIntegrationTest
import org.gradle.nativeplatform.fixtures.RequiresInstalledToolChain
import org.gradle.nativeplatform.fixtures.ToolChainRequirement
import org.gradle.nativeplatform.fixtures.app.SourceElement

@RequiresInstalledToolChain(ToolChainRequirement.SWIFTC)
abstract class AbstractSwiftComponentIntegrationTest extends AbstractNativeLanguageComponentIntegrationTest {
    def "binaries have the right Swift version"() {
        given:
        makeSingleProject()
        def expectedVersion = AbstractNativeLanguageComponentIntegrationTest.toolChain.version.major
        buildFile << """
            task verifyBinariesSwiftVersion {
                doLast {
                    ${componentUnderTestDsl}.binaries.get().each {
                        assert it.sourceCompatibility.get().version == ${expectedVersion}
                    }
                }
            }
        """

        expect:
        succeeds "verifyBinariesSwiftVersion"
    }

    @RequiresInstalledToolChain(ToolChainRequirement.SWIFTC_4)
    def "throws exception when modifying Swift component source compatibility after the binaries are known"() {
        given:
        makeSingleProject()
        buildFile << """
            ${componentUnderTestDsl} {
                sourceCompatibility = SwiftVersion.SWIFT3
            }

            ${componentUnderTestDsl}.binaries.whenElementKnown {
                ${componentUnderTestDsl}.sourceCompatibility = SwiftVersion.SWIFT4
            }

            task verifyBinariesSwiftVersion {}
        """
        settingsFile << "rootProject.name = 'swift-project'"

        expect:
        fails "verifyBinariesSwiftVersion"
        failure.assertHasDescription("A problem occurred configuring root project 'swift-project'.")
        failure.assertHasCause("This property is locked and cannot be changed.")
    }

    @RequiresInstalledToolChain(ToolChainRequirement.SWIFTC_4)
    def "can build Swift 3 source code on Swift 4 compiler"() {
        given:
        makeSingleProject()
        swift3Component.writeToProject(testDirectory)
        buildFile << """
            ${componentUnderTestDsl} {
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
        settingsFile << "rootProject.name = '${swift3Component.projectName}'"

        when:
        succeeds "verifyBinariesSwiftVersion"
        succeeds taskNameToAssembleDevelopmentBinary

        then:
        result.assertTasksExecuted(tasksToAssembleDevelopmentBinaryOfComponentUnderTest, ":$taskNameToAssembleDevelopmentBinary")
    }

    @RequiresInstalledToolChain(ToolChainRequirement.SWIFTC_3)
    def "throws exception with meaningful message when building Swift 4 source code on Swift 3 compiler"() {
        given:
        makeSingleProject()
        swift4Component.writeToProject(testDirectory)
        buildFile << """
            ${componentUnderTestDsl} {
                sourceCompatibility = SwiftVersion.SWIFT4
            }

            task verifyBinariesSwiftVersion {
                doLast {
                    ${componentUnderTestDsl}.binaries.get().each {
                        assert it.sourceCompatibility.get() == SwiftVersion.SWIFT4
                    }
                }
            }
        """
        settingsFile << "rootProject.name = '${swift4Component.projectName}'"

        when:
        succeeds "verifyBinariesSwiftVersion"
        fails taskNameToAssembleDevelopmentBinary

        then:
        failure.assertHasDescription("Execution failed for task '$developmentBinaryCompileTask'.")
        failure.assertHasCause("swiftc compiler version '${toolChain.version}' doesn't support Swift language version '${SwiftVersion.SWIFT4.version}'")
    }

    @RequiresInstalledToolChain(ToolChainRequirement.SWIFTC_3)
    def "can compile Swift 3 component on Swift 3 compiler"() {
        given:
        makeSingleProject()
        swift3Component.writeToProject(testDirectory)
        buildFile << """
            task verifyBinariesSwiftVersion {
                doLast {
                    ${componentUnderTestDsl}.binaries.get().each {
                        assert it.sourceCompatibility.get() == SwiftVersion.SWIFT3
                    }
                }
            }
        """
        settingsFile << "rootProject.name = '${swift3Component.projectName}'"

        when:
        succeeds "verifyBinariesSwiftVersion"
        succeeds taskNameToAssembleDevelopmentBinary

        then:
        result.assertTasksExecuted(tasksToAssembleDevelopmentBinaryOfComponentUnderTest, ":$taskNameToAssembleDevelopmentBinary")
    }

    @RequiresInstalledToolChain(ToolChainRequirement.SWIFTC_4)
    def "can compile Swift 4 component on Swift 4 compiler"() {
        given:
        makeSingleProject()
        swift4Component.writeToProject(testDirectory)
        buildFile << """
            task verifyBinariesSwiftVersion {
                doLast {
                    ${componentUnderTestDsl}.binaries.get().each {
                        assert it.sourceCompatibility.get() == SwiftVersion.SWIFT4
                    }
                }
            }
        """
        settingsFile << "rootProject.name = '${swift4Component.projectName}'"

        when:
        succeeds "verifyBinariesSwiftVersion"
        succeeds taskNameToAssembleDevelopmentBinary

        then:
        result.assertTasksExecuted(tasksToAssembleDevelopmentBinaryOfComponentUnderTest, ":$taskNameToAssembleDevelopmentBinary")
    }

    abstract String getDevelopmentBinaryCompileTask()

    abstract SourceElement getSwift3Component()

    abstract SourceElement getSwift4Component()

    abstract String getTaskNameToAssembleDevelopmentBinary()

    abstract List<String> getTasksToAssembleDevelopmentBinaryOfComponentUnderTest()
}
