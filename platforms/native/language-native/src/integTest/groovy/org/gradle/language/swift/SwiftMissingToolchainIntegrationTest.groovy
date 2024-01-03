/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.language.swift

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.nativeplatform.fixtures.HostPlatform
import org.gradle.nativeplatform.fixtures.app.SwiftApp

class SwiftMissingToolchainIntegrationTest extends AbstractIntegrationSpec implements HostPlatform {
    def "user receives reasonable error message when no tool chains are available"() {
        given:
        buildFile << """
            apply plugin: 'swift-application'
            model {
                toolChains {
                    withType(Swiftc) {
                        path(file('swift-bin'))
                    }
                }
            }
"""
        new SwiftApp().writeToProject(testDirectory)

        when:
        succeeds("help")

        then:
        noExceptionThrown()

        when:
        fails("assemble")

        then:
        failure.assertHasDescription("Execution failed for task ':compileDebugSwift'.")
        failure.assertHasCause("""No tool chain is available to build Swift for host operating system '${osName}' architecture '${archName}':
  - Tool chain 'swiftc' (Swift Compiler):
      - Could not find Swift compiler 'swiftc'. Searched in:
          - ${file('swift-bin')}""")
    }
}
