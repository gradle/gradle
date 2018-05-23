/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.nativeplatform.test.xctest

import org.gradle.internal.os.OperatingSystem
import org.gradle.nativeplatform.fixtures.AbstractInstalledToolChainIntegrationSpec
import org.gradle.nativeplatform.fixtures.RequiresInstalledToolChain
import org.gradle.nativeplatform.fixtures.ToolChainRequirement
import org.gradle.nativeplatform.fixtures.app.SwiftAppWithLibrariesAndXCTest
import org.gradle.nativeplatform.fixtures.app.XCTestCaseElement
import org.gradle.nativeplatform.fixtures.app.XCTestSourceElement
import org.gradle.nativeplatform.fixtures.app.XCTestSourceFileElement

@RequiresInstalledToolChain(ToolChainRequirement.SWIFTC)
class SwiftXCTestErrorHandlingIntegrationTest extends AbstractInstalledToolChainIntegrationSpec {

    def "fails when application cannot load shared library at runtime"() {
        buildWithApplicationAndDependencies()
        buildFile << """
            project(':app') {
                tasks.withType(XCTest).configureEach {
                    doFirst {
                        delete project(':hello').layout.buildDir.get()
                    }
                }
            }
        """

        expect:
        fails(':app:test')

        and:
        if (OperatingSystem.current().isMacOsX()) {
            failure.assertHasErrorOutput("The bundle “AppTest.xctest” couldn’t be loaded because it is damaged or missing necessary resources")
        } else {
            failure.assertHasErrorOutput("cannot open shared object file")
        }
        failure.assertHasCause("Failure while running xctest executable")
    }

    def "fails when force-unwrapping an optional results in an error"() {
        buildWithApplicationAndDependencies()
        addForceUnwrappedOptionalTest()

        expect:
        fails(':app:test')

        and:
        if (OperatingSystem.current().isMacOsX()) {
            failure.assertHasErrorOutput("Test Case '-[AppTest.ForceUnwrapTestSuite testForceUnwrapOptional]' started.")
        } else {
            failure.assertHasErrorOutput("Test Case 'ForceUnwrapTestSuite.testForceUnwrapOptional' started")
        }
        failure.assertHasCause("Failure while running xctest executable")
    }

    void buildWithApplicationAndDependencies() {
        def app = new SwiftAppWithLibrariesAndXCTest()
        app.test.greeterTest.withTestableImport("Hello")
        app.test.greeterTest.withTestableImport("Log")

        app.writeToProject(file("app"))
        app.sum.writeToProject(file("app"))
        app.multiply.writeToProject(file("app"))
        app.greeter.writeToProject(file("hello"))
        app.logger.writeToProject(file("log"))

        settingsFile.text =  """
            include 'app', 'log', 'hello'
            rootProject.name = "app"
        """
        file("app/build.gradle") << """
            apply plugin: 'xctest'
            apply plugin: 'swift-application'
            dependencies {
                implementation project(':hello')
            }
        """
        file("hello/build.gradle") << """
            apply plugin: 'swift-library'
            dependencies {
                implementation project(':log')
            }
        """
        file("log/build.gradle") << """
            apply plugin: 'swift-library'
        """
    }

    void addForceUnwrappedOptionalTest() {
        final XCTestSourceFileElement sourceFileElement = new XCTestSourceFileElement("ForceUnwrapTestSuite") {
            @Override
            List<XCTestCaseElement> getTestCases() {
                return [testCase("testForceUnwrapOptional",
                    """
                                let string: String? = nil
                                XCTAssert((string?.lengthOfBytes(using: .utf8))! > 0)
                            """)]
            }
        }
        XCTestSourceElement sourceElement = new XCTestSourceElement('app') {
            @Override
            List<XCTestSourceFileElement> getTestSuites() {
                return [sourceFileElement]
            }
        }
        sourceElement.writeToProject(file('app'))
    }
}
