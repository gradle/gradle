/*
 * Copyright 2015 the original author or authors.
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
package org.gradle.nativeplatform.test.googletest

import org.gradle.api.reporting.components.AbstractNativeComponentReportIntegrationTest
import org.gradle.nativeplatform.fixtures.NativePlatformsTestFixture
import org.gradle.nativeplatform.fixtures.RequiresInstalledToolChain

class TestingNativeComponentReportIntegrationTest extends AbstractNativeComponentReportIntegrationTest {
    private String currentNative = NativePlatformsTestFixture.defaultPlatformName

    @RequiresInstalledToolChain
    def "shows details of native C++ executable with test suite"() {
        given:
        buildFile << """
plugins {
    id 'cpp'
    id 'google-test'
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
    C++ source 'someExe:cpp'
        srcDir: src/someExe/cpp

Binaries
    Executable 'someExe:executable'
        build using task: :someExeExecutable
        install using task: :installSomeExeExecutable
        buildType: build type 'debug'
        flavor: flavor 'default'
        targetPlatform: platform '$currentNative'
        tool chain: Tool chain 'clang' (Clang)
        executable file: build/binaries/someExeExecutable/someExe

GoogleTest test suite 'someExeTest'
-----------------------------------

Source sets
    C++ source 'someExeTest:cpp'
        srcDir: src/someExeTest/cpp

Binaries
    Google test exe 'someExeTest:googleTestExe'
        build using task: :someExeTestGoogleTestExe
        run using task: :runSomeExeTestGoogleTestExe
        buildType: build type 'debug'
        flavor: flavor 'default'
        targetPlatform: platform '$currentNative'
        tool chain: Tool chain 'clang' (Clang)
        executable file: build/binaries/someExeTestGoogleTestExe/someExeTest
"""
    }
}
