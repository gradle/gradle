/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.nativebinaries.language.cpp

import org.gradle.nativebinaries.language.cpp.fixtures.AbstractInstalledToolChainIntegrationSpec

class SourceSetCompileDependenciesIntegrationTest extends AbstractInstalledToolChainIntegrationSpec {
    def "setup"() {
        settingsFile << "rootProject.name = 'test'"

        file("src/main/headers/funcs.h") << """
            int func1();
            int func2();
"""
        file("src/main/cpp/main.cpp") << """
            #include <iostream>
            #include "funcs.h"
            int main () {
                std::cout << func1() << func2() << std::endl;
                return 0;
            }
"""
        file("src/main/cpp1/func1.cpp") << """
            #include "one.h"

            int func1() {
                return ONE;
            }
"""
        file("src/other/cpp/func2.cpp") << """
            #include "two.h"

            int func2() {
                return TWO;
            }
"""
        file("src/lib1/headers/one.h") << """
            #define ONE 1
"""
        file("src/lib1/headers/two.h") << """
            #define TWO 2
"""

        and:
        buildFile << """
            apply plugin: "cpp"
            libraries {
                lib1
            }
            sources {
                main {
                    cpp(CppSourceSet)
                    cpp1(CppSourceSet)
                }
                other {
                    cpp(CppSourceSet)
                }
            }
            executables {
                main {
                    source sources.main
                }
            }
"""
    }

    def "dependencies of 2 language source sets are not shared when compiling"() {
        given:
        buildFile << """
            executables {
                main {
                    source sources.other
                }
            }
            sources.other.cpp.lib library: 'lib1', linkage: 'api'
"""

        when:
        fails "mainExecutable"

        then:
        failure.assertHasCause "C++ compiler failed"

        when:
        // Supply the library directly to the cpp1 source set
        buildFile << """
            sources.main.cpp1.lib library: 'lib1', linkage: 'api'
"""
        then:
        succeeds "mainExecutable"
    }

    def "dependencies of language source set added to binary are not shared when compiling"() {
        given:
        buildFile << """
            executables {
                main {
                    binaries.all {
                        source sources.other
                    }
                }
            }
            sources.other.cpp.lib library: 'lib1', linkage: 'api'
"""

        when:
        fails "mainExecutable"

        then:
        failure.assertHasCause "C++ compiler failed"

        when:
        // Supply the library directly to the cpp1 source set
        buildFile << """
            sources.main.cpp1.lib library: 'lib1', linkage: 'api'
"""
        then:
        succeeds "mainExecutable"
    }

    def "dependencies of binary are shared with all source sets when compiling"() {
        given:
        buildFile << """
            executables {
                main {
                    source sources.other
                    binaries.all {
                        lib library: 'lib1', linkage: 'api'
                    }
                }
            }
"""

        expect:
        succeeds "mainExecutable"
    }
}
