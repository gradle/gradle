/*
 * Copyright 2014 the original author or authors.
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

import org.gradle.nativebinaries.language.cpp.fixtures.AbstractInstalledToolChainIntegrationSpec
import org.gradle.nativebinaries.language.cpp.fixtures.RequiresInstalledToolChain
import org.gradle.nativebinaries.language.cpp.fixtures.app.CHelloWorldApp
import org.hamcrest.Matchers

import static org.gradle.nativebinaries.language.cpp.fixtures.ToolChainRequirement.GccCompatible

@RequiresInstalledToolChain(GccCompatible)
class GccToolChainDiscoveryIntegrationTest extends AbstractInstalledToolChainIntegrationSpec {
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

    def "can build when language tools that are not required are not available"() {
        when:
        buildFile << """
            model {
                toolChains {
                    ${toolChain.id} {
                        cppCompiler.executable = 'does-not-exist'
                    }
                }
            }
"""
        succeeds "mainExecutable"

        then:
        executable("build/binaries/mainExecutable/main").exec().out == helloWorldApp.englishOutput
    }

    def "does not break when compiler not available and not building"() {
        when:
        buildFile << """
            model {
                toolChains {
                    ${toolChain.id} {
                        cCompiler.executable = 'does-not-exist'
                    }
                }
            }
"""

        then:
        succeeds "help"
    }

    def "fails when required language tool is not available"() {
        when:
        buildFile << """
            model {
                toolChains {
                    ${toolChain.id} {
                        cCompiler.executable = 'does-not-exist'
                    }
                }
            }
"""
        fails "mainExecutable"

        then:
        failure.assertHasDescription("Execution failed for task ':compileMainExecutableMainC'.")
        failure.assertThatCause(Matchers.startsWith("Could not find C compiler 'does-not-exist'"))
    }

    def "fails when required linker tool is not available"() {
        when:
        buildFile << """
            model {
                toolChains {
                    ${toolChain.id} {
                        linker.executable = 'does-not-exist'
                    }
                }
            }
"""
        fails "mainExecutable"

        then:
        failure.assertHasDescription("Execution failed for task ':linkMainExecutable'.")
        failure.assertThatCause(Matchers.startsWith("Could not find Linker 'does-not-exist'"))
    }
}
