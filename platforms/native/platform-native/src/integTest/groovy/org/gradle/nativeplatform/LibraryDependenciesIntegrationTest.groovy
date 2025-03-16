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
import org.gradle.nativeplatform.fixtures.app.ExeWithDiamondDependencyHelloWorldApp
import org.gradle.nativeplatform.fixtures.app.ExeWithLibraryUsingLibraryHelloWorldApp
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions

@Requires(UnitTestPreconditions.CanInstallExecutable)
class LibraryDependenciesIntegrationTest extends AbstractInstalledToolChainIntegrationSpec {
    def "setup"() {
        settingsFile << "rootProject.name = 'test'"
        buildFile << """
            allprojects {
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
            }
"""
    }

    def "produces reasonable error message when referenced library #label"() {
        given:
        def app = new CppHelloWorldApp()
        app.executable.writeSources(file("exe/src/main"))
        app.library.writeSources(file("lib/src/hello"))

        and:
        createDirs("exe", "other")
        settingsFile.text = "include ':exe', ':other'"
        buildFile << """
project(":exe") {
    model {
        components {
            main(NativeExecutableSpec) {
                sources {
                    cpp.lib ${dependencyNotation}
                }
            }
            hello(NativeLibrarySpec)
        }
    }
}
project(":other") {
    model {
        components {
            hello(NativeLibrarySpec)
        }
    }
}
"""

        when:
        fails ":exe:mainExecutable"

        then:
        failure.assertHasDescription("Could not determine the dependencies of task ':exe:linkMainExecutable'.")
        failure.assertHasCause(description)

        where:
        label                                  | dependencyNotation                      | description
        "does not exist"                       | "library: 'unknown'"                    | "Could not locate library 'unknown' required by 'main' in project ':exe'."
        "project that does not exist"          | "project: ':unknown', library: 'hello'" | "Project with path ':unknown' not found."
        "does not exist in referenced project" | "project: ':other', library: 'unknown'" | "Could not locate library 'unknown' in project ':other' required by 'main' in project ':exe'."
    }

