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
import org.gradle.nativebinaries.language.cpp.fixtures.app.ExeWithLibraryUsingLibraryHelloWorldApp
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition

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

    def "can use map notation to reference library in different project"() {
        given:
        def app = new CppHelloWorldApp()
        app.executable.writeSources(file("exe/src/main"))
        app.library.writeSources(file("lib/src/hello"))

        and:
        settingsFile.text = "include ':exe', ':lib'"
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
                        if (toolChain in Gcc) {
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
}
