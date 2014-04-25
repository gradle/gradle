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

package org.gradle.nativebinaries.toolchain;

import org.gradle.nativebinaries.language.cpp.fixtures.AbstractInstalledToolChainIntegrationSpec
import org.gradle.nativebinaries.language.cpp.fixtures.app.CppHelloWorldApp

public class CommonToolchainCustomizationIntegTest extends AbstractInstalledToolChainIntegrationSpec {

    def helloWorldApp = new CppHelloWorldApp()

    def setup() {
        buildFile << """
        apply plugin: 'cpp'

        executables {
            main {}
        }
        """

        helloWorldApp.executable.writeSources(file("src/main"))
    }

    def "can customize tool arguments"() {
        when:
        helloWorldApp.writeSources(file("src/main"))
        buildFile << """
        apply plugin: 'cpp'
        model {
            toolChains {
                ${AbstractInstalledToolChainIntegrationSpec.toolChain.id} {
                    cppCompiler.withArguments { args ->
                            args << "-DFRENCH"
                    }
                }
            }
        }
        """
        and:
        succeeds "mainExecutable"
        then:
        executable("build/binaries/mainExecutable/main").exec().out == helloWorldApp.frenchOutput
    }
}
