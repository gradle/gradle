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

import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.internal.os.OperatingSystem
import org.gradle.nativeplatform.fixtures.AbstractInstalledToolChainIntegrationSpec
import org.gradle.nativeplatform.fixtures.RequiresInstalledToolChain
import org.gradle.nativeplatform.fixtures.app.CHelloWorldApp

import static org.gradle.nativeplatform.fixtures.ToolChainRequirement.GCC_COMPATIBLE

@RequiresInstalledToolChain(GCC_COMPATIBLE)
class GccToolChainCrossCompilationIntegrationTest extends AbstractInstalledToolChainIntegrationSpec {
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

    @ToBeFixedForConfigurationCache
    def "uses naming scheme of target platform when cross-compiling"() {
        // TODO - use linux as the target when running on windows
        buildFile << """
model {
    platforms {
        custom {
            operatingSystem 'windows'
        }
    }
    toolChains {
        ${toolChain.id} {
            target('custom') {
                if (${!OperatingSystem.current().windows}) {
                    cCompiler.withArguments { it << '-fPIC' }
                }
            }
        }
    }
    components {
        all {
            targetPlatform "custom"
        }
    }
}
"""

        when:
        run 'mainExe'

        then:
        file(OperatingSystem.WINDOWS.getStaticLibraryName("build/libs/hello/static/hello")).file
        file(OperatingSystem.WINDOWS.getExecutableName("build/exe/main/main")).file

        when:
        run 'helloSharedLib'

        then:
        file(OperatingSystem.WINDOWS.getSharedLibraryName("build/libs/hello/shared/hello")).file
    }
}
