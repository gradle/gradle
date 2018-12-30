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

import org.gradle.nativeplatform.fixtures.RequiresInstalledToolChain
import org.gradle.nativeplatform.fixtures.ToolChainRequirement
import org.gradle.util.Matchers

import static org.gradle.util.Matchers.containsText

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

    @RequiresInstalledToolChain(ToolChainRequirement.GCC_COMPATIBLE)
    def "build succeeds when cpp source uses language features of the requested source compatibility"() {
        given:
        buildFile << """
            apply plugin: 'cpp-application'
            
            application {
                sourceCompatibility = CppSourceCompatibility.Cpp11
            }
         """

        when:
        file("src/main/cpp/cpp11.cpp") << """
            #include <iostream>
            #include <ctime>
            #include <chrono>
            int main () {
              using namespace std::chrono;
              system_clock::time_point today = system_clock::now();
              time_t tt;
              tt = system_clock::to_time_t ( today );
              std::cout << "today is: " << ctime(&tt);
              return 0;
            }
"""

        then:
        succeeds "assemble"
    }

    @RequiresInstalledToolChain(ToolChainRequirement.GCC_COMPATIBLE)
    def "build fails when cpp source uses language features outside the requested source compatibility"() {
        given:
        buildFile << """
            apply plugin: 'cpp-application'
            
            application {
                sourceCompatibility = CppSourceCompatibility.Cpp98
            }
         """

        when:
        file("src/main/cpp/cpp11.cpp") << """
            #include <iostream>
            #include <ctime>
            #include <chrono>
            int main () {
              using namespace std::chrono;
              system_clock::time_point today = system_clock::now();
              time_t tt;
              tt = system_clock::to_time_t ( today );
              std::cout << "today is: " << ctime(&tt);
              return 0;
            }
"""

        then:
        fails "assemble"
        failure.assertHasDescription("Execution failed for task ':compileDebugCpp'.")
        failure.assertHasCause("A build operation failed.")
        failure.assertThatCause(containsText("C++ compiler failed while compiling cpp11.cpp"))
    }

    @RequiresInstalledToolChain(ToolChainRequirement.GCC_COMPATIBLE)
    def "recompile when source compatibility changes"() {
        when:
        buildFile << """
            apply plugin: 'cpp-application'
            
            application {
                sourceCompatibility = CppSourceCompatibility.Cpp98
            }
         """

        and:
        file("src/main/cpp/main.cpp") << """
            int main () {
              return 0;
            }
"""

        then:
        succeeds "assemble"

        when:
        buildFile << """
            application {
                sourceCompatibility = CppSourceCompatibility.Cpp11
            }
         """

        then:
        executedAndNotSkipped(":assemble")
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