    def "can use #notationName notation to reference library in same project"() {
        given:
        def app = new CppHelloWorldApp()
        app.executable.writeSources(file("src/main"))
        app.library.writeSources(file("src/hello"))

        and:
        buildFile << """
model {
    components { comp ->
        hello(NativeLibrarySpec)
        main(NativeExecutableSpec) {
            sources {
                cpp.lib ${notation}
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
        "direct"     | "\$.components.hello"
        "map"        | "library: 'hello'"
    }

    def "can use map #notationName notation to reference library dependency of binary"() {
        given:
        def app = new CppHelloWorldApp()
        app.executable.writeSources(file("src/main"))
        app.library.writeSources(file("src/hello"))

        and:
        buildFile << """
model {
    components {
        hello(NativeLibrarySpec)
        main(NativeExecutableSpec) {
            binaries.all { binary ->
                binary.lib ${notation}
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
        "direct"     | "\$.components.hello"
        "map"        | "library: 'hello'"
    }

    def "can use map notation to reference static library in same project"() {
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
                cpp.lib library: 'hello', linkage: 'static'
            }
        }
        hello(NativeLibrarySpec)
    }
}
"""

        when:
        succeeds "mainExecutable"

        then:
        executable("build/exe/main/main").exec().out == app.englishOutput
    }

    def "can use map notation to reference library in different project"() {
        given:
        def app = new CppHelloWorldApp()
        app.executable.writeSources(file("exe/src/main"))
        app.library.writeSources(file("lib/src/hello"))

        and:
        createDirs("lib", "exe")
        settingsFile.text = "include ':lib', ':exe'"
        buildFile << """
project(":exe") {
    model {
        components {
            main(NativeExecutableSpec) {
                sources {
                    cpp.lib project: ':lib', library: 'hello'
                }
            }
        }
    }
}
project(":lib") {
    model {
        components {
            hello(NativeLibrarySpec)
        }
    }
}
"""

        when:
        succeeds ":exe:installMainExecutable"

        then:
        installation("exe/build/install/main").exec().out == app.englishOutput
    }

    def "can use map notation to reference library in different project with configure-on-demand"() {
        given:
        def app = new CppHelloWorldApp()
        app.executable.writeSources(file("exe/src/main"))
        app.library.writeSources(file("lib/src/hello"))

        and:
        createDirs("lib", "exe")
        settingsFile.text = "include ':lib', ':exe'"
        buildFile << """
project(":exe") {
    model {
        components {
            main(NativeExecutableSpec) {
                sources {
                    cpp.lib project: ':lib', library: 'hello'
                }
            }
        }
    }
}
project(":lib") {
    model {
        components {
            hello(NativeLibrarySpec)
        }
    }
}
"""

        when:
        executer.withArgument('--configure-on-demand')
        succeeds ":exe:installMainExecutable"

        then:
        installation("exe/build/install/main").exec().out == app.englishOutput
    }

    def "can use map notation to transitively reference libraries in different projects"() {
        given:
        def app = new ExeWithLibraryUsingLibraryHelloWorldApp()
        app.writeSources(file("exe/src/main"), file("lib/src/hello"), file("greet/src/greetings"))

        and:
        createDirs("exe", "lib", "greet")
        settingsFile.text = "include ':exe', ':lib', ':greet'"
        buildFile << """
project(":exe") {
    model {
        components {
            main(NativeExecutableSpec) {
                sources {
                    cpp.lib project: ':lib', library: 'hello'
                }
            }
        }
    }
}
project(":lib") {
    model {
        components {
            hello(NativeLibrarySpec) {
                sources {
                    cpp.lib project: ':greet', library: 'greetings', linkage: 'static'
                }
            }
        }
    }
}
project(":greet") {
    model {
        components {
            greetings(NativeLibrarySpec)
        }
    }
}
"""

        when:
        succeeds ":exe:installMainExecutable"

        then:
        installation("exe/build/install/main").exec().out == app.englishOutput
    }

    def "can have component graph with project dependency cycle"() {
        given:
        def app = new ExeWithLibraryUsingLibraryHelloWorldApp()
        app.writeSources(file("exe/src/main"), file("lib/src/hello"), file("exe/src/greetings"))

        and:
        createDirs("exe", "lib")
        settingsFile.text = "include ':exe', ':lib'"
        buildFile << """
project(":exe") {
    apply plugin: "cpp"
    model {
        components {
            main(NativeExecutableSpec) {
                sources {
                    cpp.lib project: ':lib', library: 'hello'
                }
            }
            greetings(NativeLibrarySpec)
        }
    }
}
project(":lib") {
    apply plugin: "cpp"
    model {
        components {
            hello(NativeLibrarySpec) {
                sources {
                    cpp.lib project: ':exe', library: 'greetings', linkage: 'static'
                }
            }
        }
    }
}
"""

        when:
        succeeds ":exe:installMainExecutable"

        then:
        installation("exe/build/install/main").exec().out == app.englishOutput
    }

    def "can have component graph with diamond dependency"() {
        given:
        def app = new ExeWithDiamondDependencyHelloWorldApp()
        app.writeSources(file("src/main"), file("src/hello"), file("src/greetings"))

        and:
        buildFile << """
apply plugin: "cpp"
model {
    components {
        main(NativeExecutableSpec) {
            sources {
                cpp.lib library: "hello"
                cpp.lib library: "greetings", linkage: "static"
            }
        }
        hello(NativeLibrarySpec) {
            sources {
                cpp.lib library: "greetings", linkage: "static"
            }
        }
        greetings(NativeLibrarySpec)
    }
}
"""

        when:
        succeeds "installMainExecutable"

        then:
        installation("build/install/main").exec().out == app.englishOutput

        and:
        notExecuted ":greetingsSharedLibrary"
        sharedLibrary("build/binaries/greetingsSharedLibrary/greetings").assertDoesNotExist()
    }

    def "can have component graph with both static and shared variants of same library"() {
        given:
        def app = new ExeWithDiamondDependencyHelloWorldApp()
        app.writeSources(file("src/main"), file("src/hello"), file("src/greetings"))

        and:
        buildFile << """
apply plugin: "cpp"
model {
    components {
        main(NativeExecutableSpec) {
            sources {
                cpp.lib library: "hello", linkage: "shared"
                cpp.lib library: "greetings", linkage: "shared"
            }
        }
        hello(NativeLibrarySpec) {
            sources {
                cpp.lib library: "greetings", linkage: "static"
            }
        }
        greetings(NativeLibrarySpec)
    }
}
"""

        when:
        succeeds "installMainExecutable"

        then:
        installation("build/install/main").exec().out == app.englishOutput

        and:
        executedAndNotSkipped ":greetingsSharedLibrary", ":greetingsStaticLibrary"
        sharedLibrary("build/libs/greetings/shared/greetings").assertExists()
        staticLibrary("build/libs/greetings/static/greetings").assertExists()

        and:
        try {
            println executable("build/exe/main/main").binaryInfo.listLinkedLibraries()
            println sharedLibrary("build/libs/hello/shared/hello").binaryInfo.listLinkedLibraries()
        } catch (UnsupportedOperationException ignored) {
            // Toolchain doesn't support it.
        }
    }
}
