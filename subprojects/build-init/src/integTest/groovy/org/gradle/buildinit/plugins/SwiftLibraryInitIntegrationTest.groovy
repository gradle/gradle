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
import org.gradle.integtests.fixtures.ToBeFixedForInstantExecution
import org.gradle.nativeplatform.fixtures.AvailableToolChains
import org.gradle.nativeplatform.fixtures.AvailableToolChains.InstalledToolChain
import org.gradle.nativeplatform.fixtures.RequiresInstalledToolChain
import org.gradle.nativeplatform.fixtures.SharedLibraryFixture
import org.gradle.nativeplatform.fixtures.ToolChainRequirement
import spock.lang.Unroll

@RequiresInstalledToolChain(ToolChainRequirement.SWIFTC)
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

    @Unroll
    @ToBeFixedForInstantExecution
    def "creates sample source if no source present with #scriptDsl build scripts"() {
        when:
        run('init', '--type', 'swift-library', '--dsl', scriptDsl.id)

        then:
        targetDir.file("src/main/swift").assertHasDescendants(SAMPLE_LIBRARY_CLASS)
        targetDir.file("src/test/swift").assertHasDescendants(SAMPLE_LIBRARY_TEST_CLASS, LINUX_MAIN_DOT_SWIFT)

        and:
        targetDir.file("src/test/swift/${SAMPLE_LIBRARY_TEST_CLASS}").text.contains("@testable import SomeThing")


        and:
        commonFilesGenerated(scriptDsl)

        and:
        succeeds("build")

        and:
        library("build/lib/main/debug/SomeThing").assertExists()

        where:
        scriptDsl << ScriptDslFixture.SCRIPT_DSLS
    }

    @Unroll
    @ToBeFixedForInstantExecution
    def "creates sample source if project name is specified with #scriptDsl build scripts"() {
        when:
        run('init', '--type', 'swift-library', '--project-name', 'greeting', '--dsl', scriptDsl.id)

        then:
        targetDir.file("src/main/swift").assertHasDescendants(SAMPLE_LIBRARY_CLASS)
        targetDir.file("src/test/swift").assertHasDescendants(SAMPLE_LIBRARY_TEST_CLASS, LINUX_MAIN_DOT_SWIFT)

        and:
        targetDir.file("src/test/swift/${SAMPLE_LIBRARY_TEST_CLASS}").text.contains("@testable import Greeting")


        and:
        commonFilesGenerated(scriptDsl)

        and:
        succeeds("build")

        and:
        library("build/lib/main/debug/Greeting").assertExists()

        where:
        scriptDsl << ScriptDslFixture.SCRIPT_DSLS
    }


    @Unroll
    @ToBeFixedForInstantExecution
    def "source generation is skipped when cpp sources detected with #scriptDsl build scripts"() {
        setup:
        targetDir.file("src/main/swift/hola.swift") << """
            public func hola() -> String {
                return "Hola, Mundo!"
            }
        """
        targetDir.file("src/test/swift/HolaTests.swift") << """
            import XCTest
            @testable import Hello
            
            class HolaTests: XCTestCase {
                public static var allTests = [
                    ("testGreeting", testGreeting),
                ]
            
                func testGreeting() {
                    XCTAssertEqual("Hola, Mundo!", hola())
                }
            }
        """
        targetDir.file("src/test/swift/${LINUX_MAIN_DOT_SWIFT}") << """
            import XCTest

            XCTMain([testCase(HolaTests.allTests)])
        """

        when:
        run('init', '--type', 'swift-library', '--project-name', 'hello', '--dsl', scriptDsl.id)

        then:
        targetDir.file("src/main/swift").assertHasDescendants("hola.swift")
        targetDir.file("src/test/swift").assertHasDescendants("HolaTests.swift", LINUX_MAIN_DOT_SWIFT)
        dslFixtureFor(scriptDsl).assertGradleFilesGenerated()

        and:
        targetDir.file("src/main/swift/${SAMPLE_LIBRARY_CLASS}").assertDoesNotExist()
        targetDir.file("src/test/swift/${SAMPLE_LIBRARY_TEST_CLASS}").assertDoesNotExist()
        targetDir.file("src/test/swift/${LINUX_MAIN_DOT_SWIFT}").text.contains("HolaTests.allTests")

        when:
        run("build")

        then:
        executed(":test")

        and:
        library("build/lib/main/debug/Hello").assertExists()

        where:
        scriptDsl << ScriptDslFixture.SCRIPT_DSLS
    }

    SharedLibraryFixture library(String path) {
        AvailableToolChains.getToolChain(ToolChainRequirement.SWIFTC).sharedLibrary(targetDir.file(path))
    }
}
