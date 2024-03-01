/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.nativeplatform.platform

import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.internal.os.OperatingSystem
import org.gradle.nativeplatform.fixtures.AbstractInstalledToolChainIntegrationSpec
import org.gradle.nativeplatform.fixtures.RequiresInstalledToolChain
import org.gradle.nativeplatform.fixtures.app.PlatformDetectingTestApp
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions

import static org.gradle.nativeplatform.fixtures.ToolChainRequirement.GCC_COMPATIBLE

@Requires(UnitTestPreconditions.Unix)
@RequiresInstalledToolChain(GCC_COMPATIBLE)
class InstallExecutableIntegrationTest extends AbstractInstalledToolChainIntegrationSpec {
    def testApp = new PlatformDetectingTestApp()
    def os = OperatingSystem.current()

    def setup() {
        buildFile << """
plugins {
    id 'cpp'
}
model {
    components {
        main(NativeExecutableSpec)
    }
}
"""

        testApp.writeSources(file("src/main"))
    }

    @ToBeFixedForConfigurationCache
    def "can create installation for a different os than the current one"() {
        String installOS
        if (os.windows) {
            installOS = "linux"
        } else if (os.linux || os.macOsX) {
            installOS = "windows"
        } else {
            throw new AssertionError("Unexpected operating system")
        }

        when:
        buildFile << """
model {
    platforms {
        windows {
            operatingSystem "windows"
            architecture "x86"
        }
        linux {
            operatingSystem "linux"
            architecture "x86"
        }
    }
    toolChains {
        ${toolChain.id} {
            target('${installOS}') {
                if (${!OperatingSystem.current().windows}) {
                    cCompiler.withArguments { it << '-fPIC' }
                }
            }
        }
    }
    components.main {
        targetPlatform "$installOS"
    }
}
        """
        and:
        succeeds "install"

        then:
        installation("build/install/main", OperatingSystem.forName(installOS)).assertInstalled()
    }
}
