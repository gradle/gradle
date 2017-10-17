/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.nativeplatform.test.cpp.plugins

import org.gradle.language.cpp.AbstractCppInstalledToolChainIntegrationTest
import org.gradle.nativeplatform.fixtures.app.CppHelloWorldApp
import org.gradle.nativeplatform.test.googletest.GoogleTestTestResults
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition

class CppTestPluginIntegrationTest extends AbstractCppInstalledToolChainIntegrationTest {
    @Requires(TestPrecondition.MAC_OS_X)
    def "can run tests with google test"() {
        def app = new CppHelloWorldApp()
        buildFile << """
            apply plugin: 'cpp-library'
            apply plugin: 'cpp-test'
            
            repositories {
                maven { url "https://repo.gradle.org/gradle/repo" }
            }

            dependencies {
                unitTestImplementation 'org.gradle:googletest:1.8.0'
            }

            tasks.withType(RunTestExecutable) {
                args "--gtest_output=xml:test_detail.xml"
            }
        """

        app.library.writeSources(file("src/main"))
        app.googleTestTests.writeSources(file("src/unitTest"))

        when:
        succeeds("check")

        then:
        result.assertTasksExecuted(":dependDebugCpp", ":compileDebugCpp",
            ":dependUnitTestExecutableCpp", ":compileUnitTestExecutableCpp", ":linkUnitTestExecutable", ":installUnitTestExecutable", ":runUnitTest", ":check")

        def testResults = new GoogleTestTestResults(file("build/test-results/unitTest/test_detail.xml"))
        testResults.suiteNames == ['HelloTest']
        testResults.suites['HelloTest'].passingTests == ['test_sum']
        testResults.suites['HelloTest'].failingTests == []
        testResults.checkTestCases(1, 1, 0)
    }

    def "can run tests with catch"() {
        def app = new CppHelloWorldApp()

        // Download the single header
        def url = new URL('https://raw.githubusercontent.com/philsquared/Catch/7818e2666d5cc7bb1d912acb22b68f6669b74520/single_include/catch.hpp')
        file("src/unitTest/headers/catch.hpp").text = url.text

        buildFile << """
            apply plugin: 'cpp-library'
            apply plugin: 'cpp-test'

            tasks.withType(RunTestExecutable) {
                args "-r", "xml", "-o", "catch.xml"
            }
        """

        app.library.writeSources(file("src/main"))
        app.catchTests.writeSources(file("src/unitTest"))

        expect:
        succeeds("check")
        file("build/test-results/unitTest/catch.xml").assertIsFile()
    }
}
