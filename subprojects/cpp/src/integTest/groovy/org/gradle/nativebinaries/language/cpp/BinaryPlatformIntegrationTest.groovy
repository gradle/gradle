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
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition

class BinaryPlatformIntegrationTest extends AbstractInstalledToolChainIntegrationSpec {
    def helloWorldApp = new CppHelloWorldApp()

    def "setup"() {
        helloWorldApp.writeSources(file("src/main"))
    }

    @Requires([TestPrecondition.CAN_INSTALL_EXECUTABLE, TestPrecondition.NOT_WINDOWS])
    def "build binary for multiple target platforms"() {
        when:
        buildFile << """
            apply plugin: 'cpp'

            targetPlatforms {
                x86 {}
                x86_64 {}
            }
            executables {
                main {
                    binaries.all {
                        if (targetPlatform == targetPlatforms.x86) {
                            cppCompiler.args "-m32"
                        } else {
                            cppCompiler.args "-m64"
                        }
                    }
                }
            }
        """
        if (OperatingSystem.current().isMacOsX()) {
            buildFile << """
            executables {
                main {
                    binaries.all {
                        if (targetPlatform == targetPlatforms.x86) {
                            linker.args "-arch", "i386"
                        } else {
                            linker.args "-arch", "x86_64"
                        }
                    }
                }
            }
"""
        }

        and:
        succeeds "installX86MainExecutable", "installX86_64MainExecutable"

        then:
        installation("build/install/mainExecutable/x86").exec().out == helloWorldApp.englishOutput
        installation("build/install/mainExecutable/x86_64").exec().out == helloWorldApp.englishOutput

        // TODO:DAZ Verify binary and object file architectures
    }
}
