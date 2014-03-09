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

import org.gradle.internal.os.OperatingSystem
import org.gradle.nativebinaries.language.cpp.fixtures.AbstractInstalledToolChainIntegrationSpec
import org.gradle.nativebinaries.language.cpp.fixtures.RequiresInstalledToolChain
import org.gradle.nativebinaries.language.cpp.fixtures.app.CHelloWorldApp
import org.gradle.test.fixtures.file.TestFile

import static org.gradle.nativebinaries.language.cpp.fixtures.ToolChainRequirement.GccCompatible

@RequiresInstalledToolChain(GccCompatible)
class GccToolChainCustomisationIntegrationTest extends AbstractInstalledToolChainIntegrationSpec {
    def helloWorldApp = new CHelloWorldApp()

    def setup() {
        buildFile << """
            apply plugin: 'c'

            model {
                toolChains {
                    ${toolChain.buildScriptConfig}
                }
            }

            executables {
                main {
                    binaries.all {
                        lib libraries.hello.static
                    }
                }
            }
            libraries {
                hello {}
            }
"""

        helloWorldApp.executable.writeSources(file("src/main"))
        helloWorldApp.library.writeSources(file("src/hello"))
    }

    def "can add binary configuration to target a platform"() {
        when:
        buildFile << """
            model {
                toolChains {
                    ${toolChain.id} {
                        addPlatformConfiguration(new ArmArchitecture())
                    }
                }
                platforms {
                    arm {
                        architecture "arm"
                    }
                    i386 {
                        architecture "i386"
                    }
                }
            }

            class ArmArchitecture implements TargetPlatformConfiguration {
                boolean supportsPlatform(Platform element) {
                    return element.getArchitecture().name == "arm"
                }

                List<String> getCppCompilerArgs() {
                    []
                }

                List<String> getCCompilerArgs() {
                    ["-m32", "-DFRENCH"]
                }

                List<String> getObjectiveCCompilerArgs() {
                    []
                }

                List<String> getObjectiveCppCompilerArgs() {
                    []
                }

                List<String> getAssemblerArgs() {
                    []
                }

                List<String> getLinkerArgs() {
                    ["-m32"]
                }

                List<String> getStaticLibraryArchiverArgs() {
                    []
                }
            }
"""

        and:
        succeeds "armMainExecutable", "i386MainExecutable"

        then:
        executable("build/binaries/mainExecutable/arm/main").binaryInfo.arch.name == "x86"
        executable("build/binaries/mainExecutable/arm/main").exec().out == helloWorldApp.frenchOutput

        executable("build/binaries/mainExecutable/i386/main").binaryInfo.arch.name == "x86"
        executable("build/binaries/mainExecutable/i386/main").exec().out == helloWorldApp.englishOutput
    }

    def "can add action to tool chain that modifies tool arguments prior to execution"() {
        when:
        buildFile << """
            model {
                toolChains {
                    ${toolChain.id} {
                        cCompiler.withArguments { args ->
                            Collections.replaceAll(args, "CUSTOM", "-DFRENCH")
                        }
                        linker.withArguments { args ->
                            args.remove "CUSTOM"
                        }
                        staticLibArchiver.withArguments { args ->
                            args.remove "CUSTOM"
                        }
                    }
                }
            }
            binaries.all {
                cCompiler.args "CUSTOM"
                linker.args "CUSTOM"
            }
            binaries.withType(StaticLibraryBinary) {
                staticLibArchiver.args "CUSTOM"
            }
"""
        then:
        succeeds "mainExecutable"

        then:
        executable("build/binaries/mainExecutable/main").exec().out == helloWorldApp.frenchOutput
    }

    def "can configure tool executables"() {
        def binDir = testDirectory.createDir("bin")
        wrapperTool(binDir.file("c-compiler"), toolChain.CCompiler, "-DFRENCH")
        wrapperTool(binDir.file("static-lib"), toolChain.staticLibArchiver)
        wrapperTool(binDir.file("linker"), toolChain.linker)

        when:
        buildFile << """
            model {
                toolChains {
                    ${toolChain.id} {
                        path file('${binDir.toURI()}')
                        cCompiler.executable = 'c-compiler'
                        staticLibArchiver.executable = 'static-lib'
                        linker.executable = 'linker'
                    }
                }
            }
"""
        succeeds "mainExecutable"

        then:
        executable("build/binaries/mainExecutable/main").exec().out == helloWorldApp.frenchOutput
    }

    def wrapperTool(TestFile script, String executable, String... additionalArgs) {
        if (OperatingSystem.current().windows) {
            script.text = "${executable} ${additionalArgs.join(' ')} %*"
        } else {
            script.text = "${executable} ${additionalArgs.join(' ')} \"\$@\""
            script.permissions = "rwxr--r--"
        }
    }
}
