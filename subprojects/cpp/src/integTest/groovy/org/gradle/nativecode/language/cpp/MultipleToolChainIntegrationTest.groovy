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


package org.gradle.nativecode.language.cpp

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.internal.os.OperatingSystem
import org.gradle.nativecode.language.cpp.fixtures.AvailableToolChains
import org.gradle.nativecode.language.cpp.fixtures.app.CppHelloWorldApp

class MultipleToolChainIntegrationTest extends AbstractIntegrationSpec {
    def helloWorld = new CppHelloWorldApp()

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
            apply plugin: "cpp"

            toolChains {
${toolChainConfig}
            }

            sources {
                main {}
            }
            executables {
                main {
                    source sources.main
                }
            }
"""

        helloWorld.writeSources(file("src/main"))

        then:
        if (installedToolChains.size() == 1) {
            // Check for single tool chain
            succeeds "installMainExecutable"
            checkBinary("build/install/mainExecutable/main", installedToolChains.get(0).runtimeEnv)
            return;
        }

        // Check for multiple tool chains
        def tasks = installedToolChains.collect { "install${it.id.capitalize()}MainExecutable" }
        run tasks as String[]
        installedToolChains.each { toolChain ->
            checkBinary("build/install/mainExecutable/${toolChain.id}/main", toolChain.runtimeEnv)
        }
    }

    def checkBinary(String path, List runtimeEnv) {
        def executable = file(OperatingSystem.current().getExecutableName(path))
        executable.assertExists()
        assert executable.execute([], runtimeEnv).out == helloWorld.englishOutput
    }
}
