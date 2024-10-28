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

package org.gradle.api.reporting.components

import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.nativeplatform.fixtures.RequiresInstalledToolChain

class DiagnosticsComponentReportIntegrationTest extends AbstractNativeComponentReportIntegrationTest {

    @RequiresInstalledToolChain
    @ToBeFixedForConfigurationCache(because = ":components")
    def "informs the user when project has no components defined"() {
        when:
        executer.withArgument("--no-problems-report")
        expectTaskGetProjectDeprecations()
        succeeds "components"

        then:
        outputMatches """
No components defined for this project.
"""
    }

    @RequiresInstalledToolChain
    @ToBeFixedForConfigurationCache(because = ":components")
    def "shows details of multiple components"() {
        given:
        buildFile << """
plugins {
    id 'cpp'
    id 'c'
}

model {
    toolChains {
        ${toolChain.buildScriptConfig}
    }
    components {
        nativeLib(NativeLibrarySpec)
    }
}
"""
        when:
        executer.withArgument("--no-problems-report")
        expectTaskGetProjectDeprecations()
        succeeds "components"

        then:
        outputMatches """
Native library 'nativeLib'
--------------------------

Source sets
    C source 'nativeLib:c'
        srcDir: src/nativeLib/c
    C++ source 'nativeLib:cpp'
        srcDir: src/nativeLib/cpp

Binaries
    Shared library 'nativeLib:sharedLibrary'
        build using task: :nativeLibSharedLibrary
        build type: build type 'debug'
        flavor: flavor 'default'
        target platform: platform '$currentNative'
        tool chain: Tool chain 'clang' (Clang)
        shared library file: build/libs/nativeLib/shared/libnativeLib.dylib
    Static library 'nativeLib:staticLibrary'
        build using task: :nativeLibStaticLibrary
        build type: build type 'debug'
        flavor: flavor 'default'
        target platform: platform '$currentNative'
        tool chain: Tool chain 'clang' (Clang)
        static library file: build/libs/nativeLib/static/libnativeLib.a
"""
    }

}
