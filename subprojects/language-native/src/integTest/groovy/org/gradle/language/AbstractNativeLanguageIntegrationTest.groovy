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

package org.gradle.language

import org.apache.commons.lang.RandomStringUtils
import org.gradle.integtests.fixtures.ToBeFixedForInstantExecution
import org.gradle.nativeplatform.fixtures.AbstractInstalledToolChainIntegrationSpec
import org.gradle.nativeplatform.fixtures.app.HelloWorldApp
import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import spock.lang.Ignore

abstract class AbstractNativeLanguageIntegrationTest extends AbstractInstalledToolChainIntegrationSpec {

    abstract HelloWorldApp getHelloWorldApp()

    def "setup"() {
        buildFile << helloWorldApp.pluginScript
        buildFile << helloWorldApp.extraConfiguration
    }

    @ToBeFixedForInstantExecution
    def "compile and link executable"() {
        given:
        buildFile << """
            model {
                components {
                    main(NativeExecutableSpec)
                }
            }
        """

        and:
        helloWorldApp.writeSources(file("src/main"))

        when:
        run "mainExecutable"

        then:
        def mainExecutable = executable("build/exe/main/main")
        mainExecutable.assertExists()
        mainExecutable.exec().out == helloWorldApp.englishOutput
    }

    @ToBeFixedForInstantExecution
    def "build executable with custom compiler arg"() {
        given:
        buildFile << """
            model {
                components {
                    main(NativeExecutableSpec) {
                        binaries.all {
                            ${helloWorldApp.compilerArgs("-DFRENCH")}
                        }
                    }
                }
            }
        """

        and:
        helloWorldApp.writeSources(file("src/main"))

        when:
        run "mainExecutable"

        then:
        def mainExecutable = executable("build/exe/main/main")
        mainExecutable.assertExists()
        mainExecutable.exec().out == helloWorldApp.frenchOutput
    }

    @ToBeFixedForInstantExecution
    def "build executable with macro defined"() {
        given:
        buildFile << """
            model {
                components {
                    main(NativeExecutableSpec) {
                        binaries.all {
                            ${helloWorldApp.compilerDefine("FRENCH")}
                        }
                    }
                }
            }
        """

        and:
        helloWorldApp.writeSources(file("src/main"))

        when:
        run "mainExecutable"

        then:
        def mainExecutable = executable("build/exe/main/main")
        mainExecutable.assertExists()
        mainExecutable.exec().out == helloWorldApp.frenchOutput
    }

    @Requires(TestPrecondition.CAN_INSTALL_EXECUTABLE)
    @ToBeFixedForInstantExecution
    def "install and run executable with dependencies"() {
        given:
        buildFile << """
            model {
                components {
                    main(NativeExecutableSpec) {
                        sources {
                            ${helloWorldApp.sourceType}.lib library: "hello"
                        }
                    }
                    hello(NativeLibrarySpec)
                }
            }
        """

        and:
        helloWorldApp.executable.writeSources(file("src/main"))
        helloWorldApp.library.writeSources(file("src/hello"))

        when:
        run "installMainExecutable"

        then:
        sharedLibrary("build/libs/hello/shared/hello").assertExists()
        executable("build/exe/main/main").assertExists()

        def install = installation("build/install/main")
        install.assertInstalled()
        install.assertIncludesLibraries("hello")
        install.exec().out == helloWorldApp.englishOutput
    }

    @Requires(TestPrecondition.CAN_INSTALL_EXECUTABLE)
    @ToBeFixedForInstantExecution
    def "install and run executable with dependencies and customized installation"() {
        given:
        buildFile << """
            model {
                components {
                    main(NativeExecutableSpec) {
                        sources {
                            ${helloWorldApp.sourceType}.lib library: "hello"
                        }
                        binaries.withType(NativeExecutableBinarySpec) {
                            installation.directory = file("foo/custom")
                        }
                    }
                    hello(NativeLibrarySpec)
                }
            }
        """

        and:
        helloWorldApp.executable.writeSources(file("src/main"))
        helloWorldApp.library.writeSources(file("src/hello"))

        when:
        run "installMainExecutable"

        then:
        def install = installation("foo/custom")
        install.assertInstalled()
        install.assertIncludesLibraries("hello")
        install.exec().out == helloWorldApp.englishOutput
    }

