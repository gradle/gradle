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
package org.gradle.nativebinaries.language.cpp

import org.gradle.nativebinaries.language.cpp.fixtures.AbstractInstalledToolChainIntegrationSpec
import org.gradle.nativebinaries.language.cpp.fixtures.app.CppHelloWorldApp
import org.gradle.nativebinaries.language.cpp.fixtures.app.ExeWithDiamondDependencyHelloWorldApp
import org.gradle.nativebinaries.language.cpp.fixtures.app.ExeWithLibraryUsingLibraryHelloWorldApp
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import spock.lang.Ignore
import spock.lang.Unroll

@Requires(TestPrecondition.CAN_INSTALL_EXECUTABLE)
class LibraryDependenciesIntegrationTest extends AbstractInstalledToolChainIntegrationSpec {
    def "setup"() {
        settingsFile << "rootProject.name = 'test'"
    }

    def "can use map notation to reference library in same project"() {
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
            sources.main.cpp.lib library: 'hello'
            libraries {
                hello {}
            }
        """

        when:
        succeeds "installMainExecutable"

        then:
        installation("build/install/mainExecutable").exec().out == app.englishOutput
    }

    def "can use map notation to reference static library in same project"() {
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
            sources.main.cpp.lib library: 'hello', linkage: 'static'
            libraries {
                hello {}
            }
        """

        when:
        succeeds "mainExecutable"

        then:
        executable("build/binaries/mainExecutable/main").exec().out == app.englishOutput
    }

    @Ignore("Fails due to model rules evaluating before script when project.evaluate() is called")
    @Unroll
    def "can use map notation to reference library in different project#label"() {
        given:
        def app = new CppHelloWorldApp()
        app.executable.writeSources(file("exe/src/main"))
        app.library.writeSources(file("lib/src/hello"))

        and:
        settingsFile.text = "include ':lib', ':exe'"
        buildFile << """
        project(":lib") {
            apply plugin: "cpp"
            libraries {
                hello {}
            }
        }
        project(":exe") {
            ${explicitEvaluation ? "evaluationDependsOn(':lib')" : ""}
            apply plugin: "cpp"
            executables {
                main {}
            }
            sources.main.cpp.lib project: ':lib', library: 'hello'
        }
        """

        when:
        if (configureOnDemand) {
            executer.withArgument('--configure-on-demand')
        }
        succeeds ":exe:installMainExecutable"

        then:
        installation("exe/build/install/mainExecutable").exec().out == app.englishOutput

        where:
        label                       | configureOnDemand | explicitEvaluation
        ""                          | false             | false
        " with configure-on-demand" | true              | false
        " with evaluationDependsOn" | false             | true
    }

    def "can use map notation to transitively reference libraries in different projects"() {
        given:
        def app = new ExeWithLibraryUsingLibraryHelloWorldApp()
        app.writeSources(file("exe/src/main"), file("lib/src/hello"), file("greet/src/greetings"))

        and:
        settingsFile.text = "include ':exe', ':lib', ':greet'"
        buildFile << """
        project(":exe") {
            apply plugin: "cpp"
            executables {
                main {}
            }
            sources.main.cpp.lib project: ':lib', library: 'hello'
        }
        project(":lib") {
            apply plugin: "cpp"
            libraries {
                hello {}
            }
            sources.hello.cpp.lib project: ':greet', library: 'greetings', linkage: 'static'
        }
        project(":greet") {
            apply plugin: "cpp"
            libraries {
                greetings {
                    binaries.withType(StaticLibraryBinary) {
                        if (toolChain in Gcc || toolChain in Clang) {
                            cppCompiler.args '-fPIC'
                        }
                    }
                }
            }
        }
        """

        when:
        succeeds ":exe:installMainExecutable"

        then:
        installation("exe/build/install/mainExecutable").exec().out == app.englishOutput
    }

    def "can have component graph with project dependency cycle"() {
        given:
        def app = new ExeWithLibraryUsingLibraryHelloWorldApp()
        app.writeSources(file("exe/src/main"), file("lib/src/hello"), file("exe/src/greetings"))

        and:
        settingsFile.text = "include ':exe', ':lib'"
        buildFile << """
        project(":exe") {
            apply plugin: "cpp"
            executables {
                main {}
            }
            libraries {
                greetings {
                    binaries.withType(StaticLibraryBinary) {
                        if (toolChain in Gcc || toolChain in Clang) {
                            cppCompiler.args '-fPIC'
                        }
                    }
                }
            }
            sources.main.cpp.lib project: ':lib', library: 'hello'
        }
        project(":lib") {
            apply plugin: "cpp"
            libraries {
                hello {}
            }
            sources.hello.cpp.lib project: ':exe', library: 'greetings', linkage: 'static'
        }
        """

        when:
        succeeds ":exe:installMainExecutable"

        then:
        installation("exe/build/install/mainExecutable").exec().out == app.englishOutput
    }

    def "can have component graph with diamond dependency"() {
        given:
        def app = new ExeWithDiamondDependencyHelloWorldApp()
        app.writeSources(file("src/main"), file("src/hello"), file("src/greetings"))

        and:
        buildFile << """
            apply plugin: "cpp"
            executables {
                main {}
            }
            libraries {
                hello {}
                greetings {
                    binaries.withType(StaticLibraryBinary) {
                        if (toolChain in Gcc || toolChain in Clang) {
                            cppCompiler.args '-fPIC'
                        }
                    }
                }
            }
            sources.main.cpp.lib libraries.hello.shared
            sources.main.cpp.lib libraries.greetings.static
            sources.hello.cpp.lib libraries.greetings.static
        """

        when:
        succeeds "installMainExecutable"

        then:
        installation("build/install/mainExecutable").exec().out == app.englishOutput

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
            executables {
                main {}
            }
            libraries {
                hello {}
                greetings {
                    binaries.withType(StaticLibraryBinary) {
                        if (toolChain in Gcc || toolChain in Clang) {
                            cppCompiler.args '-fPIC'
                        }
                    }
                }
            }
            sources.main.cpp.lib libraries.hello.shared
            sources.main.cpp.lib libraries.greetings.shared
            sources.hello.cpp.lib libraries.greetings.static
        """

        when:
        succeeds "installMainExecutable"

        then:
        installation("build/install/mainExecutable").exec().out == app.englishOutput

        and:
        executedAndNotSkipped ":greetingsSharedLibrary", ":greetingsStaticLibrary"
        sharedLibrary("build/binaries/greetingsSharedLibrary/greetings").assertExists()
        staticLibrary("build/binaries/greetingsStaticLibrary/greetings").assertExists()

        // TODO:DAZ Investigate this output and parse to ensure that greetings is dynamically linked into mainExe but not helloShared
        and:
        println executable("build/binaries/mainExecutable/main").binaryInfo.listLinkedLibraries()
        println sharedLibrary("build/binaries/helloSharedLibrary/hello").binaryInfo.listLinkedLibraries()
    }
}
