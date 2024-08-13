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
import org.gradle.nativeplatform.fixtures.app.CppHelloWorldApp

public class CommonToolchainCustomizationIntegTest extends AbstractInstalledToolChainIntegrationSpec {

    def helloWorldApp = new CppHelloWorldApp()

    def "can add action to tool chain that modifies tool arguments prior to execution"() {
        when:
        helloWorldApp.executable.writeSources(file("src/main"))
        helloWorldApp.writeSources(file("src/main"))
        buildFile << """
apply plugin: 'cpp'

model {
    toolChains {
        ${toolChain.id} {
            eachPlatform {
                cppCompiler.withArguments { args ->
                    Collections.replaceAll(args, "CUSTOM", "-DFRENCH")
                }
                linker.withArguments { args ->
                    args.remove "CUSTOM"
                }
            }
        }
    }
    components {
        main(NativeExecutableSpec) {
            binaries.all {
                cppCompiler.args "CUSTOM"
                linker.args "CUSTOM"
            }
        }
    }
}
        """
        and:
        succeeds "mainExecutable"
        then:
        executable("build/exe/main/main").exec().out == helloWorldApp.frenchOutput
    }
}
