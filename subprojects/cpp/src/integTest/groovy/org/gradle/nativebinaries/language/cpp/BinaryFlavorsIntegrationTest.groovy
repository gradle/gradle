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
import org.gradle.nativebinaries.language.cpp.fixtures.app.ExeWithLibraryUsingLibraryHelloWorldApp
import org.gradle.nativebinaries.language.cpp.fixtures.app.HelloWorldApp
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import spock.lang.Ignore

@Requires(TestPrecondition.CAN_INSTALL_EXECUTABLE)
class BinaryFlavorsIntegrationTest extends AbstractInstalledToolChainIntegrationSpec {
    static final DEFAULT = HelloWorldApp.HELLO_WORLD
    static final FRENCH = HelloWorldApp.HELLO_WORLD_FRENCH

    def helloWorldApp = new ExeWithLibraryUsingLibraryHelloWorldApp()

    def "setup"() {
        settingsFile << "rootProject.name = 'test'"

        buildFile << """
            apply plugin: "cpp"
            model {
                flavors {
                    english
                    french
                    german
                }
            }
            nativeLibraries {
                greetings {
                    binaries.all {
                        if (!org.gradle.internal.os.OperatingSystem.current().isWindows()) {
                            cppCompiler.args("-fPIC");
                        }
                    }
                }
                hello {
                    binaries.all {
                        lib libraries.greetings.static
                    }
                }
            }
            nativeExecutables {
                main {
                    binaries.all {
                        lib libraries.hello
                    }
                }
            }
        """

        helloWorldApp.writeSources(file("src/main"), file("src/hello"), file("src/greetings"))
    }

    def "can configure components for a single flavor"() {
        given:
        buildFile << """
    binaries.all {
        if (flavor == flavors.french) {
            cppCompiler.define "FRENCH"
        }
    }
    executables.main.targetFlavors "french"
    libraries.hello.targetFlavors "french"
    libraries.greetings.targetFlavors "french"
"""
        when:
        succeeds "installMainExecutable"

        then:
        installation("build/install/mainExecutable").exec().out == FRENCH + " " + FRENCH
    }

    def "builds executable for each defined flavor when not configured for component"() {
        when:
        succeeds "installEnglishMainExecutable", "installFrenchMainExecutable", "installGermanMainExecutable"

        then:
        installation("build/install/mainExecutable/english").assertInstalled()
        installation("build/install/mainExecutable/french").assertInstalled()
        installation("build/install/mainExecutable/german").assertInstalled()
    }

    def "executable with flavors depends on library with matching flavors"() {
        when:
        buildFile << """
            nativeExecutables {
                main {
                    targetFlavors "english", "french"
                    binaries.all {
                        if (flavor == flavors.french) {
                            cppCompiler.define "FRENCH"
                        }
                    }
                }
            }
            libraries.all {
                targetFlavors "english", "french"
                binaries.all {
                    if (flavor == flavors.french) {
                        cppCompiler.define "FRENCH"
                    }
                }
            }
        """

        and:
        succeeds "installEnglishMainExecutable", "installFrenchMainExecutable"

        then:
        installation("build/install/mainExecutable/english").exec().out == DEFAULT + " " + DEFAULT
        installation("build/install/mainExecutable/french").exec().out == FRENCH + " " + FRENCH
    }

    // TODO:DAZ Un-ignore
    @Ignore("Requires proper dependency resolution")
    def "executable with flavors depends on library with no defined flavor"() {
        when:
        buildFile << """
            nativeExecutables {
                main {
                    targetFlavors "english", "french"
                    binaries.all {
                        if (flavor == flavors.french) {
                            cppCompiler.define "FRENCH"
                        }
                    }
                }
            }
        """

        and:
        succeeds "installEnglishMainExecutable", "installFrenchMainExecutable"

        then:
        installation("build/install/mainExecutable/english").exec().out == DEFAULT + " " + DEFAULT
        installation("build/install/mainExecutable/french").exec().out == FRENCH + " " + DEFAULT
    }

    // TODO:DAZ Un-ignore
    @Ignore("Library resolution does not yet handle this case")
    def "executable with flavors depends on a library with a single flavor which depends on a library with flavors"() {
        when:
        buildFile << """
            nativeExecutables {
                main {
                    targetFlavors "english", "french"
                    binaries.all {
                        if (flavor == flavors.french) {
                            cppCompiler.define "FRENCH"
                        }
                    }
                }
            }
            nativeLibraries {
                greetings {
                    targetFlavors "english", "french"
                    binaries.all {
                        if (flavor == flavors.french) {
                            cppCompiler.define "FRENCH"
                        }
                    }
                }
            }
        """

        and:
        succeeds "installEnglishMainExecutable", "installFrenchMainExecutable"

        then:
        installation("build/install/mainExecutable/english").exec().out == DEFAULT + " " + DEFAULT
        installation("build/install/mainExecutable/french").exec().out == FRENCH + " " + FRENCH
    }

    def "build fails when library has no matching flavour"() {
        when:
        buildFile << """
            apply plugin: "cpp"
            nativeLibraries {
                hello {
                    targetFlavors "english", "french"
                }
            }
            nativeExecutables {
                main {
                    targetFlavors "english", "german"
                    binaries.all {
                        lib libraries.hello
                    }
                }
            }
        """

        then:
        fails "germanMainExecutable"
        failure.assertHasDescription("No shared library binary available for library 'hello' with [flavor: 'german', platform: 'current', buildType: 'debug']")
    }

    def "fails with reasonable error message when trying to target an unknown flavor"() {
        when:
        buildFile << """
            executables.main.targetFlavors "unknown"
"""

        and:
        fails "mainExecutable"

        then:
        failure.assertHasDescription("A problem occurred configuring root project 'test'.")
        failure.assertHasCause("Invalid Flavor: 'unknown'")
    }
}
