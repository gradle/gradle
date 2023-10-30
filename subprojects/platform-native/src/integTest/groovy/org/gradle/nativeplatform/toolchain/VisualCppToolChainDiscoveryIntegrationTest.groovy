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

import org.gradle.nativeplatform.fixtures.AbstractInstalledToolChainIntegrationSpec
import org.gradle.nativeplatform.fixtures.NativePlatformsTestFixture
import org.gradle.nativeplatform.fixtures.RequiresInstalledToolChain
import org.gradle.nativeplatform.fixtures.ToolChainRequirement
import org.gradle.nativeplatform.fixtures.app.CHelloWorldApp

@RequiresInstalledToolChain(ToolChainRequirement.VISUALCPP)
class VisualCppToolChainDiscoveryIntegrationTest extends AbstractInstalledToolChainIntegrationSpec {
    def helloWorldApp = new CHelloWorldApp()

    def setup() {
        buildFile << """
apply plugin: 'c'

model {
    toolChains {
        ${toolChain.buildScriptConfig}
    }
    components {
        main(NativeExecutableSpec)
    }
}
"""

        helloWorldApp.executable.writeSources(file("src/main"))
        helloWorldApp.library.writeSources(file("src/main"))
    }

    def "tool chain is not available when visual studio install is not available"() {
        when:
        buildFile << """
model {
    toolChains {
        ${toolChain.id} {
            installDir "does-not-exist"
        }
    }
}
"""

        fails "mainExecutable"

        then:
        failure.assertHasDescription("Execution failed for task ':compileMainExecutableMainC'.")
        failure.assertHasCause("""No tool chain is available to build for platform '${NativePlatformsTestFixture.defaultPlatformName}':
  - ${toolChain.instanceDisplayName}:
      - The specified installation directory '${file('does-not-exist')}' does not appear to contain a Visual Studio installation.""")
    }

    def "tool chain is not available when SDK install is not available"() {
        when:
        buildFile << """
model {
    toolChains {
        ${toolChain.id} {
            windowsSdkDir "does-not-exist"
        }
    }
}
"""
        fails "mainExecutable"

        then:
        failure.assertHasDescription("Execution failed for task ':compileMainExecutableMainC'.")
        failure.assertHasCause("""No tool chain is available to build for platform '${NativePlatformsTestFixture.defaultPlatformName}':
  - ${toolChain.instanceDisplayName}:
      - The specified installation directory '${file('does-not-exist')}' does not appear to contain a Windows SDK installation.""")
    }
}
