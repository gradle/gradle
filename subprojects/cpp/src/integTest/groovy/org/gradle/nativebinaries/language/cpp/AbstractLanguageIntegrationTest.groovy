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
import org.gradle.nativebinaries.language.cpp.fixtures.app.HelloWorldApp
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition

abstract class AbstractLanguageIntegrationTest extends AbstractInstalledToolChainIntegrationSpec {

    abstract HelloWorldApp getHelloWorldApp()

    def "setup"() {
        buildFile << helloWorldApp.pluginScript
        buildFile << helloWorldApp.extraConfiguration
    }

    def "compile and link executable"() {
        given:
        buildFile << """
            executables {
                main {}
            }
        """

        and:
        helloWorldApp.writeSources(file("src/main"))

        when:
        run "mainExecutable"

        then:
        def mainExecutable = executable("build/binaries/mainExecutable/main")
        mainExecutable.assertExists()
        mainExecutable.exec().out == helloWorldApp.englishOutput
    }

    def "build executable with custom compiler arg"() {
        given:
        buildFile << """
            executables {
                main {
                    binaries.all {
                        ${helloWorldApp.compilerArgs("-DFRENCH")}
                    }
                }
            }
        """

        and:
        helloWorldApp.writeSources(file("src/main"))

        when:
        run "mainExecutable"

        then:
        def mainExecutable = executable("build/binaries/mainExecutable/main")
        mainExecutable.assertExists()
        mainExecutable.exec().out == helloWorldApp.frenchOutput
    }

    def "build executable with macro defined"() {
        given:
        buildFile << """
            executables {
                main {
                    binaries.all {
                        ${helloWorldApp.compilerDefine("FRENCH")}
                    }
                }
            }
        """

        and:
        helloWorldApp.writeSources(file("src/main"))

        when:
        run "mainExecutable"

        then:
        def mainExecutable = executable("build/binaries/mainExecutable/main")
        mainExecutable.assertExists()
        mainExecutable.exec().out == helloWorldApp.frenchOutput
    }

    @Requires(TestPrecondition.CAN_INSTALL_EXECUTABLE)
    def "build shared library and link into executable"() {
        given:
        buildFile << """
            executables {
                main {}
            }
            libraries {
                hello {}
            }
            sources.main.${helloWorldApp.sourceType}.lib libraries.hello
        """

        and:
        helloWorldApp.executable.writeSources(file("src/main"))
        helloWorldApp.library.writeSources(file("src/hello"))

        when:
        run "installMainExecutable"

        then:
        sharedLibrary("build/binaries/helloSharedLibrary/hello").assertExists()
        executable("build/binaries/mainExecutable/main").assertExists()

        def install = installation("build/install/mainExecutable")
        install.assertInstalled()
        install.assertIncludesLibraries("hello")
        install.exec().out == helloWorldApp.englishOutput
    }

    @Requires(TestPrecondition.CAN_INSTALL_EXECUTABLE)
    def "build static library and link into executable"() {
        given:
        buildFile << """
            executables {
                main {}
            }
            libraries {
                hello {
                    binaries.withType(StaticLibraryBinary) {
                        ${helloWorldApp.compilerDefine("FRENCH")}
                    }
                }
            }
            sources.main.${helloWorldApp.sourceType}.lib libraries.hello.static
        """

        and:
        helloWorldApp.executable.writeSources(file("src/main"))
        helloWorldApp.library.writeSources(file("src/hello"))

        when:
        run "installMainExecutable"

        then:
        staticLibrary("build/binaries/helloStaticLibrary/hello").assertExists()
        executable("build/binaries/mainExecutable/main").assertExists()

        and:
        def install = installation("build/install/mainExecutable")
        install.assertInstalled()
        install.exec().out == helloWorldApp.frenchOutput
    }
}

