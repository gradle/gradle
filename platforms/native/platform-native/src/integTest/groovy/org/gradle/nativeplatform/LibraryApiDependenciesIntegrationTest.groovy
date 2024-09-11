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
package org.gradle.nativeplatform

import org.gradle.nativeplatform.fixtures.AbstractInstalledToolChainIntegrationSpec
import org.gradle.nativeplatform.fixtures.app.CppHelloWorldApp
import org.gradle.nativeplatform.fixtures.app.ExeWithLibraryUsingLibraryHelloWorldApp
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions

@Requires(UnitTestPreconditions.CanInstallExecutable)
class LibraryApiDependenciesIntegrationTest extends AbstractInstalledToolChainIntegrationSpec {
    def "setup"() {
        settingsFile << "rootProject.name = 'test'"
        buildFile << """
apply plugin: "cpp"

model {
    // Allow static libraries to be linked into shared
    binaries {
        withType(StaticLibraryBinarySpec) {
            if (toolChain in Gcc || toolChain in Clang) {
                cppCompiler.args '-fPIC'
            }
        }
    }
}
"""
    }

    def "can use api linkage via #notationName notation"() {
        given:
        def app = new CppHelloWorldApp()
        app.executable.writeSources(file("src/main"))

        app.library.headerFiles*.writeToDir(file("src/helloApi"))
        app.library.sourceFiles*.writeToDir(file("src/hello"))

        and:
        buildFile << """
model {
    components { comp ->
        helloApi(NativeLibrarySpec)
        hello(NativeLibrarySpec) {
            sources {
                cpp.lib ${notation}
            }
        }
        main(NativeExecutableSpec) {
            sources {
                cpp.lib ${notation}
                cpp.lib library: 'hello'
            }
        }
    }
}
"""

        when:
        succeeds "installMainExecutable"

        then:
        installation("build/install/main").exec().out == app.englishOutput

        where:
        notationName | notation
        "direct"     | "\$('components.helloApi').api"
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
model {
    components {
        main(NativeExecutableSpec) {
            sources {
                cpp.lib library: 'util'
            }
        }
        util(NativeLibrarySpec)
    }
}
"""
        when:
        succeeds "installMainExecutable"

        then:
        installation("build/install/main").exec().out == "Hello from the utility library"
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
model {
    buildTypes {
        debug
        release
    }
    components {
        main(NativeExecutableSpec) {
            sources {
                cpp.lib library: 'util'
            }
        }
        util(NativeLibrarySpec) {
            binaries.all { binary ->
                sources {
                    buildTypeSources(CppSourceSet) {
                        exportedHeaders.srcDir "src/util/\${binary.buildType.name}"
                    }
                }
            }
        }
    }
}
"""
        when:
        succeeds "installMainDebugExecutable", "installMainReleaseExecutable"

        then:
        installation("build/install/main/debug").exec().out == "Hello from the debug library"
        installation("build/install/main/release").exec().out == "Hello from the release library"
    }

    def "can choose alternative library implementation of api"() {
        given:
        def app = new CppHelloWorldApp()
        app.executable.writeSources(file("src/main"))
        app.library.writeSources(file("src/hello"))

        app.alternateLibrarySources*.writeToDir(file("src/hello2"))

        and:
        buildFile << """
model {
    components {
        main(NativeExecutableSpec) {
            sources {
                cpp.lib library: 'hello', linkage: 'api'
                cpp.lib library: 'hello2'
            }
        }
        hello(NativeLibrarySpec)
        hello2(NativeLibrarySpec) {
            sources {
                cpp.lib library: 'hello', linkage: 'api'
            }
        }
    }
}
        """

        when:
        succeeds "installMainExecutable"

        then:
        installation("build/install/main").exec().out == app.alternateLibraryOutput
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
model {
    components {
        main(NativeExecutableSpec) {
            sources {
                cpp.lib library: 'hello'
            }
        }
        hello(NativeLibrarySpec) {
            sources {
                cpp.lib library: 'greetings', linkage: 'static'
            }
        }
        greetings(NativeLibrarySpec) {
            sources {
                cpp.lib library: 'hello', linkage: 'api'
            }
        }
    }
}
        """

        when:
        succeeds "installMainExecutable"

        then:
        installation("build/install/main").exec().out == app.englishOutput
    }

    def "can compile but not link when executable depends on api of library required for linking"() {
        given:
        def app = new CppHelloWorldApp()
        app.executable.writeSources(file("src/main"))
        app.library.writeSources(file("src/hello"))

        and:
        buildFile << """
model {
    components {
        main(NativeExecutableSpec) {
            sources {
                cpp.lib library: 'hello', linkage: 'api'
            }
        }
        hello(NativeLibrarySpec)
    }
}
        """

        when:
        fails "mainExecutable"

        then:
        failure.assertHasDescription("Execution failed for task ':linkMainExecutable'.")
    }
}