    @Requires(TestPrecondition.CAN_INSTALL_EXECUTABLE)
    @ToBeFixedForInstantExecution
    def "build shared library and link into executable"() {
        given:
        buildFile << """
            model {
                components {
                    main(NativeExecutableSpec) {
                        sources {
                            ${helloWorldApp.sourceType}.lib library: "hello"
                        }
                    }
                    hello(NativeLibrarySpec)
                }
            }
        """

        and:
        helloWorldApp.executable.writeSources(file("src/main"))
        helloWorldApp.library.writeSources(file("src/hello"))

        when:
        run "installMainExecutable"

        then:
        sharedLibrary("build/libs/hello/shared/hello").assertExists()
        executable("build/exe/main/main").assertExists()

        def install = installation("build/install/main")
        install.assertInstalled()
        install.assertIncludesLibraries("hello")
        install.exec().out == helloWorldApp.englishOutput
    }

    @Requires(TestPrecondition.CAN_INSTALL_EXECUTABLE)
    @ToBeFixedForInstantExecution
    def "build static library and link into executable"() {
        given:
        buildFile << """
            model {
                components {
                    main(NativeExecutableSpec) {
                        sources {
                            ${helloWorldApp.sourceType}.lib library: "hello", linkage: "static"
                        }
                    }
                    hello(NativeLibrarySpec) {
                        binaries.withType(StaticLibraryBinarySpec) {
                            ${helloWorldApp.compilerDefine("FRENCH")}
                        }
                    }
                }
            }
        """

        and:
        helloWorldApp.executable.writeSources(file("src/main"))
        helloWorldApp.library.writeSources(file("src/hello"))

        when:
        run "installMainExecutable"

        then:
        staticLibrary("build/libs/hello/static/hello").assertExists()
        executable("build/exe/main/main").assertExists()

        and:
        def install = installation("build/install/main")
        install.assertInstalled()
        install.exec().out == helloWorldApp.frenchOutput
    }

    @ToBeFixedForInstantExecution
    def "link order is stable across project directories for the same sources"() {
        def firstCopy = file("firstDir")
        def secondCopy = file("secondDir")
        [ firstCopy, secondCopy ].each { projectDir ->
            projectDir.file("settings.gradle").touch()
            def buildFile = projectDir.file("build.gradle")
            buildFile << helloWorldApp.pluginScript
            buildFile << helloWorldApp.extraConfiguration
            buildFile << """
            model {
                components {
                    main(NativeExecutableSpec)
                }
            }
        """
            helloWorldApp.writeSources(projectDir.file("src/main"))
        }
        when:
        executer.usingProjectDirectory(firstCopy)
        succeeds("mainExecutable")
        and:
        executer.usingProjectDirectory(secondCopy)
        succeeds("mainExecutable")
        then:
        def firstOptions = linkerOptionsFor("linkMainExecutable", firstCopy)
        def secondOptions = linkerOptionsFor("linkMainExecutable", secondCopy)
        def firstOptionsOrder = firstOptions.linkedObjects().collect { it.name }
        def secondOptionsOrder = secondOptions.linkedObjects().collect { it.name }
        firstOptionsOrder == secondOptionsOrder
    }

    @Ignore
    def "can run project in extended nested file paths"() {
        // windows can't handle a path up to 260 characters
        // we create a path that ends up with build folder longer than is 260
        def projectPathOffset = 180 - testDirectory.getAbsolutePath().length()
        def nestedProjectPath = RandomStringUtils.randomAlphanumeric(projectPathOffset - 10) + "/123456789"

        setup:
        def deepNestedProjectFolder = file(nestedProjectPath)
        executer.usingProjectDirectory(deepNestedProjectFolder)
        def TestFile buildFile = deepNestedProjectFolder.file("build.gradle")
        buildFile << helloWorldApp.pluginScript
        buildFile << helloWorldApp.extraConfiguration
        buildFile << """
            model {
                components {
                    main(NativeExecutableSpec)
                }
            }
        """

        and:
        helloWorldApp.writeSources(file("$nestedProjectPath/src/main"))

        expect:
        succeeds "mainExecutable"
        def mainExecutable = executable("$nestedProjectPath/build/exe/main/main")
        mainExecutable.assertExists()
        mainExecutable.exec().out == helloWorldApp.englishOutput
    }
}

