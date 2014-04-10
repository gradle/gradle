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
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.internal.os.OperatingSystem
import org.gradle.nativebinaries.language.cpp.fixtures.AvailableToolChains
import org.gradle.nativebinaries.language.cpp.fixtures.RequiresInstalledToolChain
import org.gradle.nativebinaries.language.cpp.fixtures.ToolChainRequirement
import org.gradle.nativebinaries.language.cpp.fixtures.app.CppCompilerDetectingTestApp
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import org.gradle.util.TextUtil

@RequiresInstalledToolChain
class MultipleToolChainIntegrationTest extends AbstractIntegrationSpec {
    def helloWorld = new CppCompilerDetectingTestApp()

    def setup() {
        buildFile << """
            apply plugin: 'cpp'

            executables {
                main {}
            }
        """

        helloWorld.writeSources(file("src/main"))
    }

    @Requires(TestPrecondition.CAN_INSTALL_EXECUTABLE)
    @RequiresInstalledToolChain(ToolChainRequirement.Gcc)
    def "can build with multiple tool chains"() {
        AvailableToolChains.InstalledToolChain x86ToolChain = OperatingSystem.current().isWindows() ?
                AvailableToolChains.getToolChain(ToolChainRequirement.VisualCpp) :
                AvailableToolChains.getToolChain(ToolChainRequirement.Clang)
        AvailableToolChains.InstalledToolChain sparcToolChain = AvailableToolChains.getToolChain(ToolChainRequirement.Gcc)

        when:
        buildFile << """
            model {
                platforms {
                    i386 {
                        architecture "i386"
                    }
                    sparc {
                        architecture "sparc"
                    }
                }
                toolChains {
                    ${x86ToolChain.buildScriptConfig}
                    ${sparcToolChain.buildScriptConfig}
                    ${sparcToolChain.id} {
                        target("sparc")
                    }
                }
            }

"""

        then:
        succeeds 'i386MainExecutable', 'sparcMainExecutable'

        and:
        def i386Exe = x86ToolChain.executable(file("build/binaries/mainExecutable/i386/main"))
        assert i386Exe.exec().out == "C++ " + x86ToolChain.displayName
        def sparcExe = sparcToolChain.executable(file("build/binaries/mainExecutable/sparc/main"))
        assert sparcExe.exec().out == "C++ " + sparcToolChain.displayName
    }

    def "exception when building with unavailable tool chain"() {
        when:

        buildFile << """
            model {
                toolChains {
                    bad(Gcc) {
                        cCompiler.executable = "does_not_exist"
                        cppCompiler.executable = "does_not_exist"
                    }
                }
            }
"""

        then:
        fails "mainExecutable"

        and:
        failure.assertHasDescription("Execution failed for task ':compileMainExecutableMainCpp'.")
        failure.assertHasCause(TextUtil.toPlatformLineSeparators("""No tool chain is available to build for platform 'current':
  - Tool chain 'bad' (GNU GCC): """))
    }

    def checkInstall(String path, AvailableToolChains.InstalledToolChain toolChain) {
        def executable = file(OperatingSystem.current().getScriptName(path))
        executable.assertExists()
        assert executable.execute([], toolChain.runtimeEnv).out == helloWorld.expectedOutput(toolChain)
        return true
    }
}
