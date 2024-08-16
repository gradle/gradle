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

import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.language.AbstractNativeLanguageComponentIntegrationTest
import org.gradle.nativeplatform.OperatingSystemFamily
import org.gradle.nativeplatform.fixtures.RequiresInstalledToolChain
import org.gradle.nativeplatform.fixtures.ToolChainRequirement
import org.gradle.nativeplatform.fixtures.app.SourceElement
import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform
import org.gradle.test.fixtures.file.DoesNotSupportNonAsciiPaths
import org.hamcrest.CoreMatchers

@RequiresInstalledToolChain(ToolChainRequirement.SWIFTC)
@DoesNotSupportNonAsciiPaths(reason = "swiftc does not support these paths")
abstract class AbstractSwiftComponentIntegrationTest extends AbstractNativeLanguageComponentIntegrationTest {

    @ToBeFixedForConfigurationCache(bottomSpecs = [
        'SwiftXCTestComponentWithBothLibraryLinkageIntegrationTest',
        'SwiftXCTestComponentWithSharedLibraryLinkageIntegrationTest',
        'SwiftXCTestComponentWithStaticLibraryLinkageIntegrationTest',
        'SwiftXCTestComponentWithoutComponentIntegrationTest',
        'SwiftXCTestComponentWithApplicationIntegrationTest'
    ])
    def "sources are built with Swift tools"() {
        given:
        makeSingleProject()
        componentUnderTest.writeToProject(testDirectory)
        settingsFile << "rootProject.name = '${componentUnderTest.projectName}'"

        expect:
        succeeds taskNameToAssembleDevelopmentBinary
        result.assertTasksExecuted(tasksToAssembleDevelopmentBinaryOfComponentUnderTest, ":$taskNameToAssembleDevelopmentBinary")
        assertComponentUnderTestWasBuilt()
    }

    def "binaries have the right Swift version"() {
        given:
        makeSingleProject()
        def expectedVersion = AbstractNativeLanguageComponentIntegrationTest.toolChain.version.major
        verifySwiftVersion(expectedVersion)

        expect:
        succeeds "verifyBinariesSwiftVersion"
    }

    def "throws exception when modifying Swift component source compatibility after the binary source compatibility is queried"() {
        given:
        makeSingleProject()
        buildFile << """
            ${componentUnderTestDsl} {
                sourceCompatibility = SwiftVersion.SWIFT3
            }

            ${componentUnderTestDsl}.binaries.whenElementKnown {
                assert it.targetPlatform.sourceCompatibility == SwiftVersion.SWIFT3
                ${componentUnderTestDsl}.sourceCompatibility = SwiftVersion.SWIFT4
            }

            task verifyBinariesSwiftVersion {}
        """
        settingsFile << "rootProject.name = 'swift-project'"

        expect:
        fails "verifyBinariesSwiftVersion"
        failure.assertHasDescription("A problem occurred configuring root project 'swift-project'.")
        failure.assertThatCause(CoreMatchers.containsString("property 'sourceCompatibility' is final and cannot be changed any further."))
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
        """
        verifySwiftVersion(3)
        settingsFile << "rootProject.name = '${swift3Component.projectName}'"

        when:
        succeeds "verifyBinariesSwiftVersion"
        succeeds taskNameToAssembleDevelopmentBinary

        then:
        result.assertTasksExecuted(tasksToAssembleDevelopmentBinaryOfComponentUnderTest, ":$taskNameToAssembleDevelopmentBinary")
    }

    @ToBeFixedForConfigurationCache(bottomSpecs = [
        'SwiftXCTestComponentWithBothLibraryLinkageIntegrationTest',
        'SwiftXCTestComponentWithSharedLibraryLinkageIntegrationTest',
        'SwiftXCTestComponentWithStaticLibraryLinkageIntegrationTest',
        'SwiftXCTestComponentWithoutComponentIntegrationTest',
        'SwiftXCTestComponentWithApplicationIntegrationTest'
    ])
    @RequiresInstalledToolChain(ToolChainRequirement.SWIFTC_5)
    def "can build Swift 4 source code on Swift 5 compiler"() {
        given:
        makeSingleProject()
        swift4Component.writeToProject(testDirectory)
        buildFile << """
            ${componentUnderTestDsl} {
                sourceCompatibility = SwiftVersion.SWIFT4
            }
        """
        verifySwiftVersion(4)
        settingsFile << "rootProject.name = '${swift4Component.projectName}'"

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
        """
        verifySwiftVersion(4)
        settingsFile << "rootProject.name = '${swift4Component.projectName}'"

        when:
        succeeds "verifyBinariesSwiftVersion"
        fails taskNameToAssembleDevelopmentBinary

        then:
        failure.assertHasDescription("Execution failed for task '$developmentBinaryCompileTask'.")
        failure.assertHasCause("Swift compiler version '${toolChain.version}' doesn't support Swift language version '${SwiftVersion.SWIFT4.version}'")
    }

