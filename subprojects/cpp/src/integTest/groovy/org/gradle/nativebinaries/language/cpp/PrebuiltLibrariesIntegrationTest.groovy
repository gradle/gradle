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
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition

// TODO:DAZ Get this working on MinGW and cygwin
//@Requires(TestPrecondition.CAN_INSTALL_EXECUTABLE)
@Requires([TestPrecondition.NOT_WINDOWS, TestPrecondition.CAN_INSTALL_EXECUTABLE])
class PrebuiltLibrariesIntegrationTest extends AbstractInstalledToolChainIntegrationSpec {
    final app = new CppHelloWorldApp()

    def "setup"() {
        settingsFile << "rootProject.name = 'test'"
        app.executable.writeSources(file("src/main"))
        app.library.writeSources(file("libs/src/hello"))

        file("libs/build.gradle") << """
            apply plugin: 'cpp'
            model {
                flavors {
                    create("english")
                    create("french")
                }
            }
            libraries {
                hello {
                    binaries.all {
                        if (flavor.name == "french") {
                            cppCompiler.define "FRENCH"
                        }
                    }
                }
            }
            task buildAll {
                dependsOn binaries.matching {
                    it.buildable
                }
            }
"""
    }

    private void preBuildLibrary() {
        executer.inDirectory(file("libs"))
        run "buildAll"
    }

    def "can link to a prebuilt header-only library with api linkage"() {
        given:
        app.alternateLibrarySources*.writeToDir(file("src/main"))
        buildFile << """
            apply plugin: 'cpp'
            model {
                repositories {
                    libs(PrebuiltLibraries) {
                        create("hello") {
                            headers.srcDir "libs/src/hello/headers"
                        }
                    }
                }
            }
            executables {
                main {}
            }
            sources.main.cpp.lib library: 'hello', linkage: 'api'
        """

        when:
        succeeds "installMainExecutable"

        then:
        installation("build/install/mainExecutable").exec().out == app.alternateLibraryOutput
    }

    def "can link to a prebuilt library with static and shared linkage"() {
        given:
        preBuildLibrary()

        and:
        buildFile << """
            apply plugin: 'cpp'
            model {
                repositories {
                    libs(PrebuiltLibraries) {
                        create("hello") {
                            headers.srcDir "libs/src/hello/headers"
                            binaries.withType(StaticLibraryBinary) { binary ->
                                def os = binary.targetPlatform.operatingSystem
                                def libName = os.windows ? 'hello.lib' : 'libhello.a'
                                staticLibraryFile = file("libs/build/binaries/helloStaticLibrary/english/\${libName}")
                            }
                            binaries.withType(SharedLibraryBinary) { binary ->
                                def os = binary.targetPlatform.operatingSystem
                                def libName = os.windows ? 'hello.dll' : (os.macOsX ? 'libhello.dylib' : 'libhello.so')
                                sharedLibraryFile = file("libs/build/binaries/helloSharedLibrary/french/\${libName}")
                            }
                        }
                    }
                }
            }
            executables {
                main {}
                mainStatic {}
            }
            sources {
                main.cpp {
                    lib library: 'hello'
                }
                mainStatic.cpp {
                    source.srcDir "src/main/cpp"
                    lib library: 'hello', linkage: 'static'
                }
            }
        """

        when:
        succeeds "installMainExecutable", "installMainStaticExecutable"

        then:
        installation("build/install/mainExecutable").exec().out == app.frenchOutput
        installation("build/install/mainStaticExecutable").exec().out == app.englishOutput
    }

    def "produces reasonable error message when no output file is defined for binary"() {
        given:
        buildFile << """
            apply plugin: 'cpp'
            model {
                repositories {
                    libs(PrebuiltLibraries) {
                        create("hello") {
                            headers.srcDir "libs/src/hello/headers"
                        }
                    }
                }
            }
            executables {
                main {}
            }
            sources.main.cpp.lib library: 'hello', linkage: 'static'
        """

        when:
        fails "mainExecutable"

        then:
        failure.assertHasDescription("Static library file not set for prebuilt library 'hello'.")
    }

    def "produces reasonable error message when prebuilt library output file does not exist"() {
        given:
        buildFile << """
            apply plugin: 'cpp'
            model {
                repositories {
                    libs(PrebuiltLibraries) {
                        create("hello") {
                            headers.srcDir "libs/src/hello/headers"
                            binaries.withType(StaticLibraryBinary) { binary ->
                                staticLibraryFile = file("does_not_exist")
                            }
                        }
                    }
                }
            }
            executables {
                main {}
            }
            sources.main.cpp.lib library: 'hello', linkage: 'static'
        """

        when:
        succeeds "tasks"
        fails "mainExecutable"

        then:
        failure.assertHasDescription("Static library file ${file("does_not_exist").absolutePath} does not exist for prebuilt library 'hello'.")
    }

    def "produces reasonable error message when prebuilt library does not exist"() {
        given:
        buildFile << """
            apply plugin: 'cpp'
            model {
                repositories {
                    libs(PrebuiltLibraries) {
                        create("hello") {
                        }
                    }
                }
            }
            executables {
                main {}
            }
            sources.main.cpp.lib library: 'other'
        """

        when:
        fails "mainExecutable"

        then:
        failure.assertHasDescription("Could not locate library 'other'.")
        failure.assertHasCause("Library with name 'other' not found.")
        failure.assertHasCause("PrebuiltLibrary with name 'other' not found.")
    }

    def "produces reasonable error message when attempting to access prebuilt library in a different project"() {
        given:
        buildFile << """
            apply plugin: 'cpp'
            model {
                repositories {
                    libs(PrebuiltLibraries) {
                        create("hello") {
                            headers.srcDir "libs/src/hello/headers"
                        }
                    }
                }
            }
            executables {
                main {}
            }
            sources.main.cpp.lib project: ':different', library: 'hello', linkage: 'api'
        """

        when:
        fails "mainExecutable"

        then:
        failure.assertHasDescription("Could not locate library 'hello' for project ':different'.")
        failure.assertHasCause("Project with path ':different' could not be found in root project 'test'.")
        failure.assertHasCause("Cannot resolve prebuilt library in another project.")
    }
}
