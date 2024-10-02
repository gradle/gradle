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

import org.gradle.nativeplatform.fixtures.RequiresInstalledToolChain
import org.gradle.nativeplatform.fixtures.ToolChainRequirement
import org.gradle.nativeplatform.fixtures.app.SourceElement
import org.gradle.nativeplatform.fixtures.app.Swift3
import org.gradle.nativeplatform.fixtures.app.Swift4
import org.gradle.nativeplatform.fixtures.app.Swift5
import org.gradle.util.Matchers

@RequiresInstalledToolChain(ToolChainRequirement.SWIFTC)
abstract class AbstractSwiftIntegrationTest extends AbstractSwiftComponentIntegrationTest {
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
        file("src/main/swift/broken.swift") << "broken!"

        expect:
        fails "assemble"
        failure.assertHasDescription("Execution failed for task '$developmentBinaryCompileTask'.")
        failure.assertHasCause("A build operation failed.")
        failure.assertThatCause(Matchers.containsText("Swift compiler failed while compiling swift file(s)"))
    }

    // TODO Move this to AbstractSwiftComponentIntegrationTest when xcode test works properly with architecture
    def "can build for current machine when multiple target machines are specified"() {
        given:
        makeSingleProject()
        settingsFile << "rootProject.name = '${componentUnderTest.projectName}'"
        componentUnderTest.writeToProject(testDirectory)

        and:
        buildFile << """
            ${componentUnderTestDsl} {
                targetMachines = [machines.linux, machines.macOS]
            }
        """

        expect:
        succeeds taskNameToAssembleDevelopmentBinary
        result.assertTasksExecutedAndNotSkipped getTasksToAssembleDevelopmentBinary(currentOsFamilyName.toLowerCase()), ":${taskNameToAssembleDevelopmentBinary}"
    }

    // TODO Move this to AbstractSwiftComponentIntegrationTest when xcode test works properly with architecture
    def "fails when 32-bit architecture is specified"() {
        given:
        makeSingleProject()
        componentUnderTest.writeToProject(testDirectory)

        and:
        buildFile << """
            ${componentUnderTestDsl} {
                targetMachines = [machines.os('${currentOsFamilyName}').x86]
            }
        """

        expect:
        fails taskNameToAssembleDevelopmentBinary
        failure.assertHasCause("No tool chain has support to build Swift")
    }

    // TODO Move this to AbstractCppComponentIntegrationTest when unit test works properly with architecture
    def "ignores duplicate target machines"() {
        given:
        makeSingleProject()
        settingsFile << "rootProject.name = '${componentUnderTest.projectName}'"
        componentUnderTest.writeToProject(testDirectory)

        and:
        buildFile << configureTargetMachines("machines.${currentHostOperatingSystemFamilyDsl}", "machines.${currentHostOperatingSystemFamilyDsl}")
        buildFile << """
            task verifyTargetMachines {
                def targetMachines = ${componentUnderTestDsl}.targetMachines
                def hostMachine = machines.${currentHostOperatingSystemFamilyDsl}
                doLast {
                    assert targetMachines.get().size() == 1
                    assert targetMachines.get() == [hostMachine] as Set
                }
            }
        """

        expect:
        succeeds "verifyTargetMachines"
    }

    protected abstract List<String> getTasksToAssembleDevelopmentBinary(String variant = "")

    @Override
    SourceElement getSwift3Component() {
        return new Swift3('project')
    }

    @Override
    SourceElement getSwift4Component() {
        return new Swift4('project')
    }

    @Override
    SourceElement getSwift5Component() {
        return new Swift5('project')
    }

    @Override
    String getTaskNameToAssembleDevelopmentBinary() {
        return "assemble"
    }

    @Override
    String getDevelopmentBinaryCompileTask() {
        return ":compileDebugSwift"
    }

    @Override
    String getComponentName() {
        return "main"
    }

    @Override
    List<String> getTasksToAssembleDevelopmentBinaryOfComponentUnderTest() {
        return getTasksToAssembleDevelopmentBinary()
    }
}
