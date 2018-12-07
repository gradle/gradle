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
import org.gradle.nativeplatform.fixtures.ToolChainRequirement
import org.gradle.nativeplatform.fixtures.app.SourceElement
import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform
import org.gradle.util.GUtil

abstract class AbstractCppComponentIntegrationTest extends AbstractNativeLanguageComponentIntegrationTest {
    def "can build on current operating system family and architecture when explicitly specified"() {
        given:
        makeSingleProject()
        componentUnderTest.writeToProject(testDirectory)

        and:
        buildFile << """
            ${componentUnderTestDsl} {
                targetMachines = [machines.${currentHostOperatingSystemFamilyDsl}${currentHostArchitectureDsl}]
            }
        """

        expect:
        succeeds taskNameToAssembleDevelopmentBinary
        result.assertTasksExecutedAndNotSkipped(tasksToAssembleDevelopmentBinary, ":$taskNameToAssembleDevelopmentBinary")
    }

    def "ignores compile and link tasks when current operating system family is excluded"() {
        given:
        makeSingleProject()
        componentUnderTest.writeToProject(testDirectory)

        and:
        buildFile << """
            ${componentUnderTestDsl} {
                targetMachines = [machines.os('some-other-family')]
            }
        """

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
        buildFile << """
            ${componentUnderTestDsl} {
                targetMachines = []
            }
        """

        expect:
        fails taskNameToAssembleDevelopmentBinary
        failure.assertHasDescription("A problem occurred configuring root project '${testDirectory.name}'.")
        failure.assertHasCause("A target machine needs to be specified for the ${GUtil.toWords(componentUnderTestDsl, (char) ' ')}.")
    }

    @Override
    protected String getDefaultArchitecture() {
        return toolChain.meets(ToolChainRequirement.WINDOWS_GCC) ? "x86" : super.defaultArchitecture
    }

    protected String getCurrentHostArchitectureDsl() {
        return toolChain.meets(ToolChainRequirement.WINDOWS_GCC) ? ".x86" : ""
    }

    protected String getCurrentHostOperatingSystemFamilyDsl() {
        return DefaultNativePlatform.getCurrentOperatingSystem().toFamilyName()
    }

    protected abstract SourceElement getComponentUnderTest()

    protected abstract List<String> getTasksToAssembleDevelopmentBinary(String variant = "")

    protected abstract String getTaskNameToAssembleDevelopmentBinary()
}
