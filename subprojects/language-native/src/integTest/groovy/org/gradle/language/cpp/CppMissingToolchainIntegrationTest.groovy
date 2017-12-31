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

package org.gradle.language.cpp

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.nativeplatform.fixtures.app.CppApp


class CppMissingToolchainIntegrationTest extends AbstractIntegrationSpec {
    def "user receives reasonable error message when no tool chains are available"() {
        given:
        buildFile << """
            apply plugin: 'cpp-application'
            model {
                toolChains {
                    withType(Gcc) {
                        path(file('gcc-bin'))
                    }
                    withType(Clang) {
                        path(file('clang-bin'))
                    }
                    withType(VisualCpp) {
                        installDir = file('vs-install')
                        windowsSdkDir = file('sdk-install')
                    }
                }
            }
"""
        new CppApp().writeToProject(testDirectory)

        when:
        fails("assemble")

        then:
        failure.assertHasDescription("Execution failed for task ':compileDebugCpp'.")
        failure.assertHasCause("""No tool chain is available to build for platform 'current':
  - Tool chain 'visualCpp' (Visual Studio): Visual Studio is not available on this operating system.
  - Tool chain 'gcc' (GNU GCC):
      - Could not find C compiler 'gcc'. Searched in: ${file('gcc-bin')}
  - Tool chain 'clang' (Clang):
      - Could not find C compiler 'clang'. Searched in: ${file('clang-bin')}""")
    }
}
