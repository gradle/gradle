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

import org.gradle.internal.os.OperatingSystem
import org.gradle.language.AbstractNativeLanguageComponentIntegrationTest
import org.gradle.nativeplatform.fixtures.ToolChainRequirement
import org.gradle.nativeplatform.fixtures.app.SourceElement
import org.gradle.util.GUtil

abstract class AbstractCppComponentIntegrationTest extends AbstractNativeLanguageComponentIntegrationTest {
    def "can build on current operating system family when explicitly specified"() {
        given:
        makeSingleProject()
        componentUnderTest.writeToProject(testDirectory)

        and:
        buildFile << """
            ${componentUnderTestDsl} {
                targetMachines = [machines.host()]
            }
        """

        expect:
        succeeds taskNameToAssembleDevelopmentBinary
        result.assertTasksExecuted(tasksToAssembleDevelopmentBinary, ":$taskNameToAssembleDevelopmentBinary")
        result.assertTasksNotSkipped(tasksToAssembleDevelopmentBinary, ":$taskNameToAssembleDevelopmentBinary")
    }

    def "ignores compile and link tasks when current operating system family is excluded"() {
        given:
        makeSingleProject()
        componentUnderTest.writeToProject(testDirectory)

        and:
        buildFile << """
            ${componentUnderTestDsl} {
                targetMachines = [machines.of('some-other-family', 'x86')]
            }
        """

        expect:
        succeeds taskNameToAssembleDevelopmentBinary
        result.assertTasksExecuted(":$taskNameToAssembleDevelopmentBinary")
        result.assertTasksSkipped(":$taskNameToAssembleDevelopmentBinary")
    }

    def "fails configuration when no operating system family is configured"() {
        given:
        makeSingleProject()
        componentUnderTest.writeToProject(testDirectory)

        and:
        buildFile << """
            ${componentUnderTestDsl} {
                targetMachines = []
            }
        """

        expect:
        fails taskNameToAssembleDevelopmentBinary
        failure.assertHasDescription("A problem occurred configuring root project '${testDirectory.name}'.")
        failure.assertHasCause("An operating system needs to be specified for the ${GUtil.toWords(componentUnderTestDsl, (char) ' ')}.")
    }

    @Override
    protected String getDefaultArchitecture() {
        if (toolChain.meets(ToolChainRequirement.GCC) && OperatingSystem.current().windows) {
            return "x86"
        }
        return super.defaultArchitecture
    }

    protected abstract SourceElement getComponentUnderTest()

    protected abstract List<String> getTasksToAssembleDevelopmentBinary()

    protected abstract String getTaskNameToAssembleDevelopmentBinary()
}
