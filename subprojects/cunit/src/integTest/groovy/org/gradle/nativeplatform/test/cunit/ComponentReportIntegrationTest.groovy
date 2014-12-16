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
package org.gradle.nativeplatform.test.cunit

import org.gradle.api.reporting.components.AbstractComponentReportIntegrationTest
import org.gradle.nativeplatform.platform.internal.NativePlatforms

class ComponentReportIntegrationTest extends AbstractComponentReportIntegrationTest {
    private String currentNative = NativePlatforms.defaultPlatformName

    def "shows details of native C executable with test suite"() {
        given:
        buildFile << """
plugins {
    id 'c'
    id 'cunit'
}

model {
    toolChains {
        ${toolChain.buildScriptConfig}
    }
    components {
        someExe(NativeExecutableSpec)
    }
}
"""
        when:
        succeeds "components"

        then:
        outputMatches output, """
Native executable 'someExe'
---------------------------

Source sets
    C source 'someExe:c'
        src/someExe/c

Binaries
    Executable 'someExe:executable'
        build using task: :someExeExecutable
        install using task: :installSomeExeExecutable
        platform: $currentNative
        build type: debug
        flavor: default
        tool chain: Tool chain 'clang' (Clang)
        executable file: build/binaries/someExeExecutable/someExe

Cunit test suite 'someExeTest'
------------------------------

Source sets
    C source 'someExeTest:c'
        src/someExeTest/c
    C source 'someExeTest:cunitLauncher'
        build/src/someExeTest/cunitLauncher/c

Binaries
    C unit exe 'someExeTest:cUnitExe'
        build using task: :someExeTestCUnitExe
        run using task: :runSomeExeTestCUnitExe
        platform: $currentNative
        build type: debug
        flavor: default
        tool chain: Tool chain 'clang' (Clang)
        executable file: build/binaries/someExeTestCUnitExe/someExeTest
"""
    }
}
