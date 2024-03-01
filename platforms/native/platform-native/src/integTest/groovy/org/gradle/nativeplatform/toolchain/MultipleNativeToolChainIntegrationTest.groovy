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

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.internal.os.OperatingSystem
import org.gradle.nativeplatform.fixtures.AvailableToolChains
import org.gradle.nativeplatform.fixtures.RequiresInstalledToolChain
import org.gradle.nativeplatform.fixtures.ToolChainRequirement
import org.gradle.nativeplatform.fixtures.app.CppCompilerDetectingTestApp
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions
import org.junit.Assume

@RequiresInstalledToolChain
class MultipleNativeToolChainIntegrationTest extends AbstractIntegrationSpec {
    def helloWorld = new CppCompilerDetectingTestApp()

    def setup() {
        buildFile << """
plugins { id 'cpp' }
"""

        helloWorld.writeSources(file("src/main"))
    }

    @Requires(UnitTestPreconditions.CanInstallExecutable)
    @RequiresInstalledToolChain(ToolChainRequirement.GCC)
    @ToBeFixedForConfigurationCache
    def "can build with multiple tool chains"() {
        AvailableToolChains.InstalledToolChain x86ToolChain = OperatingSystem.current().isWindows() ?
                AvailableToolChains.getToolChain(ToolChainRequirement.VISUALCPP) :
                AvailableToolChains.getToolChain(ToolChainRequirement.CLANG)
        AvailableToolChains.InstalledToolChain sparcToolChain = AvailableToolChains.getToolChain(ToolChainRequirement.GCC)

        // This is a Junit class, but works in Spock too.
        Assume.assumeNotNull(x86ToolChain?.buildScriptConfig, sparcToolChain?.buildScriptConfig)

        when:
        buildFile << """
model {
    platforms {
        i386 {
            architecture "i386"
        }
        sparc {
            architecture "sparc"
        }
    }
    toolChains {
        ${x86ToolChain.buildScriptConfig}
        ${sparcToolChain.buildScriptConfig}
        ${sparcToolChain.id} {
            target("sparc")
        }
    }
    components {
        main(NativeExecutableSpec) {
            targetPlatform "i386"
            targetPlatform "sparc"
        }
    }
}
"""

        then:
        succeeds 'mainI386Executable', 'mainSparcExecutable'

        and:
        def i386Exe = x86ToolChain.executable(file("build/exe/main/i386/main"))
        assert i386Exe.exec().out == helloWorld.expectedOutput(x86ToolChain)
        def sparcExe = sparcToolChain.executable(file("build/exe/main/sparc/main"))
        assert sparcExe.exec().out == helloWorld.expectedOutput(sparcToolChain)
    }

    def checkInstall(String path, AvailableToolChains.InstalledToolChain toolChain) {
        def executable = file(OperatingSystem.current().getScriptName(path))
        executable.assertExists()
        assert executable.execute([], toolChain.runtimeEnv).out == helloWorld.expectedOutput(toolChain)
        return true
    }
}
