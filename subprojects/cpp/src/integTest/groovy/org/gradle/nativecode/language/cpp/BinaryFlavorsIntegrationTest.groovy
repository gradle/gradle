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
package org.gradle.nativecode.language.cpp
import org.gradle.nativecode.language.cpp.fixtures.AbstractBinariesIntegrationSpec
import org.gradle.nativecode.language.cpp.fixtures.app.CppHelloWorldApp

class BinaryFlavorsIntegrationTest extends AbstractBinariesIntegrationSpec {
    def helloWorldApp = new CppHelloWorldApp()

    def "setup"() {
        settingsFile << "rootProject.name = 'test'"
    }

     def "build multiple flavors of executable binary"() {
        given:
        helloWorldApp.sourceFiles.each { sourceFile ->
            write("main", sourceFile)
        }

        and:
        buildFile << """
            apply plugin: "cpp"
            sources {
                main {}
            }
            executables {
                main {
                    source sources.main
                    flavors {
                        french {}
                    }
                    binaries.all {
                        if (flavor == flavors.french) {
                            define "FRENCH"
                        }
                    }
                }
            }
        """

        when:
        succeeds "mainExecutable"

        then:
        executable("build/binaries/mainExecutable/main").exec().out == helloWorldApp.englishOutput

        when:
        succeeds "frenchMainExecutable"

        then:
        executable("build/binaries/frenchMainExecutable/main").exec().out == helloWorldApp.frenchOutput
    }

    def "build multiple flavors of shared library binary and link into executable with same flavor"() {
        given:
        write("main", helloWorldApp.mainSource)
        write("hello", helloWorldApp.libraryHeader)
        helloWorldApp.librarySources.each {
            write("hello", it)
        }

        and:
        buildFile << """
            apply plugin: "cpp"
            sources {
                main {}
                hello {}
            }
            libraries {
                hello {
                    source sources.hello
                    flavors {
                        french {}
                    }
                    binaries.all {
                        if (flavor == flavors.french) {
                            define "FRENCH"
                        }
                    }
                }
            }
            executables {
                main {
                    source sources.main
                    flavors {
                        french {}
                    }
                    binaries.all {
                        lib libraries.hello
                    }
                }
            }
        """

        when:
        succeeds "installMainExecutable"

        then:
        def install = installation("build/install/mainExecutable")
        install.assertInstalled()
        install.exec().out == helloWorldApp.englishOutput

        when:
        succeeds "installFrenchMainExecutable"

        then:
        def frenchInstall = installation("build/install/frenchMainExecutable")
        frenchInstall.assertInstalled()
        frenchInstall.exec().out == helloWorldApp.frenchOutput
    }

    def "build multiple flavors of static library binary and link into executable with same flavor"() {
        given:
        write("main", helloWorldApp.mainSource)
        write("hello", helloWorldApp.libraryHeader)
        helloWorldApp.librarySources.each {
            write("hello", it)
        }

        and:
        buildFile << """
            apply plugin: "cpp"
            sources {
                main {}
                hello {}
            }
            libraries {
                hello {
                    source sources.hello
                    flavors {
                        french {}
                    }
                    binaries.all {
                        if (flavor == flavors.french) {
                            define "FRENCH"
                        }
                    }
                }
            }
            executables {
                main {
                    source sources.main
                    flavors {
                        french {}
                    }
                    binaries.all {
                        lib libraries.hello.static
                    }
                }
            }
        """

        when:
        succeeds "mainExecutable"

        then:
        executable("build/binaries/mainExecutable/main").exec().out == helloWorldApp.englishOutput

        when:
        succeeds "frenchMainExecutable"

        then:
        executable("build/binaries/frenchMainExecutable/main").exec().out == helloWorldApp.frenchOutput
    }

    def "build fails when library has no matching flavour"() {
        given:
        write("main", helloWorldApp.mainSource)
        write("hello", helloWorldApp.libraryHeader)
        helloWorldApp.librarySources.each {
            write("hello", it)
        }

        when:
        buildFile << """
            apply plugin: "cpp"
            sources {
                main {}
                hello {}
            }
            libraries {
                hello {
                    source sources.hello
                    flavors {
                        french {}
                    }
                }
            }
            executables {
                main {
                    source sources.main
                    flavors {
                        german {}
                    }
                    binaries.all {
                        lib libraries.hello
                    }
                }
            }
        """

        and:
        // TODO:DAZ Fix the excpetion reporting
        executer.withStackTraceChecksDisabled()

        then:
        fails "germanMainExecutable"
        failure.assertHasCause("No shared library binary available for library 'hello' with flavor 'german'")
   }

    def write(def path, def sourceFile) {
        file("src/$path/${sourceFile.path}/${sourceFile.name}") << sourceFile.content
    }
}
