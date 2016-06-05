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
package org.gradle.nativeplatform.sourceset

import org.gradle.nativeplatform.fixtures.AbstractInstalledToolChainIntegrationSpec

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
        file("src/main/cpp/func1.cpp") << """
            #include "lib.h"

            int func1() {
                return LIB_ID;
            }
"""
        file("src/main/otherCpp/func2.cpp") << """
            #include "lib.h"

            int func2() {
                return LIB_ID;
            }
"""
        file("src/lib1/headers/lib.h") << """
            #define LIB_ID 1
"""
        file("src/lib2/headers/lib.h") << """
            #define LIB_ID 2
"""

        and:
        buildFile << """
apply plugin: "cpp"
model {
    components {
        lib1(NativeLibrarySpec)
        lib2(NativeLibrarySpec)
    }
}
"""
    }

    def "dependencies of 2 language source sets are not shared when compiling"() {
        given:
        buildFile << """
model {
    components {
        main(NativeExecutableSpec) {
            sources {
                cpp {
                    lib library: 'lib1', linkage: 'api'
                }
                otherCpp(CppSourceSet) {
                    lib library: 'lib2', linkage: 'api'
                }
            }
        }
    }
}
"""

        when:
        succeeds "mainExecutable"

        then:
        executable("build/exe/main/main").exec().out == "12\n"
    }

    def "dependencies of language source set added to binary are not shared when compiling"() {
        given:
        buildFile << """
model {
    components {
        main(NativeExecutableSpec) {
            sources {
                cpp.lib library: 'lib1', linkage: 'api'
            }
            binaries.all {
                sources {
                    other(CppSourceSet) {
                        source.srcDir "src/main/otherCpp"
                        lib library: 'lib2', linkage: 'api'
                    }
                }
            }
        }
    }
}
"""

        when:
        succeeds "mainExecutable"

        then:
        executable("build/exe/main/main").exec().out == "12\n"
    }

    def "dependencies of binary are shared with all source sets when compiling"() {
        given:
        buildFile << """
model {
    components {
        main(NativeExecutableSpec) {
            sources {
                otherCpp(CppSourceSet)
            }
            binaries.all {
                lib library: 'lib1', linkage: 'api'
            }
        }
    }
}
"""

        when:
        succeeds "mainExecutable"

        then:
        executable("build/exe/main/main").exec().out == "11\n"
    }
}