    @RequiresInstalledToolChain(ToolChainRequirement.SWIFTC_3)
    def "throws exception with meaningful message when building Swift 5 source code on Swift 3 compiler"() {
        given:
        makeSingleProject()
        swift5Component.writeToProject(testDirectory)
        buildFile << """
            ${componentUnderTestDsl} {
                sourceCompatibility = SwiftVersion.SWIFT5
            }
        """
        verifySwiftVersion(5)
        settingsFile << "rootProject.name = '${swift5Component.projectName}'"

        when:
        succeeds "verifyBinariesSwiftVersion"
        fails taskNameToAssembleDevelopmentBinary

        then:
        failure.assertHasDescription("Execution failed for task '$developmentBinaryCompileTask'.")
        failure.assertHasCause("Swift compiler version '${toolChain.version}' doesn't support Swift language version '${SwiftVersion.SWIFT5.version}'")
    }

    @RequiresInstalledToolChain(ToolChainRequirement.SWIFTC_4)
    def "throws exception with meaningful message when building Swift 5 source code on Swift 4 compiler"() {
        given:
        makeSingleProject()
        swift5Component.writeToProject(testDirectory)
        buildFile << """
            ${componentUnderTestDsl} {
                sourceCompatibility = SwiftVersion.SWIFT5
            }
        """
        verifySwiftVersion(5)
        settingsFile << "rootProject.name = '${swift5Component.projectName}'"

        when:
        succeeds "verifyBinariesSwiftVersion"
        fails taskNameToAssembleDevelopmentBinary

        then:
        failure.assertHasDescription("Execution failed for task '$developmentBinaryCompileTask'.")
        failure.assertHasCause("Swift compiler version '${toolChain.version}' doesn't support Swift language version '${SwiftVersion.SWIFT5.version}'")
    }

    @RequiresInstalledToolChain(ToolChainRequirement.SWIFTC_5)
    @ToBeFixedForConfigurationCache(bottomSpecs = [
        'SwiftXCTestComponentWithBothLibraryLinkageIntegrationTest',
        'SwiftXCTestComponentWithStaticLibraryLinkageIntegrationTest',
        'SwiftXCTestComponentWithSharedLibraryLinkageIntegrationTest',
        'SwiftXCTestComponentWithApplicationIntegrationTest',
        'SwiftXCTestComponentWithoutComponentIntegrationTest'
    ])
    def "throws exception with meaningful message when building Swift 3 source code on Swift 5 compiler"() {
        given:
        makeSingleProject()
        swift3Component.writeToProject(testDirectory)
        buildFile << """
            ${componentUnderTestDsl} {
                sourceCompatibility = SwiftVersion.SWIFT3
            }
        """
        verifySwiftVersion(3)
        settingsFile << "rootProject.name = '${swift3Component.projectName}'"

        when:
        succeeds "verifyBinariesSwiftVersion"
        fails taskNameToAssembleDevelopmentBinary

        then:
        failure.assertHasDescription("Execution failed for task '$developmentBinaryCompileTask'.")
        failure.assertHasCause("Swift compiler version '${toolChain.version}' doesn't support Swift language version '${SwiftVersion.SWIFT3.version}'")
    }

    @RequiresInstalledToolChain(ToolChainRequirement.SWIFTC_3)
    def "can compile Swift 3 component on Swift 3 compiler"() {
        given:
        makeSingleProject()
        swift3Component.writeToProject(testDirectory)
        verifySwiftVersion(3)
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
        verifySwiftVersion(4)
        settingsFile << "rootProject.name = '${swift4Component.projectName}'"

        when:
        succeeds "verifyBinariesSwiftVersion"
        succeeds taskNameToAssembleDevelopmentBinary

        then:
        result.assertTasksExecuted(tasksToAssembleDevelopmentBinaryOfComponentUnderTest, ":$taskNameToAssembleDevelopmentBinary")
    }

