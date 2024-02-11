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
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.internal.os.OperatingSystem
import org.gradle.nativeplatform.fixtures.AvailableToolChains
import org.gradle.nativeplatform.fixtures.HostPlatform
import org.gradle.nativeplatform.fixtures.ToolChainRequirement
import org.gradle.nativeplatform.fixtures.app.CppApp
import org.gradle.nativeplatform.fixtures.app.CppCompilerDetectingTestApp
import org.junit.Assume

class CppMissingToolchainIntegrationTest extends AbstractIntegrationSpec implements HostPlatform {
    @ToBeFixedForConfigurationCache
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
        succeeds("tasks")

        then:
        noExceptionThrown()

        when:
        fails("assemble")

        then:
        failure.assertHasDescription("Execution failed for task ':compileDebugCpp'.")
        if (OperatingSystem.current().windows) {
            failure.assertHasCause("""No tool chain is available to build C++ for host operating system '${osName}' architecture '${archName}':
  - Tool chain 'visualCpp' (Visual Studio):
      - The specified installation directory '${file('vs-install')}' does not appear to contain a Visual Studio installation.
  - Tool chain 'gcc' (GNU GCC):
      - Could not find C++ compiler 'g++'. Searched in:
          - ${file('gcc-bin')}
  - Tool chain 'clang' (Clang):
      - Could not find C++ compiler 'clang++'. Searched in:
          - ${file('clang-bin')}""")
        } else {
            failure.assertHasCause("""No tool chain is available to build C++ for host operating system '${osName}' architecture '${archName}':
  - Tool chain 'visualCpp' (Visual Studio):
      - Visual Studio is not available on this operating system.
  - Tool chain 'gcc' (GNU GCC):
      - Could not find C++ compiler 'g++'. Searched in:
          - ${file('gcc-bin')}
  - Tool chain 'clang' (Clang):
      - Could not find C++ compiler 'clang++'. Searched in:
          - ${file('clang-bin')}""")
        }
    }

    @ToBeFixedForConfigurationCache
    def "can build with Clang when gcc is available but g++ is not available"() {
        def gcc = AvailableToolChains.getToolChain(ToolChainRequirement.GCC)
        Assume.assumeTrue(gcc != null)
        def clang = AvailableToolChains.getToolChain(ToolChainRequirement.CLANG)
        Assume.assumeTrue(clang != null)

        buildFile << """
            apply plugin: 'cpp-application'
            model {
                toolChains {
                    withType(Gcc) {
                        eachPlatform {
                            cppCompiler.executable = 'does-not-exist'
                        }
                    }
                }
            }
"""

        def app = new CppCompilerDetectingTestApp()
        app.writeToProject(testDirectory)

        when:
        run("assemble")

        then:
        installation("build/install/main/debug").exec().out == app.expectedOutput(clang)
    }
}
