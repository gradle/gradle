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

package org.gradle.nativeplatform.test.googletest.plugins

import org.gradle.internal.os.OperatingSystem
import org.gradle.language.cpp.AbstractCppInstalledToolChainIntegrationTest
import org.gradle.nativeplatform.fixtures.app.CppHelloWorldApp
import org.gradle.nativeplatform.test.googletest.GoogleTestTestResults
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import org.gradle.util.TextUtil

class GoogleUnitTestPluginIntegrationTest extends AbstractCppInstalledToolChainIntegrationTest {
    def prebuiltDir = buildContext.getSamplesDir().file("native-binaries/google-test/libs")
    def prebuiltPath = TextUtil.normaliseFileSeparators(prebuiltDir.path)

    @Requires(TestPrecondition.MAC_OS_X)
    def "can run tests with google test"() {
        def app = new CppHelloWorldApp()
        buildFile << """
            apply plugin: 'cpp-library'
            apply plugin: 'google-unit-test'

            def googleTestHeaders = file("${prebuiltPath}/googleTest/1.7.0/include")
            def googleTestStaticLib = file("${prebuiltPath}/googleTest/1.7.0/lib/osx64/${googleTestLib}")
            dependencies {
                cppCompileUnitTestExecutable files(googleTestHeaders)
                nativeLinkUnitTestExecutable files(googleTestStaticLib)
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

    private def getGoogleTestLib() {
        return OperatingSystem.current().getStaticLibraryName("gtest")
    }
}
