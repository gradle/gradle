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

package org.gradle.nativeplatform.toolchain

import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.nativeplatform.fixtures.AbstractInstalledToolChainIntegrationSpec
import org.gradle.nativeplatform.fixtures.NativePlatformsTestFixture
import org.gradle.nativeplatform.fixtures.RequiresInstalledToolChain
import org.gradle.nativeplatform.fixtures.app.CHelloWorldApp
import org.hamcrest.CoreMatchers
import spock.lang.IgnoreIf

import static org.gradle.nativeplatform.fixtures.ToolChainRequirement.GCC_COMPATIBLE

@RequiresInstalledToolChain(GCC_COMPATIBLE)
class GccToolChainDiscoveryIntegrationTest extends AbstractInstalledToolChainIntegrationSpec {
    def helloWorldApp = new CHelloWorldApp()

    def setup() {
        buildFile << """
apply plugin: 'c'

model {
    toolChains {
        ${toolChain.buildScriptConfig}
    }
    components {
        main(NativeExecutableSpec) {
            binaries.all {
                lib library: 'hello', linkage: 'static'
            }
        }
        hello(NativeLibrarySpec)
    }
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
            eachPlatform {
                cppCompiler.executable = 'does-not-exist'
            }
        }
    }
}
"""
        succeeds "mainExecutable"

        then:
        executable("build/exe/main/main").exec().out == helloWorldApp.englishOutput
    }

    def "does not break when compiler not available and not building"() {
        when:
        buildFile << """
model {
    toolChains {
        ${toolChain.id} {
            eachPlatform {
                cCompiler.executable = 'does-not-exist'
                cppCompiler.executable = 'does-not-exist'
                linker.executable = 'does-not-exist'
            }
        }
    }
}
"""

        then:
        succeeds "help"
    }

    def "tool chain is not available when no tools are available"() {
        when:
        buildFile << """
model {
    toolChains {
        ${toolChain.id} {
            eachPlatform {
                assembler.executable = 'does-not-exist'
                cCompiler.executable = 'does-not-exist'
                cppCompiler.executable = 'does-not-exist'
                linker.executable = 'does-not-exist'
                staticLibArchiver.executable = 'does-not-exist'
                objcCompiler.executable = 'does-not-exist'
                objcppCompiler.executable = 'does-not-exist'
                symbolExtractor.executable = 'does-not-exist'
                stripper.executable = 'does-not-exist'
            }
        }
    }
}
"""
        fails "compileMainExecutableMainC"

        then:
        failure.assertHasDescription("Execution failed for task ':compileMainExecutableMainC'.")
        failure.assertHasCause("""No tool chain is available to build for platform '${NativePlatformsTestFixture.defaultPlatformName}':
  - ${toolChain.instanceDisplayName}:
      - Could not find C compiler 'does-not-exist'""")
    }

    @IgnoreIf({GradleContextualExecuter.parallel})
    def "fails when required language tool is not available but other language tools are available"() {
        when:
        buildFile << """
model {
    toolChains {
        ${toolChain.id} {
            eachPlatform {
                cCompiler.executable = 'does-not-exist'
            }
        }
    }
}
"""
        fails "compileMainExecutableMainC"

        then:
        failure.assertHasDescription("Execution failed for task ':compileMainExecutableMainC'.")
        failure.assertThatCause(CoreMatchers.startsWith("Could not find C compiler 'does-not-exist'"))
    }

    def "fails when required linker tool is not available but language tool is available"() {
        when:
        buildFile << """
model {
    toolChains {
        ${toolChain.id} {
            eachPlatform {
                linker.executable = 'does-not-exist'
            }
        }
    }
}
"""
        fails "mainExecutable"

        then:
        failure.assertHasDescription("Execution failed for task ':linkMainExecutable'.")
        failure.assertThatCause(CoreMatchers.startsWith("Could not find Linker 'does-not-exist'"))
    }
}
