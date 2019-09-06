/*
 * Copyright 2019 the original author or authors.
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


import org.gradle.language.nativeplatform.internal.repo.HomebrewBinaryRepository
import org.gradle.nativeplatform.fixtures.AbstractInstalledToolChainIntegrationSpec

class CppHomebrewPrebuiltBinariesIntegrationTest extends AbstractInstalledToolChainIntegrationSpec {
    def "can compile library against homebrew library"() {
        given:
        settingsFile << """
            rootProject.name = "some-thing"
        """
        buildFile << """
            plugins {
                id 'cpp-application'
            } 
            repositories {
                // Would add a factory method of some kind instead of instantiating internal class
                // Base directory would also be a property and have some default value
                add(objects.newInstance(${HomebrewBinaryRepository.name}, file('homebrew')))
            }
            application {
                dependencies {
                    // Might also add a factory method for homebrew libraries (or for libraries that have a name but no group)
                    implementation(":somelib:1.2")
                }
            }
        """

        // Use a homebrew mock up
        file("homebrew/somelib/1.2/include/somelib/common.h") << """
            #define SOME_LIB_VERSION "1.2"
        """

        file("src/main/cpp/main.cpp") << """
        #include <iostream>
        #include <somelib/common.h>
        
        int main() {
            std::cout << SOME_LIB_VERSION << std::endl;
            return 0;
        }
        """

        when:
        run("installDebug")

        then:
        executable("build/exe/main/debug/some-thing").exec().out == "1.2\n"
    }
}
