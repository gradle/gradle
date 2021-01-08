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

package org.gradle.nativeplatform.toolchain


import org.gradle.nativeplatform.fixtures.AbstractInstalledToolChainIntegrationSpec
import org.gradle.nativeplatform.fixtures.RequiresInstalledToolChain
import org.gradle.nativeplatform.fixtures.ToolChainRequirement
import org.gradle.nativeplatform.fixtures.app.SwiftApp

@RequiresInstalledToolChain(ToolChainRequirement.SWIFTC)
class SwiftToolChainDiscoveryIntegrationTest extends AbstractInstalledToolChainIntegrationSpec {

    def setup() {
        buildFile << """
            model {
                toolChains {
                    ${toolChain.buildScriptConfig}
                }
            }
        """
    }

    def "toolchain is not available when the discovered swift executable does not return sensible output"() {
        def scriptDir = testDirectory.createDir("scriptDir")
        def script = scriptDir.createFile("swiftc")
        script << """
            #!/bin/sh
            echo "foo"
        """
        script.executable = true

        def app = new SwiftApp()
        settingsFile << "rootProject.name = '${app.projectName}'"
        app.writeToProject(testDirectory)

        when:
        buildFile << """
            model {
                toolChains {
                    ${toolChain.id} {
                        path.add(0, file('${scriptDir.toURI()}'))
                    }
                }
            }
            apply plugin: 'swift-application'
        """

        then:
        fails('assemble')

        and:
        failure.assertHasDescription("Execution failed for task ':compileDebugSwift'.")
        failure.assertHasCause("""No tool chain is available to build Swift for host operating system '${osName}' architecture '${archName}':
  - Tool chain '${toolChain.id}' (Swift Compiler):
      - Could not determine SwiftC metadata: swiftc produced unexpected output.""")
    }
}
