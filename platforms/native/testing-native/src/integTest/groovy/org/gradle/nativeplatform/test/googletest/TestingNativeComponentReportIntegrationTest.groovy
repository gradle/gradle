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
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.nativeplatform.fixtures.RequiresInstalledToolChain

class TestingNativeComponentReportIntegrationTest extends AbstractNativeComponentReportIntegrationTest {
    @RequiresInstalledToolChain
    @ToBeFixedForConfigurationCache(because = ":components")
    def "shows details of native C++ executable with test suite"() {
        given:
        buildFile << """
plugins {
    id 'cpp'
    id 'google-test-test-suite'
}

model {
    toolChains {
        ${toolChain.buildScriptConfig}
    }
    components {
        someExe(NativeExecutableSpec)
    }
    testSuites {
        someExeTest(GoogleTestTestSuiteSpec) {
            testing \$.components.someExe
        }
    }
}
"""
        when:
        executer.withArgument("--no-problems-report")
        expectTaskGetProjectDeprecations()
        succeeds "components"

        then:
        outputMatches """
Native executable 'someExe'
---------------------------

Source sets
    C++ source 'someExe:cpp'
        srcDir: src/someExe/cpp

Binaries
    Executable 'someExe:executable'
        build using task: :someExeExecutable
        install using task: :installSomeExeExecutable
        build type: build type 'debug'
        flavor: flavor 'default'
        target platform: platform '$currentNative'
        tool chain: Tool chain 'clang' (Clang)
        executable file: build/exe/someExe/someExe

Google test suite 'someExeTest'
-------------------------------

Source sets
    C++ source 'someExeTest:cpp'
        srcDir: src/someExeTest/cpp

Binaries
    Google test exe 'someExeTest:googleTestExe'
        build using task: :someExeTestGoogleTestExe
        install using task: :installSomeExeTestGoogleTestExe
        run using task: :runSomeExeTestGoogleTestExe
        build type: build type 'debug'
        flavor: flavor 'default'
        target platform: platform '$currentNative'
        component under test: native executable 'someExe'
        binary under test: executable 'someExe:executable'
        tool chain: Tool chain 'clang' (Clang)
        executable file: build/exe/someExeTest/someExeTest
"""
    }
}
