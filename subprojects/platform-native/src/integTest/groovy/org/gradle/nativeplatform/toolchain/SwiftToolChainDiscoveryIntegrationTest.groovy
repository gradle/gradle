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
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import org.hamcrest.Matchers

@Requires(TestPrecondition.SWIFT_SUPPORT)
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

    def "toolchain is not available when swift executable is not found"() {
        when:
        buildFile << """
            model {
                toolChains {
                    ${toolChain.id} {
                        path.clear()
                    }
                }
            }
            apply plugin: 'swift-executable'
        """
        executer.withEnvironmentVars(PATH: "")

        then:
        fails('assemble')

        and:
        failure.assertHasDescription("Execution failed for task ':compileDebugSwift'.")
        failure.assertThatCause(Matchers.startsWith("No tool chain is available to build for platform 'current'"))
        failure.assertThatCause(Matchers.containsString("- Tool chain 'swiftc' (Swift Compiler): Could not find Swift compiler 'swiftc' in system path."))
    }

    def "toolchain is not available when the discovered swift executable does not return sensible output"() {
        def scriptDir = testDirectory.createDir("scriptDir")
        def script = scriptDir.createFile("swiftc")
        script << """
            #!/bin/sh
            echo "foo"
        """
        script.executable = true

        when:
        buildFile << """
            model {
                toolChains {
                    ${toolChain.id} {
                        path.add(0, file('${scriptDir.toURI()}'))
                    }
                }
            }
            apply plugin: 'swift-executable'
        """

        then:
        fails('assemble')

        and:
        failure.assertHasDescription("Execution failed for task ':compileDebugSwift'.")
        failure.assertThatCause(Matchers.startsWith("No tool chain is available to build for platform 'current'"))
        failure.assertThatCause(Matchers.containsString("- Tool chain 'swiftc' (Swift Compiler): Could not determine SwiftC metadata: swiftc produced unexpected output."))
    }
}
