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
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition

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

    def "can configure platform specific args"() {
        when:
        buildFile << """
            model {
                toolChains {
                    ${toolChain.id} {
                        target("arm"){
                            cCompiler.withArguments { args ->
                                args << "-m32"
                                args << "-DFRENCH"
                            }
                            linker.withArguments { args ->
                                args << "-m32"
                            }
                        }
                        target("sparc")
                    }
                }
                platforms {
                    arm {
                        architecture "arm"
                    }
                    i386 {
                        architecture "i386"
                    }
                    sparc {
                        architecture "sparc"
                    }
                }
            }
"""

        and:
        succeeds "armMainExecutable", "i386MainExecutable", "sparcMainExecutable"

        then:
        executable("build/binaries/mainExecutable/arm/main").binaryInfo.arch.name == "x86"
        executable("build/binaries/mainExecutable/arm/main").exec().out == helloWorldApp.frenchOutput

        executable("build/binaries/mainExecutable/i386/main").binaryInfo.arch.name == "x86"
        executable("build/binaries/mainExecutable/i386/main").exec().out == helloWorldApp.englishOutput

        executable("build/binaries/mainExecutable/sparc/main").exec().out == helloWorldApp.englishOutput
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

    @Requires(TestPrecondition.NOT_WINDOWS)
    def "can configure tool executables"() {
        def binDir = testDirectory.createDir("bin")
        wrapperTool(binDir, "c-compiler", toolChain.CCompiler, "-DFRENCH")
        wrapperTool(binDir, "static-lib", toolChain.staticLibArchiver)
        wrapperTool(binDir, "linker", toolChain.linker)

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

    def "can configure platform specific executables"() {
        def binDir = testDirectory.createDir("bin")
        wrapperTool(binDir, "c-compiler", toolChain.CCompiler, "-DFRENCH")
        wrapperTool(binDir, "static-lib", toolChain.staticLibArchiver)
        wrapperTool(binDir, "linker", toolChain.linker)

        when:
        buildFile << """
            model {
                toolChains {
                    ${toolChain.id} {
                        println path
                        //path file('${binDir.toURI()}')
                        println path
                        target("arm"){
                            cCompiler.executable = '${binDir.absolutePath}/c-compiler'
                            staticLibArchiver.executable = '${binDir.absolutePath}/static-lib'
                            linker.executable = '${binDir.absolutePath}/linker'
                        }
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
"""
        succeeds "armMainExecutable", "i386MainExecutable"

        then:
        executable("build/binaries/mainExecutable/arm/main").exec().out == helloWorldApp.frenchOutput
        executable("build/binaries/mainExecutable/i386/main").exec().out == helloWorldApp.englishOutput
    }

    def wrapperTool(TestFile binDir, String wrapperName, String executable, String... additionalArgs) {
        def script = binDir.file(OperatingSystem.current().getExecutableName(wrapperName))
        if (OperatingSystem.current().windows) {
            script.text = "${executable} ${additionalArgs.join(' ')} %*"
        } else {
            script.text = "${executable} ${additionalArgs.join(' ')} \"\$@\""
            script.permissions = "rwxr--r--"
        }
        return script
    }
}
