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

import org.gradle.internal.os.OperatingSystem
import org.gradle.language.AbstractNativeLanguageComponentIntegrationTest
import org.gradle.nativeplatform.fixtures.ToolChainRequirement
import org.gradle.nativeplatform.fixtures.app.SourceElement
import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform
import org.gradle.util.Matchers

abstract class AbstractCppIntegrationTest extends AbstractNativeLanguageComponentIntegrationTest {
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

    def "can build on current operating system family when explicitly specified"() {
        given:
        makeSingleProject()
        componentUnderTest.writeToProject(testDirectory)

        and:
        buildFile << """
            ${componentUnderTestDsl} {
                operatingSystems = [objects.named(OperatingSystemFamily, '${DefaultNativePlatform.currentOperatingSystem.toFamilyName()}')]
            }
        """

        expect:
        succeeds "assemble"
        result.assertTasksExecuted(tasksToAssembleDevelopmentBinary, ':assemble')
        result.assertTasksNotSkipped(tasksToAssembleDevelopmentBinary, ':assemble')
    }

    def "ignores compile and link tasks when current operating system family is excluded"() {
        given:
        makeSingleProject()
        componentUnderTest.writeToProject(testDirectory)

        and:
        buildFile << """
            ${componentUnderTestDsl} {
                operatingSystems = [objects.named(OperatingSystemFamily, 'some-other-family')]
            }
        """

        expect:
        succeeds "assemble"
        result.assertTasksExecuted(':assemble')
        result.assertTasksSkipped(':assemble')
    }

    def "fails configuration when no operating system family is configured"() {
        given:
        makeSingleProject()
        componentUnderTest.writeToProject(testDirectory)

        and:
        buildFile << """
            ${componentUnderTestDsl} {
                operatingSystems = []
            }
        """

        expect:
        fails "assemble"
        failure.assertHasDescription("A problem occurred configuring root project '${testDirectory.name}'.")
        failure.assertHasCause("An operating system needs to be specified for the ${componentUnderTestDsl}.")
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

    protected abstract String getDevelopmentBinaryCompileTask()

    protected abstract String getComponentUnderTestDsl()
}
