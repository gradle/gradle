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
import org.gradle.nativeplatform.fixtures.app.SourceFileElement
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import org.gradle.util.VersionNumber
import org.hamcrest.Matchers
import org.junit.Assume

@Requires(TestPrecondition.SWIFT_SUPPORT)
abstract class AbstractSwiftComponentIntegrationTest extends AbstractNativeLanguageComponentIntegrationTest {
    def "binaries have the right Swift version"() {
        given:
        makeSingleProject()
        buildFile << """
            task verifyBinariesSwiftVersion {
                doLast {
                    ${componentUnderTestDsl}.binaries.get().each {
                        assert it.swiftLanguageVersion == SwiftLanguageVersion.of(${
            VersionNumber.canonicalName}.parse('${AbstractNativeLanguageComponentIntegrationTest.toolChain.version}'))
                    }
                }
            }
        """

        expect:
        succeeds "verifyBinariesSwiftVersion"
    }

    def "throws exception when modifying Swift component language support after the binaries are known"() {
        Assume.assumeThat(AbstractNativeLanguageComponentIntegrationTest.toolChain.version.major, Matchers.equalTo(4))

        given:
        makeSingleProject()
        buildFile << """
            ${componentUnderTestDsl} {
                swiftLanguageVersionSupport = SwiftLanguageVersion.SWIFT3
            }

            ${componentUnderTestDsl}.binaries.whenElementKnown {
                ${componentUnderTestDsl}.swiftLanguageVersionSupport = SwiftLanguageVersion.SWIFT4
            }
        """
        settingsFile << "rootProject.name = 'swift-project'"

        expect:
        fails "verifyBinariesSwiftVersion"
        failure.assertHasDescription("A problem occurred configuring root project 'swift-project'.")
        failure.assertHasCause("This property is locked and cannot be changed.")
    }

    def "can build Swift 3 source code on Swift 4 compiler"() {
        Assume.assumeThat(AbstractNativeLanguageComponentIntegrationTest.toolChain.version.major, Matchers.equalTo(4))

        given:
        makeSingleProject()
        swift3Component.writeToProject(testDirectory)
        buildFile << """
            ${componentUnderTestDsl} {
                swiftLanguageVersionSupport = SwiftLanguageVersion.SWIFT3
            }

            task verifyBinariesSwiftVersion {
                doLast {
                    ${componentUnderTestDsl}.binaries.get().each {
                        assert it.swiftLanguageVersion == SwiftLanguageVersion.SWIFT3
                    }
                }
            }
        """
        settingsFile << "rootProject.name = 'project'"

        when:
        succeeds "verifyBinariesSwiftVersion"
        succeeds taskNameToAssembleDevelopmentBinary

        then:
        result.assertTasksExecuted(tasksToAssembleDevelopmentBinaryOfComponentUnderTest, ":$taskNameToAssembleDevelopmentBinary")
    }

    def "throws exception with meaningful message when building Swift 4 source code on Swift 3 compiler"() {
        Assume.assumeThat(AbstractNativeLanguageComponentIntegrationTest.toolChain.version.major, Matchers.equalTo(3))

        given:
        makeSingleProject()
        swift4Component.writeToProject(testDirectory)
        buildFile << """
            ${componentUnderTestDsl} {
                swiftLanguageVersionSupport = SwiftLanguageVersion.SWIFT4
            }

            task verifyBinariesSwiftVersion {
                doLast {
                    ${componentUnderTestDsl}.binaries.get().each {
                        assert it.swiftLanguageVersion == SwiftLanguageVersion.SWIFT4
                    }
                }
            }
        """
        settingsFile << "rootProject.name = 'project'"

        when:
        succeeds "verifyBinariesSwiftVersion"
        fails taskNameToAssembleDevelopmentBinary

        then:
        failure.assertHasDescription("Execution failed for task ':compileDebugSwift'.")
        failure.assertHasCause("swiftc compiler version '${toolChain.version}' doesn't support Swift language version '${SwiftLanguageVersion.SWIFT4.version}'")
    }

    abstract SourceFileElement getSwift3Component()

    abstract SourceFileElement getSwift4Component()

    abstract String getTaskNameToAssembleDevelopmentBinary()

    abstract List<String> getTasksToAssembleDevelopmentBinaryOfComponentUnderTest()
}
