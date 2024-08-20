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

import org.gradle.integtests.fixtures.DefaultTestExecutionResult
import org.gradle.integtests.fixtures.TestExecutionResult
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.internal.os.OperatingSystem
import org.gradle.nativeplatform.fixtures.AbstractInstalledToolChainIntegrationSpec
import org.gradle.nativeplatform.fixtures.RequiresInstalledToolChain
import org.gradle.nativeplatform.fixtures.ToolChainRequirement
import org.gradle.nativeplatform.fixtures.app.SwiftAppWithLibrariesAndXCTest
import org.gradle.nativeplatform.fixtures.app.XCTestCaseElement
import org.gradle.nativeplatform.fixtures.app.XCTestSourceElement
import org.gradle.nativeplatform.fixtures.app.XCTestSourceFileElement
import org.gradle.test.fixtures.file.DoesNotSupportNonAsciiPaths
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions
import org.gradle.util.internal.VersionNumber

import static org.gradle.integtests.fixtures.TestExecutionResult.EXECUTION_FAILURE
import static org.gradle.util.Matchers.containsText

@RequiresInstalledToolChain(ToolChainRequirement.SWIFTC)
@Requires(UnitTestPreconditions.HasXCTest)
@DoesNotSupportNonAsciiPaths(reason = "swiftc does not support these paths")
class SwiftXCTestErrorHandlingIntegrationTest extends AbstractInstalledToolChainIntegrationSpec {
    @ToBeFixedForConfigurationCache
    def "fails when working directory is invalid"() {
        buildWithApplicationAndDependencies()
        buildFile << """
            project(':app') {
                tasks.withType(XCTest).configureEach {
                    doFirst {
                        workingDirectory = project.layout.projectDirectory.dir("does-not-exist")
                    }
                }
            }
        """
        expect:
        fails(':app:test')

        and:
        failure.assertHasCause("There were failing tests.")
        def testFailure = testExecutionResult.testClass("Gradle Test Run :app:xcTest")
        testFailure.assertTestFailed(EXECUTION_FAILURE, containsText("A problem occurred starting process"))
    }

    @ToBeFixedForConfigurationCache
    def "fails when application cannot load shared library at runtime"() {
        buildWithApplicationAndDependencies()
        buildFile << """
            project(':app') {
                tasks.withType(XCTest).configureEach {
                    doFirst {
                        delete project(':hello').layout.buildDirectory.get()
                    }
                }
            }
        """

        expect:
        fails(':app:test')

        and:
        failure.assertHasCause("There were failing tests.")
        def testFailure = testExecutionResult.testClass("Gradle Test Run :app:xcTest")
        testFailure.assertTestFailed(EXECUTION_FAILURE, containsText("finished with non-zero exit value"))
        if (OperatingSystem.current().isMacOsX()) {
            if (toolChain.version < VersionNumber.version(5, 9)) {
                testFailure.assertStderr(containsText("The bundle “AppTest.xctest” couldn’t be loaded because it is damaged or missing necessary resources"))
            }
            // Else, there is no stderr/stdout produced by newer versions
        } else {
            testFailure.assertStderr(containsText("cannot open shared object file"))
        }
    }

    @ToBeFixedForConfigurationCache
    def "fails when force-unwrapping an optional results in an error"() {
        buildWithApplicationAndDependencies()
        addForceUnwrappedOptionalTest()

        expect:
        fails(':app:test')

        and:
        failure.assertHasCause("There were failing tests.")
        testExecutionResult.testClass("ForceUnwrapTestSuite").assertTestFailed("testForceUnwrapOptional", containsText("finished with non-zero exit value"))
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

        settingsFile.text = """
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

    TestExecutionResult getTestExecutionResult() {
        return new DefaultTestExecutionResult(testDirectory.file('app'), 'build', '', '', 'xcTest')
    }
}
