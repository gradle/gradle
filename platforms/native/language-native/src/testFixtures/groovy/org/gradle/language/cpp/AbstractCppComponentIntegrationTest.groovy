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

package org.gradle.language.cpp

import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.language.AbstractNativeLanguageComponentIntegrationTest
import org.gradle.nativeplatform.MachineArchitecture
import org.gradle.nativeplatform.fixtures.RequiresInstalledToolChain
import org.gradle.nativeplatform.fixtures.ToolChainRequirement
import org.gradle.util.internal.GUtil

abstract class AbstractCppComponentIntegrationTest extends AbstractNativeLanguageComponentIntegrationTest {
    def "can build on current operating system family and architecture when explicitly specified"() {
        given:
        makeSingleProject()
        componentUnderTest.writeToProject(testDirectory)

        and:
        buildFile << configureTargetMachines("machines.${currentHostOperatingSystemFamilyDsl}")

        expect:
        succeeds taskNameToAssembleDevelopmentBinary
        result.assertTasksExecutedAndNotSkipped(tasksToAssembleDevelopmentBinary, ":$taskNameToAssembleDevelopmentBinary")
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
        buildFile << configureTargetMachines()

        expect:
        fails taskNameToAssembleDevelopmentBinary
        failure.assertHasDescription("A problem occurred configuring root project '${testDirectory.name}'.")
        failure.assertHasCause("A target machine needs to be specified for the ${GUtil.toWords(componentUnderTestDsl, (char) ' ')}.")
    }

    def "can build for current machine when multiple target machines are specified"() {
        given:
        makeSingleProject()
        componentUnderTest.writeToProject(testDirectory)

        and:
        buildFile << configureTargetMachines("machines.linux", "machines.macOS", "machines.windows")

        expect:
        succeeds taskNameToAssembleDevelopmentBinary
        result.assertTasksExecutedAndNotSkipped getTasksToAssembleDevelopmentBinary(currentOsFamilyName.toLowerCase()), ":${taskNameToAssembleDevelopmentBinary}"
    }

    @RequiresInstalledToolChain(ToolChainRequirement.SUPPORTS_32_AND_64)
    def "can build for multiple target machines"() {
        given:
        makeSingleProject()
        componentUnderTest.writeToProject(testDirectory)

        and:
        buildFile << configureTargetMachines("machines.${currentHostOperatingSystemFamilyDsl}.x86", "machines.${currentHostOperatingSystemFamilyDsl}.x86_64")

        expect:
        succeeds getTaskNameToAssembleDevelopmentBinaryWithArchitecture(MachineArchitecture.X86), getTaskNameToAssembleDevelopmentBinaryWithArchitecture(MachineArchitecture.X86_64)
        result.assertTasksExecutedAndNotSkipped(getTasksToAssembleDevelopmentBinary(MachineArchitecture.X86),
                getTasksToAssembleDevelopmentBinary(MachineArchitecture.X86_64),
                getTaskNameToAssembleDevelopmentBinaryWithArchitecture(MachineArchitecture.X86),
                getTaskNameToAssembleDevelopmentBinaryWithArchitecture(MachineArchitecture.X86_64))
    }

    @ToBeFixedForConfigurationCache(bottomSpecs = [
        'CppUnitTestComponentWithStaticLibraryLinkageIntegrationTest',
        'CppUnitTestComponentWithBothLibraryLinkageIntegrationTest',
        'CppUnitTestComponentWithSharedLibraryLinkageIntegrationTest'
    ], because = "may compile main and test binaries concurrently so may fail with multiple exceptions")
    def "fails when no target architecture can be built"() {
        given:
        makeSingleProject()
        componentUnderTest.writeToProject(testDirectory)

        and:
        buildFile << configureTargetMachines("machines.${currentHostOperatingSystemFamilyDsl}.architecture('foo')")
        buildFile << configureToolChainSupport('foo')

        expect:
        fails taskNameToAssembleDevelopmentBinary
        failure.assertHasCause("No tool chain is available to build C++")
    }

    def "can build current architecture when other, non-buildable architectures are specified"() {
        given:
        makeSingleProject()
        componentUnderTest.writeToProject(testDirectory)

        and:
        buildFile << configureTargetMachines("machines.${currentHostOperatingSystemFamilyDsl}.architecture('foo')", "machines.${currentHostOperatingSystemFamilyDsl}")
        buildFile << configureToolChainSupport('foo')

        expect:
        succeeds taskNameToAssembleDevelopmentBinary
        result.assertTasksExecutedAndNotSkipped(getTasksToAssembleDevelopmentBinary(currentArchitecture), ":$taskNameToAssembleDevelopmentBinary")
    }

    def "ignores duplicate target machines"() {
        given:
        makeSingleProject()
        componentUnderTest.writeToProject(testDirectory)

        and:
        buildFile << configureTargetMachines("machines.${currentHostOperatingSystemFamilyDsl}", "machines.${currentHostOperatingSystemFamilyDsl}")
        buildFile << """
            task verifyTargetMachineCount {
                def targetMachines = ${componentUnderTestDsl}.targetMachines
                def expectedMachine = machines.${currentHostOperatingSystemFamilyDsl}
                doLast {
                    assert targetMachines.get().size() == 1
                    assert targetMachines.get() == [expectedMachine] as Set
                }
            }
        """

        expect:
        succeeds "verifyTargetMachineCount"
    }

    def "can specify unbuildable architecture as a component target machine"() {
        given:
        makeSingleProject()
        componentUnderTest.writeToProject(testDirectory)

        and:
        buildFile << configureTargetMachines("machines.${currentHostOperatingSystemFamilyDsl}", "machines.${currentHostOperatingSystemFamilyDsl}.architecture('foo')")
        buildFile << configureToolChainSupport('foo')

        expect:
        succeeds taskNameToAssembleDevelopmentBinary
    }

    protected abstract List<String> getTasksToAssembleDevelopmentBinary(String variant = "")

    protected abstract String getTaskNameToAssembleDevelopmentBinaryWithArchitecture(String architecture)

    protected abstract String getComponentName()

    protected String configureToolChainSupport(String architecture) {
        return """
            model {
                toolChains {
                    toolChainFor${architecture.capitalize()}Architecture(Gcc) {
                        path "/not/found"
                        target("host:${architecture}")
                    }
                }
            }
        """
    }
}
