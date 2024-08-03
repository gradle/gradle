/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.language.cpp

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.nativeplatform.fixtures.AvailableToolChains
import org.gradle.nativeplatform.fixtures.AvailableToolChains.InstalledToolChain
import org.gradle.nativeplatform.fixtures.app.CppHelloWorldApp
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions
import org.opentest4j.TestAbortedException

@Requires(UnitTestPreconditions.NotMacOsM1)
class CppToolChainChangesIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        def app = new CppHelloWorldApp()

        buildFile << """
            project(':library') {
                apply plugin: 'cpp-library'
                library {
                    publicHeaders.from('src/main/headers')
                }
            }
            project(':app') {
                apply plugin: 'cpp-application'
                dependencies {
                    implementation project(':library')
                }
            }
        """
        createDirs("library", "app")
        settingsFile << """
            rootProject.name = 'test'
            include 'library', 'app'
        """
        app.mainSource.writeToDir(file("app/src/main"))
        app.libraryHeader.writeToDir(file("library/src/main"))
        app.commonHeader.writeToDir(file("library/src/main"))
        app.librarySources.each {
            it.writeToDir(file("library/src/main"))
        }
    }

    def "recompiles binary when toolchain changes from #toolChainBefore to #toolChainAfter"() {
        buildFile.text = buildScriptForToolChains(toolChainBefore, toolChainAfter)
        def useAlternateToolChain = false
        executer.beforeExecute({
            if (useAlternateToolChain) {
                toolChainAfter.configureExecuter(it)
            } else {
                toolChainBefore.configureExecuter(it)
            }
        })

        when:
        run ':app:compileDebugCpp'

        then:
        executedAndNotSkipped ':app:compileDebugCpp'

        when:
        useAlternateToolChain = true
        run ':app:compileDebugCpp', '-PuseAlternativeToolChain=true', "--info"

        then:
        executedAndNotSkipped ':app:compileDebugCpp'
        output =~ /Value of input property 'compilerVersion\.(version|type)' has changed for task ':app:compileDebugCpp'/

        where:
        toolChains << toolChainPairs
        toolChainBefore = toolChains[0]
        toolChainAfter = toolChains[1]
    }

    private static GString buildScriptForToolChains(InstalledToolChain before, InstalledToolChain after) {
        """
            allprojects {
                apply plugin: ${before.pluginClass}
                apply plugin: ${after.pluginClass}

                model {
                    toolChains {
                        if (findProperty('useAlternativeToolChain')) {
                            ${after.buildScriptConfig}
                        } else {
                            ${before.buildScriptConfig}
                        }
                    }
                }
            }
            project(':library') {
                apply plugin: 'cpp-library'
                library {
                    publicHeaders.from('src/main/headers')
                    targetMachines = [machines.host().x86]
                }
            }
            project(':app') {
                apply plugin: 'cpp-application'
                application {
                    targetMachines = [machines.host().x86]
                }
                dependencies {
                    implementation project(':library')
                }
            }
        """
    }

    private static List<List<InstalledToolChain>> getToolChainPairs() {
        def availableToolChains = AvailableToolChains.toolChains.findAll {
            it.available && !(it instanceof AvailableToolChains.InstalledSwiftc)
        }
        println("AvailableToolChains: $availableToolChains")
        int numberOfToolChains = availableToolChains.size()
        if (numberOfToolChains < 2) {
            // Don't use JUnit 4 Assume: https://github.com/spockframework/spock/issues/1185
            throw new TestAbortedException('2 or more tool chains are required for this test')
        }
        List<List<InstalledToolChain>> result = (0..<(numberOfToolChains - 1)).collectMany { first ->
            ((first + 1)..<numberOfToolChains).collect { second ->
                [availableToolChains[first], availableToolChains[second]]
            }
        }
        // To avoid [gcc, g++] pair because they have same display name
        return result.findAll { it[0].displayName != it[1].displayName }
    }
}
