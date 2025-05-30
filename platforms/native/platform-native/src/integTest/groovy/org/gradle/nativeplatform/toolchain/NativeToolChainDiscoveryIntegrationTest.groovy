/*
 * Copyright 2013 the original author or authors.
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
import org.gradle.nativeplatform.fixtures.app.CppCompilerDetectingTestApp

/**
 * Test that each available tool chain can be discovered and used without configuration, assuming it is in the path.
 */
class NativeToolChainDiscoveryIntegrationTest extends AbstractInstalledToolChainIntegrationSpec {

    def helloWorldApp = new CppCompilerDetectingTestApp()

    def setup() {
        // Discard init script content generated by superclass
        initScript.text = ""
    }

    def "can discover tool chain in environment"() {
        given:
        toolChain.initialiseEnvironment()

        and:
        buildFile << """
apply plugin: 'cpp'

    toolChains {
        tc(${toolChain.implementationClass}) {
            // For software model builds, windows defaults to 32-bit target, so if we discard the toolchain init script,
            // we need to reapply the 32-bit platform config for cygwin64 and mingw64
            ${toolChain.platformSpecificToolChainConfiguration()}
        }
    }
model {
    components {
        main(NativeExecutableSpec)
    }
}
        """

        and:
        helloWorldApp.writeSources(file("src/main"))

        when:
        run "mainExecutable"

        then:
        def mainExecutable = executable("build/exe/main/main")
        mainExecutable.assertExists()
        mainExecutable.exec().out == helloWorldApp.expectedOutput(toolChain)

        cleanup:
        toolChain.resetEnvironment()
    }

    def "uses correct tool chain when explicitly configured"() {
        given:
        buildFile << """
apply plugin: 'cpp'

    toolChains {
        ${toolChain.buildScriptConfig}
    }
model {
    components {
        main(NativeExecutableSpec)
    }
}
        """

        and:
        helloWorldApp.writeSources(file("src/main"))

        when:
        run "mainExecutable"

        then:
        def mainExecutable = executable("build/exe/main/main")
        mainExecutable.assertExists()
        mainExecutable.exec().out == helloWorldApp.expectedOutput(toolChain)
    }

}
