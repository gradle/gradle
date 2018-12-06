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

import org.gradle.language.AbstractNativeLanguageComponentIntegrationTest
import org.gradle.nativeplatform.MachineArchitecture
import org.gradle.nativeplatform.OperatingSystemFamily
import org.gradle.nativeplatform.fixtures.RequiresInstalledToolChain
import org.gradle.nativeplatform.fixtures.ToolChainRequirement
import org.gradle.nativeplatform.fixtures.app.SourceElement
import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform
import org.gradle.util.GUtil
import org.junit.Assume

abstract class AbstractCppComponentIntegrationTest extends AbstractNativeLanguageComponentIntegrationTest {
    def "can build on current operating system family and architecture when explicitly specified"() {
        given:
        makeSingleProject()
        componentUnderTest.writeToProject(testDirectory)

        and:
        buildFile << configureTargetMachines("machines.${currentHostOperatingSystemFamilyDsl}${currentHostArchitectureDsl}")

        expect:
        succeeds taskNameToAssembleDevelopmentBinary
        result.assertTasksExecutedAndNotSkipped(tasksToAssembleDevelopmentBinary, ":$taskNameToAssembleDevelopmentBinary")
    }

    def "ignores compile and link tasks when current operating system family is excluded"() {
        given:
        makeSingleProject()
        componentUnderTest.writeToProject(testDirectory)

        and:
        buildFile << configureTargetMachines("machines.os('some-other-family')")

        expect:
        succeeds taskNameToAssembleDevelopmentBinary
        result.assertTasksExecuted(":$taskNameToAssembleDevelopmentBinary")
        result.assertTasksSkipped(":$taskNameToAssembleDevelopmentBinary")
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
        failure.assertHasCause("A target machine needs to be specified for the ${GUtil.toWords(componentUnderTestDsl, (char) ' ')}.")
    }
    
    def "can build for current machine when multiple target machines are specified"() {
        Assume.assumeFalse(toolChain.meets(ToolChainRequirement.WINDOWS_GCC))

        given:
        makeSingleProject()
        componentUnderTest.writeToProject(testDirectory)

        and:
        buildFile << configureTargetMachines("machines.linux, machines.macOS, machines.windows")

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
        buildFile << configureTargetMachines("machines.${currentHostOperatingSystemFamilyDsl}.x86, machines.${currentHostOperatingSystemFamilyDsl}.x86_64")

        expect:
        succeeds getTaskNameToAssembleDevelopmentBinaryWithArchitecture(MachineArchitecture.X86), getTaskNameToAssembleDevelopmentBinaryWithArchitecture(MachineArchitecture.X86_64)
        result.assertTasksExecutedAndNotSkipped(getTasksToAssembleDevelopmentBinary(MachineArchitecture.X86),
                getTasksToAssembleDevelopmentBinary(MachineArchitecture.X86_64),
                getTaskNameToAssembleDevelopmentBinaryWithArchitecture(MachineArchitecture.X86),
                getTaskNameToAssembleDevelopmentBinaryWithArchitecture(MachineArchitecture.X86_64))
    }

    def "fails when no target architecture can be built"() {
        given:
        makeSingleProject()
        componentUnderTest.writeToProject(testDirectory)

        and:
        buildFile << configureTargetMachines("machines.os('${currentOsFamilyName}').architecture('foo')")

        expect:
        fails taskNameToAssembleDevelopmentBinary
        failure.assertHasCause("No tool chain is available to build C++")
    }

    def "can build current architecture when other, non-buildable architectures are specified"() {
        Assume.assumeFalse(toolChain.meets(ToolChainRequirement.WINDOWS_GCC))

        given:
        makeSingleProject()
        componentUnderTest.writeToProject(testDirectory)

        and:
        buildFile << configureTargetMachines("machines.${currentHostOperatingSystemFamilyDsl}.architecture('foo'), machines.${currentHostOperatingSystemFamilyDsl}${currentHostArchitectureDsl}")

        expect:
        succeeds taskNameToAssembleDevelopmentBinary
        result.assertTasksExecutedAndNotSkipped(getTasksToAssembleDevelopmentBinary(currentArchitecture), ":$taskNameToAssembleDevelopmentBinary")
    }

    def "ignores duplicate target machines"() {
        Assume.assumeFalse(toolChain.meets(ToolChainRequirement.WINDOWS_GCC))

        given:
        makeSingleProject()
        componentUnderTest.writeToProject(testDirectory)

        and:
        buildFile << configureTargetMachines("machines.${currentHostOperatingSystemFamilyDsl}.architecture('foo'), machines.${currentHostOperatingSystemFamilyDsl}${currentHostArchitectureDsl}, machines.${currentHostOperatingSystemFamilyDsl}${currentHostArchitectureDsl}")

        expect:
        succeeds taskNameToAssembleDevelopmentBinary
        result.assertTasksExecutedAndNotSkipped(getTasksToAssembleDevelopmentBinary(currentArchitecture), ":$taskNameToAssembleDevelopmentBinary")
    }

    @Override
    protected String getDefaultArchitecture() {
        return toolChain.meets(ToolChainRequirement.WINDOWS_GCC) ? "x86" : super.defaultArchitecture
    }

    protected String getCurrentHostArchitectureDsl() {
        return toolChain.meets(ToolChainRequirement.WINDOWS_GCC) ? ".x86" : ""
    }

    protected String getCurrentHostOperatingSystemFamilyDsl() {
        String osFamily = DefaultNativePlatform.getCurrentOperatingSystem().toFamilyName()
        if (osFamily == OperatingSystemFamily.MACOS) {
            return "macOS"
        } else {
            return osFamily
        }
    }

    protected abstract SourceElement getComponentUnderTest()

    protected abstract List<String> getTasksToAssembleDevelopmentBinary(String variant = "")

    protected abstract String getTaskNameToAssembleDevelopmentBinary()

    protected abstract String getTaskNameToAssembleDevelopmentBinaryWithArchitecture(String architecture)

    protected configureTargetMachines(String targetMachines) {
        return """
            ${componentUnderTestDsl} {
                targetMachines = [${targetMachines}]
            }
        """
    }
}
