/*
 * Copyright 2012 the original author or authors.
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
package org.gradle.nativeplatform

import org.gradle.nativeplatform.fixtures.AbstractInstalledToolChainIntegrationSpec
import org.gradle.nativeplatform.fixtures.app.CppHelloWorldApp
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions
import spock.lang.Issue

@Requires(UnitTestPreconditions.CanInstallExecutable)
class LibraryBinariesIntegrationTest extends AbstractInstalledToolChainIntegrationSpec {
    def "setup"() {
        settingsFile << "rootProject.name = 'test'"
    }

    def "executable can use a mix of static and shared libraries"() {
        given:
        buildFile << """
apply plugin: "cpp"
model {
    components {
        helloStatic(NativeLibrarySpec)
        helloShared(NativeLibrarySpec)
        main(NativeExecutableSpec) {
            sources {
                cpp.lib library: 'helloStatic', linkage: 'static'
                cpp.lib library: 'helloShared', linkage: 'shared'
            }
        }
    }
}
"""

        and:
        file("src/helloStatic/cpp/hellostatic.cpp") << """
            #include <iostream>

            void helloStatic() {
                std::cout << "Hello static";
            }
        """

        and:
        file("src/helloStatic/headers/hellostatic.h") << """
            void helloStatic();
        """

        and:
        file("src/helloShared/cpp/helloshared.cpp") << """
            #include <iostream>
            #include "helloshared.h"

            void DLL_FUNC helloShared() {
                std::cout << "Hello shared";
            }
        """

        and:
        file("src/helloShared/headers/helloshared.h") << """
            #ifdef _WIN32
            #define DLL_FUNC __declspec(dllexport)
            #else
            #define DLL_FUNC
            #endif

            void DLL_FUNC helloShared();
        """

        and:
        file("src/main/cpp/main.cpp") << """
            #include "hellostatic.h"
            #include "helloshared.h"

            int main () {
                helloStatic();
                helloShared();
                return 0;
            }
        """

        when:
        succeeds "installMainExecutable"

        then:
        staticLibrary("build/libs/helloStatic/static/helloStatic").assertExistsAndDelete()
        sharedLibrary("build/libs/helloShared/shared/helloShared").assertExistsAndDelete()
        installation("build/install/main")
            .assertIncludesLibraries("helloShared")
            .exec().out == "Hello staticHello shared"
    }

    def "executable can use a combination of libraries from the same and other projects"() {
        given:
        settingsFile << """
include 'exe', 'lib'
"""
        buildFile << """
project('lib') {
    apply plugin: "cpp"
    model {
        components {
            helloLib(NativeLibrarySpec)
        }
    }
}
project('exe') {
    apply plugin: "cpp"
    model {
        components {
            main(NativeExecutableSpec) {
                sources {
                    cpp {
                        lib library: "helloMain"
                        lib project: ":lib", library: "helloLib"
                    }
                }
            }
            helloMain(NativeLibrarySpec)
        }
    }
}
"""

        and:
        file("lib/src/helloLib/cpp/hellolib.cpp") << """
            #include <iostream>
            #include "hellolib.h"

            void DLL_FUNC helloLib() {
                std::cout << "Hello lib";
            }
        """

        and:
        file("lib/src/helloLib/headers/hellolib.h") << """
            #ifdef _WIN32
            #define DLL_FUNC __declspec(dllexport)
            #else
            #define DLL_FUNC
            #endif

            void DLL_FUNC helloLib();
        """

        and:
        file("exe/src/helloMain/cpp/hellomain.cpp") << """
            #include <iostream>
            #include "hellomain.h"

            void DLL_FUNC helloMain() {
                std::cout << "Hello main" << std::endl;
            }
        """

        and:
        file("exe/src/helloMain/headers/hellomain.h") << """
            #ifdef _WIN32
            #define DLL_FUNC __declspec(dllexport)
            #else
            #define DLL_FUNC
            #endif

            void DLL_FUNC helloMain();
        """

        and:
        file("exe/src/main/cpp/main.cpp") << """
            #include "hellolib.h"
            #include "hellomain.h"

            int main () {
                helloMain();
                helloLib();
                return 0;
            }
        """

        when:
        succeeds "exe:installMainExecutable"

        then:
        sharedLibrary("lib/build/libs/helloLib/shared/helloLib").assertExistsAndDelete()
        sharedLibrary("exe/build/libs/helloMain/shared/helloMain").assertExistsAndDelete()
        installation("exe/build/install/main")
            .assertIncludesLibraries("helloLib", "helloMain")
            .exec().out == "Hello main\nHello lib"
    }

    def "source set library dependencies are not shared with other source sets"() {
        given:
        buildFile << """
apply plugin: "cpp"
apply plugin: "c"

model {
    components {
        libCpp(NativeLibrarySpec)
        libC(NativeLibrarySpec)
        main(NativeExecutableSpec) {
            sources {
                cpp.lib library: 'libCpp', linkage: 'static'
                c.lib library: 'libC', linkage: 'static'
            }
        }
    }
}
"""

        and:
        file("src/main/headers/head.h") << """
            void cppOut();

            extern "C" {
                void cOut();
            }
"""

        file("src/main/cpp/main.cpp") << """
            #include "head.h"

            int main () {
                cppOut();
                cOut();
                return 0;
            }
"""
        and: "C and CPP sources sets depend on header file with same name"

        file("src/main/cpp/test.cpp") << """
            #include <iostream>
            #include "output.h"

            void cppOut() {
                std::cout << OUTPUT << "_";
            }
"""

        file("src/main/c/test.c") << """
            #include <stdio.h>
            #include "output.h"

            void cOut() {
                printf(OUTPUT);
            }
"""

        and: "Library header files define different OUTPUT values"

        file("src/libCpp/headers/output.h") << """
            #define OUTPUT "CPP"
"""

        file("src/libC/headers/output.h") << """
            #define OUTPUT "C"
"""

        when:
        succeeds "installMainExecutable"

        then:
        installation("build/install/main").exec().out == "CPP_C"
    }

    @Issue("GRADLE-2925")
    def "headers for source set added to library binary are available to consuming binary"() {
        def app = new CppHelloWorldApp()
        given:
        buildFile << """
apply plugin: "cpp"

model {
    components {
        main(NativeExecutableSpec) {
            sources {
                cpp.lib library: "hello"
            }
        }
        hello(NativeLibrarySpec) {
            binaries.all {
                sources {
                    helloLib(CppSourceSet) {
                        source.srcDir "src/helloLib/cpp"
                        exportedHeaders.srcDir "src/helloLib/headers"
                    }
                }
            }
        }
    }
}
"""

        and:
        app.executable.writeSources(file("src/main"))
        app.library.writeSources(file("src/helloLib"))

        when:
        succeeds "installMainExecutable"

        then:
        installation("build/install/main").exec().out == app.englishOutput
    }
}
