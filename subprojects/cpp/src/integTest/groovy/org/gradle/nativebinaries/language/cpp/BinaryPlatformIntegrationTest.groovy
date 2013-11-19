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
import org.gradle.nativebinaries.language.cpp.fixtures.app.CppHelloWorldApp
import org.gradle.nativebinaries.language.cpp.fixtures.binaryinfo.DumpbinBinaryInfo
import org.gradle.nativebinaries.language.cpp.fixtures.binaryinfo.OtoolBinaryInfo
import org.gradle.nativebinaries.language.cpp.fixtures.binaryinfo.ReadelfBinaryInfo
import org.gradle.test.fixtures.file.TestFile

class BinaryPlatformIntegrationTest extends AbstractInstalledToolChainIntegrationSpec {
    def helloWorldApp = new CppHelloWorldApp()

    def setup() {
        buildFile << """
            apply plugin: 'cpp'

            executables {
                main {}
            }
            libraries {
                hello {}
            }
            sources.main.cpp.lib libraries.hello.static
        """

        helloWorldApp.executable.writeSources(file("src/main"))
        helloWorldApp.library.writeSources(file("src/hello"))
    }

    def "build binary for multiple target architectures"() {
        when:
        buildFile << """
            targetPlatforms {
                x86 {
                    architecture "x86"
                }
                x86_64 {
                    architecture "x86_64"
                }
                itanium {
                    architecture "ia-64"
                }
                arm {
                    // ARM is not yet supported on any tool chain
                    architecture "arm"
                }
            }
            task buildExecutables {
                dependsOn binaries.withType(ExecutableBinary).matching {
                    it.buildable
                }
            }
"""

        and:
        succeeds "buildExecutables"

        then:
        executable("build/binaries/mainExecutable/x86/main").binaryInfo.arch.name == "x86"
        binaryInfo(objectFile("build/objectFiles/mainExecutable/x86/mainCpp/main")).arch.name == "x86"

        // x86_64 binaries not supported on MinGW or cygwin
        if (toolChain.id == "mingw" || toolChain.id == "gcccygwin") {
            executable("build/binaries/mainExecutable/x86_64/main").assertDoesNotExist()
        } else {
            executable("build/binaries/mainExecutable/x86_64/main").binaryInfo.arch.name == "x86_64"
            binaryInfo(objectFile("build/objectFiles/mainExecutable/x86_64/mainCpp/main")).arch.name == "x86_64"
        }

        // Itanium only supported on visualCpp
        if (toolChain.visualCpp) {
            executable("build/binaries/mainExecutable/itanium/main").binaryInfo.arch.name == "ia-64"
            binaryInfo(objectFile("build/objectFiles/mainExecutable/itanium/mainCpp/main")).arch.name == "ia-64"
        } else {
            executable("build/binaries/mainExecutable/itanium/main").assertDoesNotExist()
        }

        // ARM not supported on any platform
        executable("build/binaries/mainExecutable/arm/main").assertDoesNotExist()
    }

    def "can configure binary for multiple target operating systems"() {
        when:
        buildFile << """
            targetPlatforms {
                windows {
                    operatingSystem "windows"
                }
                linux {
                    operatingSystem "linux"
                }
                osx {
                    operatingSystem "osx"
                }
            }

            binaries.matching({ it.targetPlatform.operatingSystem.windows }).all {
                cppCompiler.define "FRENCH"
            }
            task buildExecutables {
                dependsOn binaries.withType(ExecutableBinary).matching {
                    it.buildable
                }
            }
        """

        and:
        succeeds "buildExecutables"

        then:
        final os = OperatingSystem.current()
        if (os.windows) {
            executable("build/binaries/mainExecutable/windows/main").exec().out ==  helloWorldApp.frenchOutput
        }
        if (os.linux) {
            executable("build/binaries/mainExecutable/linux/main").exec().out ==  helloWorldApp.englishOutput
        }
        if (os.macOsX) {
            executable("build/binaries/mainExecutable/osx/main").exec().out ==  helloWorldApp.englishOutput
        }
    }
    def "fails with reasonable error message when trying to build for an unavailable architecture"() {
        when:
        buildFile << """
            targetPlatforms {
                arm {
                    architecture "arm"
                }
            }
"""

        and:
        fails "mainExecutable"

        then:
        failure.assertHasDescription("Execution failed for task ':compileMainExecutableMainCpp'.")
        failure.assertHasCause("Tool chain ${toolChain.id} cannot build for platform: arm")
    }

    def "fails with reasonable error message when trying to build for a different operating system"() {
        when:
        buildFile << """
            targetPlatforms {
                solaris {
                    operatingSystem "solaris"
                }
            }
"""

        and:
        fails "mainExecutable"

        then:
        failure.assertHasDescription("Execution failed for task ':compileMainExecutableMainCpp'.")
        failure.assertHasCause("Tool chain ${toolChain.id} cannot build for platform: solaris")
    }

    def binaryInfo(TestFile file) {
        file.assertIsFile()
        if (OperatingSystem.current().isMacOsX()) {
            return new OtoolBinaryInfo(file)
        }
        if (OperatingSystem.current().isWindows()) {
            return new DumpbinBinaryInfo(file, toolChain)
        }
        return new ReadelfBinaryInfo(file)
    }

}
