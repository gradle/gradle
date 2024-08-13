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
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions

@Requires([
    UnitTestPreconditions.CanInstallExecutable,
    UnitTestPreconditions.NotMacOs
])
class SourceSetLinkDependenciesIntegrationTest extends AbstractInstalledToolChainIntegrationSpec {
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
            int getOne();

            int func1() {
                return getOne();
            }
"""
        file("src/other/cpp/func2.cpp") << """
            int getTwo();

            int func2() {
                return getTwo();
            }
"""
        file("src/lib1/cpp/getters.cpp") << """
            int getOne() {
                return 1;
            }
            int getTwo() {
                return 2;
            }
"""

        and:
        buildFile << """
apply plugin: "cpp"
model {
    components {
        lib1(NativeLibrarySpec)
        main(NativeExecutableSpec) {
            sources {
                cpp1(CppSourceSet)
            }
        }
    }
}
"""
    }

    def "library dependency of binary is available when linking all source sets"() {
        given:
        buildFile << """
model {
    components {
        main {
            sources {
                other(CppSourceSet) {
                    source.srcDir "src/other/cpp"
                }
            }
            binaries.all {
                lib library: 'lib1', linkage: 'static'
            }
        }
    }
}
"""
        when:
        succeeds "installMainExecutable"

        then:
        installation("build/install/main").exec().out == "12\n"
    }

    def "library dependency of 1 language source set is available to another when linking"() {
        given:
        buildFile << """
model {
    components {
        main {
            sources {
                other(CppSourceSet) {
                    source.srcDir "src/other/cpp"
                    lib library: 'lib1', linkage: 'static'
                }
            }
        }
    }
}
"""

        when:
        succeeds "installMainExecutable"

        then:
        installation("build/install/main").exec().out == "12\n"
    }

    def "dependencies of language source set added to binary are available when linking"() {
        given:
        buildFile << """
model {
    components {
        main {
            binaries.all {
                sources {
                    other(CppSourceSet) {
                        source.srcDir "src/other/cpp"
                        lib library: 'lib1', linkage: 'static'
                    }
                }
            }
        }
    }
}
"""

        when:
        succeeds "installMainExecutable"

        then:
        installation("build/install/main").exec().out == "12\n"
    }
}
