/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.buildinit.plugins

import org.gradle.buildinit.plugins.fixtures.ScriptDslFixture
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.nativeplatform.fixtures.AvailableToolChains
import org.gradle.nativeplatform.fixtures.AvailableToolChains.InstalledToolChain
import org.gradle.nativeplatform.fixtures.RequiresInstalledToolChain
import org.gradle.nativeplatform.fixtures.SharedLibraryFixture
import org.gradle.nativeplatform.fixtures.ToolChainRequirement
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition

@RequiresInstalledToolChain(ToolChainRequirement.SWIFTC)
@Requires(TestPrecondition.NOT_MAC_OS_X_M1) // M1 Macs need modern Xcode to compile aarch64 binaries
class SwiftLibraryInitIntegrationTest extends AbstractInitIntegrationSpec {

    public static final String SAMPLE_LIBRARY_CLASS = "Hello.swift"
    public static final String SAMPLE_LIBRARY_TEST_CLASS = "HelloTests.swift"
    public static final String LINUX_MAIN_DOT_SWIFT = "LinuxMain.swift"

    private final InstalledToolChain swiftcToolChain = AvailableToolChains.getToolChain(ToolChainRequirement.SWIFTC)

    def setup() {
        swiftcToolChain.initialiseEnvironment()
    }

    def cleanup() {
        swiftcToolChain.resetEnvironment()
    }

    @Override
    String subprojectName() { 'lib' }

    @ToBeFixedForConfigurationCache(because = "swift-library plugin")
    def "creates sample source if no source present with #scriptDsl build scripts"() {
        when:
        run('init', '--type', 'swift-library', '--dsl', scriptDsl.id)

        then:
        subprojectDir.file("src/main/swift").assertHasDescendants(SAMPLE_LIBRARY_CLASS)
        subprojectDir.file("src/test/swift").assertHasDescendants(SAMPLE_LIBRARY_TEST_CLASS, LINUX_MAIN_DOT_SWIFT)

        and:
        subprojectDir.file("src/test/swift/${SAMPLE_LIBRARY_TEST_CLASS}").text.contains("@testable import Lib")


        and:
        commonFilesGenerated(scriptDsl)

        and:
        succeeds("build")

        and:
        library("${subprojectName()}/build/lib/main/debug/Lib").assertExists()

        where:
        scriptDsl << ScriptDslFixture.SCRIPT_DSLS
    }

    @ToBeFixedForConfigurationCache(because = "swift-library plugin")
    def "creates sample source if project name is specified with #scriptDsl build scripts"() {
        when:
        run('init', '--type', 'swift-library', '--project-name', 'greeting', '--dsl', scriptDsl.id)

        then:
        subprojectDir.file("src/main/swift").assertHasDescendants(SAMPLE_LIBRARY_CLASS)
        subprojectDir.file("src/test/swift").assertHasDescendants(SAMPLE_LIBRARY_TEST_CLASS, LINUX_MAIN_DOT_SWIFT)

        and:
        subprojectDir.file("src/test/swift/${SAMPLE_LIBRARY_TEST_CLASS}").text.contains("@testable import Lib")


        and:
        commonFilesGenerated(scriptDsl)

        and:
        succeeds("build")

        and:
        library("${subprojectName()}/build/lib/main/debug/Lib").assertExists()

        where:
        scriptDsl << ScriptDslFixture.SCRIPT_DSLS
    }


    @ToBeFixedForConfigurationCache(because = "swift-library plugin")
    def "source generation is skipped when cpp sources detected with #scriptDsl build scripts"() {
        setup:
        subprojectDir.file("src/main/swift/hola.swift") << """
            public func hola() -> String {
                return "Hola, Mundo!"
            }
        """
        subprojectDir.file("src/test/swift/HolaTests.swift") << """
            import XCTest
            @testable import Lib
            class HolaTests: XCTestCase {
                public static var allTests = [
                    ("testGreeting", testGreeting),
                ]

                func testGreeting() {
                    XCTAssertEqual("Hola, Mundo!", hola())
                }
            }
        """
        subprojectDir.file("src/test/swift/${LINUX_MAIN_DOT_SWIFT}") << """
            import XCTest

            XCTMain([testCase(HolaTests.allTests)])
        """

        when:
        run('init', '--type', 'swift-library', '--project-name', 'hello', '--dsl', scriptDsl.id)

        then:
        subprojectDir.file("src/main/swift").assertHasDescendants("hola.swift")
        subprojectDir.file("src/test/swift").assertHasDescendants("HolaTests.swift", LINUX_MAIN_DOT_SWIFT)
        dslFixtureFor(scriptDsl).assertGradleFilesGenerated()

        and:
        subprojectDir.file("src/main/swift/${SAMPLE_LIBRARY_CLASS}").assertDoesNotExist()
        subprojectDir.file("src/test/swift/${SAMPLE_LIBRARY_TEST_CLASS}").assertDoesNotExist()
        subprojectDir.file("src/test/swift/${LINUX_MAIN_DOT_SWIFT}").text.contains("HolaTests.allTests")

        when:
        run("build")

        then:
        executed(":lib:test")

        and:
        library("${subprojectName()}/build/lib/main/debug/Lib").assertExists()

        where:
        scriptDsl << ScriptDslFixture.SCRIPT_DSLS
    }

    SharedLibraryFixture library(String path) {
        AvailableToolChains.getToolChain(ToolChainRequirement.SWIFTC).sharedLibrary(targetDir.file(path))
    }
}
