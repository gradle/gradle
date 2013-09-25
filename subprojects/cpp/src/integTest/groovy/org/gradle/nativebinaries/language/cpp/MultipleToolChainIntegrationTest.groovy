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
import org.gradle.nativebinaries.language.cpp.fixtures.app.CompilerDetectingHelloWorldApp
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition

class MultipleToolChainIntegrationTest extends AbstractIntegrationSpec {
    def helloWorld = new CompilerDetectingHelloWorldApp()

    def setup() {
        buildFile << """
            apply plugin: 'cpp'

            executables {
                main {}
            }
            libraries {
                hello {}
            }
            sources.main.cpp.lib libraries.hello
        """

        helloWorld.writeSources(file("src/main"), file("src/hello"))
    }

    @Requires(TestPrecondition.CAN_INSTALL_EXECUTABLE)
    def "can build with all available tool chains"() {
        List<AvailableToolChains.InstalledToolChain> installedToolChains = []
        for (AvailableToolChains.ToolChainCandidate toolChainCandidate : AvailableToolChains.getToolChains()) {
            if (toolChainCandidate.isAvailable()) {
                installedToolChains << toolChainCandidate
            }
        }

        def toolChainConfig = installedToolChains.collect({it.buildScriptConfig}).join("\n")

        when:
        buildFile << """
            toolChains {
${toolChainConfig}

                unavailable(Gcc) {
                    linker.executable = "does_not_exist"
                }
            }

"""

        then:
        def tasks = installedToolChains.collect { "install${it.id.capitalize()}MainExecutable" }
        succeeds tasks as String[]

        and:
        installedToolChains.each { toolChain ->
            checkInstall("build/install/mainExecutable/${toolChain.id}/main", toolChain)
        }
    }

    def "exception when building with unavailable tool chain"() {
        when:
        buildFile << """
            toolChains {
                bad(Gcc) {
                    linker.executable = "does_not_exist"
                }
            }
"""

        helloWorld.writeSources(file("src/main"))

        then:
        fails "mainExecutable"

        and:
        failure.assertHasDescription("Execution failed for task ':compileMainExecutableMainCpp'.")
        failure.assertHasCause("Tool chain bad is not available")
    }

    @Requires(TestPrecondition.CAN_INSTALL_EXECUTABLE)
    def "includes tool chain in task names and binary paths with two defined and one available"() {
        AvailableToolChains.InstalledToolChain toolChain = AvailableToolChains.getToolChains().get(0) as AvailableToolChains.InstalledToolChain

        given:
        buildFile << """
            toolChains {
${toolChain.buildScriptConfig}

                unavailable(Gcc) {
                    linker.executable = "does_not_exist"
                }
            }
"""

        helloWorld.writeSources(file("src/main"))

        when:
        succeeds "install${toolChain.id.capitalize()}MainExecutable"

        then:
        checkInstall("build/install/mainExecutable/${toolChain.id}/main", toolChain)
    }

    def checkInstall(String path, AvailableToolChains.InstalledToolChain toolChain) {
        def executable = file(OperatingSystem.current().getScriptName(path))
        executable.assertExists()
        assert executable.execute([], toolChain.runtimeEnv).out == "C++ ${toolChain.typeDisplayName}"
        return true
    }
}
