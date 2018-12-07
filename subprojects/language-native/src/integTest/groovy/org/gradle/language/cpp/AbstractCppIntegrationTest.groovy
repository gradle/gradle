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

package org.gradle.language.cpp

import org.gradle.nativeplatform.MachineArchitecture
import org.gradle.util.Matchers

import static org.gradle.nativeplatform.fixtures.ToolChainRequirement.WINDOWS_GCC
import static org.junit.Assume.assumeFalse

abstract class AbstractCppIntegrationTest extends AbstractCppComponentIntegrationTest {
    def "skip assemble tasks when no source"() {
        given:
        makeSingleProject()

        expect:
        succeeds "assemble"
        result.assertTasksExecuted(tasksToAssembleDevelopmentBinary, ":assemble")
        // TODO - should skip the task as NO-SOURCE
        result.assertTasksSkipped(tasksToAssembleDevelopmentBinary, ":assemble")
    }

    def "build fails when compilation fails"() {
        given:
        makeSingleProject()

        and:
        file("src/main/cpp/broken.cpp") << "broken!"

        expect:
        fails "assemble"
        failure.assertHasDescription("Execution failed for task '$developmentBinaryCompileTask'.")
        failure.assertHasCause("A build operation failed.")
        failure.assertThatCause(Matchers.containsText("C++ compiler failed while compiling broken.cpp"))
    }

    // TODO Move this to AbstractCppComponentIntegrationTest when unit test works properly with architecture
    def "can build for current machine when multiple target machines are specified"() {
        assumeFalse(toolChain.meets(WINDOWS_GCC))

        given:
        makeSingleProject()
        componentUnderTest.writeToProject(testDirectory)

        and:
        buildFile << """
            ${componentUnderTestDsl} {
                targetMachines = [machines.linux, machines.macOS, machines.windows]
            }
        """

        expect:
        succeeds taskNameToAssembleDevelopmentBinary
        result.assertTasksExecutedAndNotSkipped getTasksToAssembleDevelopmentBinary(currentOsFamilyName.toLowerCase()), ":${taskNameToAssembleDevelopmentBinary}"
    }

    // TODO Move this to AbstractCppComponentIntegrationTest when unit test works properly with architecture
    def "can build for multiple target machines"() {
        assumeFalse(toolChain.meets(WINDOWS_GCC))

        given:
        makeSingleProject()
        componentUnderTest.writeToProject(testDirectory)

        and:
        buildFile << """
            ${componentUnderTestDsl} {
                targetMachines = [machines.${currentHostOperatingSystemFamilyDsl}.x86, machines.${currentHostOperatingSystemFamilyDsl}.x86_64]
            }
        """

        expect:
        succeeds getTaskNameToAssembleDevelopmentBinaryWithArchitecture(MachineArchitecture.X86), getTaskNameToAssembleDevelopmentBinaryWithArchitecture(MachineArchitecture.X86_64)
        result.assertTasksExecutedAndNotSkipped(getTasksToAssembleDevelopmentBinary(MachineArchitecture.X86),
                getTasksToAssembleDevelopmentBinary(MachineArchitecture.X86_64),
                getTaskNameToAssembleDevelopmentBinaryWithArchitecture(MachineArchitecture.X86),
                getTaskNameToAssembleDevelopmentBinaryWithArchitecture(MachineArchitecture.X86_64))
    }

    // TODO Move this to AbstractCppComponentIntegrationTest when unit test works properly with architecture
    def "fails when no target architecture can be built"() {
        given:
        makeSingleProject()
        componentUnderTest.writeToProject(testDirectory)

        and:
        buildFile << """
            ${componentUnderTestDsl} {
                targetMachines = [machines.os('${currentOsFamilyName}').architecture('foo')]
            }
        """

        expect:
        fails taskNameToAssembleDevelopmentBinary
        failure.assertHasCause("No tool chain is available to build C++")
    }

    // TODO Move this to AbstractCppComponentIntegrationTest when unit test works properly with architecture
    def "can build current architecture when other, non-buildable architectures are specified"() {
        assumeFalse(toolChain.meets(WINDOWS_GCC))
        
        given:
        makeSingleProject()
        componentUnderTest.writeToProject(testDirectory)

        and:
        buildFile << """
            ${componentUnderTestDsl} {
                targetMachines = [machines.${currentHostOperatingSystemFamilyDsl}.architecture('foo'), machines.${currentHostOperatingSystemFamilyDsl}${currentHostArchitectureDsl}]
            }
        """

        expect:
        succeeds taskNameToAssembleDevelopmentBinary
        result.assertTasksExecutedAndNotSkipped(getTasksToAssembleDevelopmentBinary(currentArchitecture), ":$taskNameToAssembleDevelopmentBinary")
    }

    // TODO Move this to AbstractCppComponentIntegrationTest when unit test works properly with architecture
    def "ignores duplicate target machines"() {
        assumeFalse(toolChain.meets(WINDOWS_GCC))

        given:
        makeSingleProject()
        componentUnderTest.writeToProject(testDirectory)

        and:
        buildFile << """
            ${componentUnderTestDsl} {
                targetMachines = [machines.host().architecture('foo'), machines.host()${currentHostArchitectureDsl}, machines.host()${currentHostArchitectureDsl}]
            }
        """

        expect:
        succeeds taskNameToAssembleDevelopmentBinary
        result.assertTasksExecutedAndNotSkipped(getTasksToAssembleDevelopmentBinary(currentArchitecture), ":$taskNameToAssembleDevelopmentBinary")
    }

    protected abstract String getDevelopmentBinaryCompileTask()

    @Override
    protected String getTaskNameToAssembleDevelopmentBinary() {
        return 'assemble'
    }

    protected String getTaskNameToAssembleDevelopmentBinaryWithArchitecture(String architecture) {
        return ":assembleDebug${architecture.capitalize()}"
    }
}