    @RequiresInstalledToolChain(ToolChainRequirement.SWIFTC_5)
    @ToBeFixedForConfigurationCache(bottomSpecs = [
        'SwiftXCTestComponentWithBothLibraryLinkageIntegrationTest',
        'SwiftXCTestComponentWithSharedLibraryLinkageIntegrationTest',
        'SwiftXCTestComponentWithStaticLibraryLinkageIntegrationTest',
        'SwiftXCTestComponentWithApplicationIntegrationTest',
        'SwiftXCTestComponentWithoutComponentIntegrationTest'
    ])
    def "can compile Swift 5 component on Swift 5 compiler"() {
        given:
        makeSingleProject()
        swift5Component.writeToProject(testDirectory)
        verifySwiftVersion(5)
        settingsFile << "rootProject.name = '${swift5Component.projectName}'"

        when:
        succeeds "verifyBinariesSwiftVersion"
        succeeds taskNameToAssembleDevelopmentBinary

        then:
        result.assertTasksExecuted(tasksToAssembleDevelopmentBinaryOfComponentUnderTest, ":$taskNameToAssembleDevelopmentBinary")
    }

    def "assemble task warns when current operating system family is excluded"() {
        given:
        makeSingleProject()
        componentUnderTest.writeToProject(testDirectory)

        and:
        buildFile << configureTargetMachines("machines.os('some-other-family')")

        expect:
        succeeds taskNameToAssembleDevelopmentBinary

        and:
        outputContains("'${componentName}' component in project ':' does not target this operating system.")
    }

    def "build task warns when current operating system family is excluded"() {
        given:
        makeSingleProject()
        componentUnderTest.writeToProject(testDirectory)

        and:
        buildFile << configureTargetMachines("machines.os('some-other-family')")

        expect:
        succeeds "build"

        and:
        outputContains("'${componentName}' component in project ':' does not target this operating system.")
    }

    def "does not fail when current operating system family is excluded but assemble is not invoked"() {
        given:
        makeSingleProject()
        componentUnderTest.writeToProject(testDirectory)

        and:
        buildFile << configureTargetMachines("machines.os('some-other-family')")

        expect:
        succeeds "help"
    }

    def "fails configuration when no target machine is configured"() {
        given:
        makeSingleProject()
        componentUnderTest.writeToProject(testDirectory)

        and:
        buildFile << configureTargetMachines('')

        expect:
        fails taskNameToAssembleDevelopmentBinary
        failure.assertHasDescription("A problem occurred configuring root project '${testDirectory.name}'.")
        failure.assertHasCause("A target machine needs to be specified")
    }

    protected String getCurrentHostOperatingSystemFamilyDsl() {
        String osFamily = DefaultNativePlatform.getCurrentOperatingSystem().toFamilyName()
        if (osFamily == OperatingSystemFamily.MACOS) {
            return "macOS"
        } else {
            return osFamily
        }
    }

    protected String getCurrentHostOperatingSystemName() {
        return DefaultNativePlatform.getCurrentOperatingSystem().toFamilyName()
    }

    protected String getCurrentHostArchName() {
        if (DefaultNativePlatform.getCurrentArchitecture().arm64) {
            return "aarch64"
        } else {
            return "x86-64"
        }
    }

    private void verifySwiftVersion(int version) {
        buildFile << """
            task verifyBinariesSwiftVersion {
                def sourceCompatibilities = provider {
                    // `binaries` is not a provider, so cannot map it
                    ${componentUnderTestDsl}.binaries.get().collect { it.targetPlatform.sourceCompatibility }
                }
                doLast {
                    sourceCompatibilities.get().each {
                        assert it.version == $version
                    }
                }
            }
        """
    }

    abstract String getDevelopmentBinaryCompileTask()

    abstract SourceElement getSwift3Component()

    abstract SourceElement getSwift4Component()

    abstract SourceElement getSwift5Component()

    abstract List<String> getTasksToAssembleDevelopmentBinaryOfComponentUnderTest()

    abstract String getComponentName()

    abstract void assertComponentUnderTestWasBuilt()
}
