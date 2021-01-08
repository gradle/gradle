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

import org.gradle.api.reporting.components.AbstractNativeComponentReportIntegrationTest
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.nativeplatform.fixtures.RequiresInstalledToolChain

class CUnitComponentReportIntegrationTest extends AbstractNativeComponentReportIntegrationTest {
    @RequiresInstalledToolChain
    def "fails with a reasonable error if component under test is not specified"() {
        given:
        buildFile << """
plugins {
    id 'c'
    id 'cunit-test-suite'
}

model {
    toolChains {
        ${toolChain.buildScriptConfig}
    }
    components {
        someExe(NativeExecutableSpec)
    }
    testSuites {
        someExeTest(CUnitTestSuiteSpec)
    }
}
"""
        when:
        fails "components"

        then:
        failure.assertHasCause "Test suite 'someExeTest' doesn't declare component under test. Please specify it with `testing \$.components.myComponent`."
    }

    @RequiresInstalledToolChain
    @ToBeFixedForConfigurationCache(because = ":components")
    def "shows details of native C executable with test suite"() {
        given:
        buildFile << """
plugins {
    id 'c'
    id 'cunit-test-suite'
}

model {
    toolChains {
        ${toolChain.buildScriptConfig}
    }
    components {
        someExe(NativeExecutableSpec)
    }
    testSuites {
        someExeTest(CUnitTestSuiteSpec) {
            testing \$.components.someExe
        }
    }
}
"""
        when:
        succeeds "components"

        then:
        outputMatches """
Native executable 'someExe'
---------------------------

Source sets
    C source 'someExe:c'
        srcDir: src/someExe/c

Binaries
    Executable 'someExe:executable'
        build using task: :someExeExecutable
        install using task: :installSomeExeExecutable
        build type: build type 'debug'
        flavor: flavor 'default'
        target platform: platform '$currentNative'
        tool chain: Tool chain 'clang' (Clang)
        executable file: build/exe/someExe/someExe

Cunit test suite 'someExeTest'
------------------------------

Source sets
    C source 'someExeTest:c'
        srcDir: src/someExeTest/c
    C source 'someExeTest:cunitLauncher'
        srcDir: build/src/someExeTest/cunitLauncher/c

Binaries
    C unit exe 'someExeTest:cUnitExe'
        build using task: :someExeTestCUnitExe
        install using task: :installSomeExeTestCUnitExe
        run using task: :runSomeExeTestCUnitExe
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
