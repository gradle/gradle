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

    def "can build with all available tool chains"() {
        def helloWorld = new CppHelloWorldApp()

        List<AvailableToolChains.InstalledToolChain> installedToolChains = []
        for (AvailableToolChains.ToolChainCandidate toolChainCandidate : AvailableToolChains.getToolChains()) {
            if (toolChainCandidate.isAvailable()) {
                installedToolChains << toolChainCandidate
            }
        }

        def toolChainConfig = installedToolChains.collect({it.buildScriptConfig}).join("\n")

        given:
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

        when:
        def tasks = installedToolChains.collect { "install${it.id.capitalize()}MainExecutable" }
        run tasks as String[]

        then:
        installedToolChains.each { toolChain ->
            def executable = file(OperatingSystem.current().getExecutableName("build/install/mainExecutable/${toolChain.id}/main"))
            executable.assertExists()
            executable.execute([], toolChain.runtimeEnv).out == helloWorld.englishOutput
        }
    }
}
