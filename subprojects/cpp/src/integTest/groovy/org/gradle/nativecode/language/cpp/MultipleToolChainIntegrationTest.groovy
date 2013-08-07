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
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition

class MultipleToolChainIntegrationTest extends AbstractIntegrationSpec {

    @Requires(TestPrecondition.NOT_WINDOWS)
    def "can build with all available tool chains"() {
        def helloWorld = new CppHelloWorldApp()

        List<AvailableToolChains.InstalledToolChain> installedToolChains = []
        for (AvailableToolChains.ToolChainCandidate toolChainCandidate : AvailableToolChains.getToolChains()) {
            if (toolChainCandidate.isVisualCpp() || !toolChainCandidate.isAvailable()) {
                continue;
            }
            installedToolChains << toolChainCandidate
        }

        def toolChainConfig = ""
        installedToolChains.each { toolChain ->
            toolChainConfig += """
                ${toolChain.id}(${toolChain.implementationClass})
                ${toolChain.id}.binPath = "${toolChain.pathEntries.join(':')}"
"""
        }

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

        println "---------------------------------------------------------------"
        println buildFile.text
        println "---------------------------------------------------------------"

        when:
        def tasks = installedToolChains.collect { "${it.id}MainExecutable" }
        executer.withArgument("--debug")
        run tasks as String[]

        then:
        installedToolChains.each {
            def executable = file(OperatingSystem.current().getExecutableName("build/binaries/mainExecutable/${it.id}/main"))
            executable.assertExists()
            println executable.exec().out
            executable.exec().out == helloWorld.englishOutput
        }
    }
}
