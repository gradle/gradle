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
import org.gradle.nativebinaries.language.cpp.fixtures.app.CppHelloWorldApp
import org.gradle.nativebinaries.language.cpp.fixtures.app.ExeWithLibraryUsingLibraryHelloWorldApp
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import spock.lang.Unroll

@Requires(TestPrecondition.CAN_INSTALL_EXECUTABLE)
class LibraryApiDependenciesIntegrationTest extends AbstractInstalledToolChainIntegrationSpec {
    @Unroll
    def "can use api linkage via #notationName notation"() {
        given:
        def app = new CppHelloWorldApp()
        app.executable.writeSources(file("src/main"))

        app.library.headerFiles*.writeToDir(file("src/helloApi"))
        app.library.sourceFiles*.writeToDir(file("src/hello"))

        and:
        buildFile << """
            apply plugin: "cpp"
            executables {
                main {}
            }
            libraries {
                helloApi {}
                hello {}
            }
            sources.main.cpp.lib ${notation}
            sources.main.cpp.lib library: 'hello'
            sources.hello.cpp.lib ${notation}
        """

        when:
        succeeds "installMainExecutable"

        then:
        installation("build/install/mainExecutable").exec().out == app.englishOutput

        where:
        notationName | notation
        "direct"     | "libraries.helloApi.api"
        "map"        | "library: 'helloApi', linkage: 'api'"
    }

    def "executable compiles using functions defined in header-only utility library"() {
        given:
        file("src/util/headers/util.h") << """
            const char *message = "Hello from the utility library";
"""
        file("src/main/cpp/main.cpp") << """
            #include "util.h"
            #include <iostream>

            int main () {
                std::cout << message;
                return 0;
            }
"""
        buildFile << """
            apply plugin: "cpp"
            executables {
                main {}
            }
            libraries {
                util {}
            }
            sources.main.cpp.lib library: 'util'
"""
        when:
        succeeds "installMainExecutable"

        then:
        installation("build/install/mainExecutable").exec().out == "Hello from the utility library"
    }

    def "executable compiles using functions defined in utility library with build type variants"() {
        given:
        file("src/util/debug/util.h") << """
            const char *message = "Hello from the debug library";
"""
        file("src/util/release/util.h") << """
            const char *message = "Hello from the release library";
"""
        file("src/main/cpp/main.cpp") << """
            #include "util.h"
            #include <iostream>

            int main () {
                std::cout << message;
                return 0;
            }
"""
        buildFile << """
            apply plugin: "cpp"
            model {
                buildTypes {
                    create("debug")
                    create("release")
                }
            }
            executables {
                main {}
            }
            libraries {
                util {
                    binaries.all { binary ->
                        binary.source sources[binary.buildType.name]
                    }
                }
            }
            sources {
                main.cpp.lib library: 'util'
                debug.cpp {
                    exportedHeaders.srcDir "src/util/debug"
                }
                release.cpp {
                    exportedHeaders.srcDir "src/util/release"
                }
            }
"""
        when:
        succeeds "installDebugMainExecutable", "installReleaseMainExecutable"

        then:
        installation("build/install/mainExecutable/debug").exec().out == "Hello from the debug library"
        installation("build/install/mainExecutable/release").exec().out == "Hello from the release library"
    }

    def "can choose alternative library implementation of api"() {
        given:
        def app = new CppHelloWorldApp()
        app.executable.writeSources(file("src/main"))
        app.library.writeSources(file("src/hello"))

        app.alternateLibrarySources*.writeToDir(file("src/hello2"))

        and:
        buildFile << """
            apply plugin: "cpp"
            executables {
                main {}
            }
            libraries {
                hello {}
                hello2 {}
            }
            sources.main.cpp.lib library: 'hello', linkage: 'api'
            sources.main.cpp.lib library: 'hello2'
            sources.hello2.cpp.lib library: 'hello', linkage: 'api'
        """

        when:
        succeeds "installMainExecutable"

        then:
        installation("build/install/mainExecutable").exec().out == app.alternateLibraryOutput
    }

    def "can use api linkage for component graph with library dependency cycle"() {
        given:
        def app = new ExeWithLibraryUsingLibraryHelloWorldApp()
        app.executable.writeSources(file("src/main"))
        app.library.writeSources(file("src/hello"))
        app.greetingsHeader.writeToDir(file("src/hello"))
        app.greetingsSources*.writeToDir(file("src/greetings"))

        and:
        buildFile << """
            apply plugin: "cpp"
            executables {
                main {}
            }
            libraries {
                hello {}
                greetings {}
            }
            sources.main.cpp.lib library: 'hello'
            sources.hello.cpp.lib library: 'greetings', linkage: 'static'
            sources.greetings.cpp.lib library: 'hello', linkage: 'api'
        """

        when:
        succeeds "installMainExecutable"

        then:
        installation("build/install/mainExecutable").exec().out == app.englishOutput
    }

    def "can compile but not link when executable depends on api of library required for linking"() {
        given:
        def app = new CppHelloWorldApp()
        app.executable.writeSources(file("src/main"))
        app.library.writeSources(file("src/hello"))

        and:
        buildFile << """
            apply plugin: "cpp"
            executables {
                main {}
            }
            libraries {
                hello {}
            }
            sources.main.cpp.lib library: 'hello', linkage: 'api'
        """

        when:
        fails "mainExecutable"

        then:
        failure.assertHasDescription("Execution failed for task ':linkMainExecutable'.")
    }
}
