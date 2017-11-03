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

package org.gradle.nativeplatform.test.xctest

import org.gradle.integtests.fixtures.DefaultTestExecutionResult
import org.gradle.integtests.fixtures.TestExecutionResult
import org.gradle.integtests.fixtures.TestResources
import org.gradle.internal.os.OperatingSystem
import org.gradle.nativeplatform.fixtures.AbstractInstalledToolChainIntegrationSpec
import org.gradle.nativeplatform.fixtures.NativeBinaryFixture
import org.gradle.nativeplatform.fixtures.app.IncrementalSwiftXCTestAddDiscoveryBundle
import org.gradle.nativeplatform.fixtures.app.IncrementalSwiftXCTestRemoveDiscoveryBundle
import org.gradle.nativeplatform.fixtures.app.SwiftAppWithSingleXCTestSuite
import org.gradle.nativeplatform.fixtures.app.SwiftAppWithXCTest
import org.gradle.nativeplatform.fixtures.app.SwiftFailingXCTestBundle
import org.gradle.nativeplatform.fixtures.app.SwiftLib
import org.gradle.nativeplatform.fixtures.app.SwiftLibTest
import org.gradle.nativeplatform.fixtures.app.SwiftLibWithXCTest
import org.gradle.nativeplatform.fixtures.app.SwiftSingleFileLibWithSingleXCTestSuite
import org.gradle.nativeplatform.fixtures.app.XCTestCaseElement
import org.gradle.nativeplatform.fixtures.app.XCTestSourceElement
import org.gradle.nativeplatform.fixtures.app.XCTestSourceFileElement
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import org.junit.Rule
import spock.lang.Unroll

@Requires([TestPrecondition.SWIFT_SUPPORT])
class SwiftXCTestIntegrationTest extends AbstractInstalledToolChainIntegrationSpec {
    @Rule
    TestResources resources = new TestResources(temporaryFolder)

    def setup() {
        buildFile << """
apply plugin: 'xctest'
"""

        if (OperatingSystem.current().linux) {
            buildFile << """
    dependencies {
        swiftCompileTest files('${xcTestImportPath}')
        nativeLinkTest files('${xcTestLinkFile}')
        nativeRuntimeTest files('${xcTestRuntimeFile}')
    }
"""
        }

    }

    private File getXcTestImportPath() {
        for (File pathEntry : toolChain.getPathEntries()) {
            File result = new File(pathEntry.parentFile, 'lib/swift/linux/x86_64/XCTest.swiftmodule')
            if (result.exists()) {
                return result.parentFile
            }
        }

        throw new IllegalStateException("'XCTest.swiftmodule' couldn't be found.")
    }

    private String getXcTestLinkFile() {
        for (File pathEntry : toolChain.getPathEntries()) {
            File result = new File(pathEntry.parentFile, 'lib/swift/linux/libXCTest.so')
            if (result.exists()) {
                return result.absolutePath
            }
        }

        throw new IllegalStateException("'libXCTest.so' couldn't be found.")
    }

    private String getXcTestRuntimeFile() {
        return xcTestLinkFile
    }

    // TODO: Need to support test report for test case assertion
    @Requires(TestPrecondition.MAC_OS_X)
    def "fails when test cases fail"() {
        given:
        def testBundle = new SwiftFailingXCTestBundle()
        settingsFile << "rootProject.name = '${testBundle.projectName}'"
        buildFile << "apply plugin: 'swift-library'"
        testBundle.writeToProject(testDirectory)

        when:
        executer.withTasks("tasks").run()
        fails("test")

        then:
        result.assertTasksExecuted(":compileDebugSwift", ":compileTestSwift", ":linkTest", bundleOrInstallTask(), ":xcTest")
        testBundle.assertTestCasesRan(testExecutionResult)
    }

    // TODO: Need to support test report for test case assertion
    @Requires(TestPrecondition.MAC_OS_X)
    def "succeeds when test cases pass"() {
        given:
        def lib = new SwiftLibWithXCTest()
        settingsFile << "rootProject.name = '${lib.projectName}'"
        buildFile << "apply plugin: 'swift-library'"
        lib.writeToProject(testDirectory)

        when:
        succeeds("test")

        then:
        result.assertTasksExecuted(":compileDebugSwift", ":compileTestSwift", ":linkTest", bundleOrInstallTask(), ":xcTest", ":test")
        lib.assertTestCasesRan(testExecutionResult)
    }

    // TODO: Need to support test report for test case assertion
    @Requires(TestPrecondition.MAC_OS_X)
    def "can build xctest bundle when Info.plist is provided"() {
        given:
        def lib = new SwiftLibWithXCTest().withInfoPlist()
        settingsFile << "rootProject.name = '${lib.projectName}'"
        buildFile << "apply plugin: 'swift-library'"
        lib.writeToProject(testDirectory)

        when:
        succeeds("test")

        then:
        result.assertTasksExecuted(":compileDebugSwift", ":compileTestSwift", ":linkTest", bundleOrInstallTask(), ":xcTest", ":test")
        lib.assertTestCasesRan(testExecutionResult)
    }

    // TODO: Need to support test report for test case assertion
    @Unroll
    @Requires(TestPrecondition.MAC_OS_X)
    def "runs tests when #task lifecycle task executes"() {
        given:
        def lib = new SwiftLibWithXCTest()
        settingsFile << "rootProject.name = '${lib.projectName}'"
        buildFile << "apply plugin: 'swift-library'"
        lib.writeToProject(testDirectory)

        when:
        succeeds(task)

        then:
        executed(":xcTest")
        lib.assertTestCasesRan(testExecutionResult)

        where:
        task << ["test", "check", "build"]
    }

    // TODO: Need to support test report for test case assertion
    @Requires(TestPrecondition.MAC_OS_X)
    def "can test public and internal features of a Swift library"() {
        given:
        def lib = new SwiftLibWithXCTest()
        settingsFile << "rootProject.name = '${lib.projectName}'"
        buildFile << "apply plugin: 'swift-library'"
        lib.writeToProject(testDirectory)

        when:
        succeeds("test")

        then:
        result.assertTasksExecuted(":compileDebugSwift", ":compileTestSwift", ":linkTest", bundleOrInstallTask(), ":xcTest", ":test")
        lib.assertTestCasesRan(testExecutionResult)
    }

    // TODO: Need to support test report for test case assertion
    @Requires(TestPrecondition.MAC_OS_X)
    def "does not execute removed test suite and case"() {
        given:
        def testBundle = new IncrementalSwiftXCTestRemoveDiscoveryBundle()
        assert testBundle.alternateFooTestSuite.testCount < testBundle.fooTestSuite.testCount

        settingsFile << "rootProject.name = 'app'"
        buildFile << "apply plugin: 'swift-library'"
        testBundle.writeToProject(testDirectory)

        when:
        succeeds("test")

        then:
        result.assertTasksExecuted(":compileDebugSwift", ":compileTestSwift", ":linkTest", bundleOrInstallTask(), ":xcTest", ":test")
        testBundle.assertTestCasesRan(testExecutionResult)

        when:
        testBundle.applyChangesToProject(testDirectory)
        succeeds("test")

        then:
        result.assertTasksExecuted(":compileDebugSwift", ":compileTestSwift", ":linkTest", bundleOrInstallTask(), ":xcTest", ":test")
        testBundle.assertAlternateTestCasesRan(testExecutionResult)
        testBundle.getFooTestSuite().getTestCount()
    }

    // TODO: Need to support test report for test case assertion
    @Requires(TestPrecondition.MAC_OS_X)
    def "executes added test suite and case"() {
        given:
        def testBundle = new IncrementalSwiftXCTestAddDiscoveryBundle()
        assert testBundle.alternateFooTestSuite.testCount > testBundle.fooTestSuite.testCount

        settingsFile << "rootProject.name = 'app'"
        buildFile << "apply plugin: 'swift-library'"
        testBundle.writeToProject(testDirectory)

        when:
        succeeds("test")

        then:
        result.assertTasksExecuted(":compileDebugSwift", ":compileTestSwift", ":linkTest", bundleOrInstallTask(), ":xcTest", ":test")
        testBundle.assertTestCasesRan(testExecutionResult)

        when:
        testBundle.applyChangesToProject(testDirectory)
        succeeds("test")

        then:
        result.assertTasksExecuted(":compileDebugSwift", ":compileTestSwift", ":linkTest", bundleOrInstallTask(), ":xcTest", ":test")
        testBundle.assertAlternateTestCasesRan(testExecutionResult)
    }

    // TODO: Needs RunTestExecutable to be incremental
    @Requires(TestPrecondition.MAC_OS_X)
    def "skips test tasks as up-to-date when nothing changes between invocation"() {
        given:
        def lib = new SwiftLibWithXCTest()
        settingsFile << "rootProject.name = '${lib.projectName}'"
        buildFile << "apply plugin: 'swift-library'"
        lib.writeToProject(testDirectory)

        when:
        succeeds("test")
        succeeds("test")

        then:
        result.assertTasksExecuted(":compileDebugSwift", ":compileTestSwift", ":linkTest", bundleOrInstallTask(), ":xcTest", ":test")
        result.assertTasksSkipped(":compileDebugSwift", ":compileTestSwift", ":linkTest", bundleOrInstallTask(), ":xcTest", ":test")
    }

    // TODO: Need to support test report for test case assertion
    @Requires(TestPrecondition.MAC_OS_X)
    def "build logic can change source layout convention"() {
        given:
        def lib = new SwiftLibWithXCTest()
        settingsFile << "rootProject.name = '${lib.projectName}'"
        buildFile << "apply plugin: 'swift-library'"
        lib.main.writeToSourceDir(file("Sources"))
        lib.test.writeToSourceDir(file("Tests"))
        file("src/main/swift/broken.swift") << "ignore me!"
        file("src/test/swift/broken.swift") << "ignore me!"

        and:
        buildFile << """
            library {
                source.from 'Sources'
            }
            xctest {
                source.from 'Tests'
                resourceDir.set(file('Tests'))
            }
         """

        expect:
        succeeds "test"
        result.assertTasksExecuted(":compileDebugSwift", ":compileTestSwift", ":linkTest", bundleOrInstallTask(), ":xcTest", ":test")

        file("build/obj/test").assertIsDir()
        executable("build/exe/test/${lib.test.moduleName}").assertExists()
        lib.assertTestCasesRan(testExecutionResult)
    }

    def "can specify a test dependency on another library"() {
        def lib = new SwiftLib()
        def test = new SwiftLibTest(lib, lib.greeter, lib.sum, lib.multiply)

        given:
        settingsFile << """
rootProject.name = 'app'
include 'greeter'
"""
        buildFile << """
project(':greeter') {
    apply plugin: 'swift-library'
}

apply plugin: 'swift-library'

dependencies {
    testImplementation project(':greeter')
}
"""
        lib.writeToProject(file('greeter'))
        test.writeToProject(testDirectory)

        when:
        succeeds("test")

        then:
        result.assertTasksExecuted(":greeter:compileDebugSwift", ":greeter:linkDebug", ":compileDebugSwift", ":compileTestSwift", ":linkTest", bundleOrInstallTask(), ":xcTest", ":test")
    }

    def "does not build or run any of the tests when assemble task executes"() {
        given:
        def testBundle = new SwiftFailingXCTestBundle()
        settingsFile << "rootProject.name = '${testBundle.projectName}'"
        testBundle.writeToProject(testDirectory)

        when:
        succeeds("assemble")

        then:
        result.assertTasksExecuted(":assemble")
        result.assertTasksSkipped(":assemble")
    }

    def "skips test tasks when no source is available for Swift library"() {
        given:
        buildFile << "apply plugin: 'swift-library'"

        when:
        succeeds("test")

        then:
        result.assertTasksExecuted(":compileDebugSwift", ":compileTestSwift", ":linkTest", bundleOrInstallTask(), ":xcTest", ":test")
        result.assertTasksSkipped(":compileDebugSwift", ":compileTestSwift", ":linkTest", bundleOrInstallTask(), ":xcTest", ":test")
    }

    def "skips test tasks when no source is available for Swift executable"() {
        given:
        buildFile << """
apply plugin: 'swift-executable'
"""

        when:
        succeeds("test")

        then:
        result.assertTasksExecuted(":compileDebugSwift", ":compileTestSwift", ":linkTest", bundleOrInstallTask(), ":xcTest", ":test")
        result.assertTasksSkipped(":compileDebugSwift", ":compileTestSwift", ":linkTest", bundleOrInstallTask(), ":xcTest", ":test")
    }

    // TODO: Need to support test report for test case assertion
    @Requires(TestPrecondition.MAC_OS_X)
    def "can test public and internal features of a Swift executable"() {
        given:
        def app = new SwiftAppWithXCTest()
        settingsFile << "rootProject.name = '${app.projectName}'"
        buildFile << """
apply plugin: 'swift-executable'

linkTest.source = project.files(new HashSet(linkTest.source.from)).filter { !it.name.equals("main.o") }
"""
        app.writeToProject(testDirectory)

        when:
        succeeds("test")

        then:
        result.assertTasksExecuted(":compileDebugSwift", ":compileTestSwift", ":linkTest", bundleOrInstallTask(), ":xcTest", ":test")
        app.assertTestCasesRan(testExecutionResult)
    }

    // TODO: Need to support test report for test case assertion
    @Requires(TestPrecondition.MAC_OS_X)
    def "can test features of a Swift executable using a single test source file"() {
        given:
        def app = new SwiftAppWithSingleXCTestSuite()
        settingsFile << "rootProject.name = '${app.projectName}'"
        buildFile << """
apply plugin: 'swift-executable'
"""
        app.writeToProject(testDirectory)

        when:
        succeeds("test")

        then:
        result.assertTasksExecuted(":compileDebugSwift", ":compileTestSwift", ":linkTest", bundleOrInstallTask(), ":xcTest", ":test")
        assertMainSymbolIsAbsent(objectFiles(app.test, "build/obj/test"))
        app.assertTestCasesRan(testExecutionResult)
    }

    // TODO: Need to support test report for test case assertion
    @Requires(TestPrecondition.MAC_OS_X)
    def "can test features of a single file Swift library using a single test source file"() {
        given:
        def lib = new SwiftSingleFileLibWithSingleXCTestSuite()

        settingsFile << "rootProject.name = '${lib.projectName}'"
        buildFile << """
apply plugin: 'swift-library'
"""
        lib.writeToProject(testDirectory)

        when:
        succeeds("test")

        then:
        result.assertTasksExecuted(":compileDebugSwift", ":compileTestSwift", ":linkTest", bundleOrInstallTask(), ":xcTest", ":test")
        assertMainSymbolIsAbsent(objectFiles(lib.test, "build/obj/test"))
        assertMainSymbolIsAbsent(machOBundle("build/exe/test/${lib.test.moduleName}"))
        lib.assertTestCasesRan(testExecutionResult)
    }

    // TODO: Need to support test report for test case assertion
    @Requires(TestPrecondition.MAC_OS_X)
    def "build passes when tests have unicode characters"() {
        given:
        def test = new XCTestSourceElement("app") {
            List<XCTestSourceFileElement> testSuites = [
                new XCTestSourceFileElement("NormalTestSuite") {
                    List<XCTestCaseElement> testCases = [
                        passingTestCase("testExpectedTestName")]
                },

                new XCTestSourceFileElement("SpecialCharsTestSuite") {
                    List<XCTestCaseElement> testCases = [
                        passingTestCase("test_name_with_leading_underscore"),
                        passingTestCase("testname_with_a_number_1234"),
                        passingTestCase("test·middle_dot"),
                        passingTestCase("test1234"),
                        passingTestCase("testᏀᎡᎪᎠᏞᎬ")
                    ]
                },

                new XCTestSourceFileElement("UnicodeᏀᎡᎪᎠᏞᎬSuite") {
                    List<XCTestCaseElement> testCases = [
                        passingTestCase("testSomething"),
                    ]
                }
            ]
        }
        settingsFile << "rootProject.name = '${test.projectName}'"
        test.writeToProject(testDirectory)

        when:
        succeeds("test")

        then:
        result.assertTasksExecuted(":compileTestSwift", ":linkTest", ":bundleSwiftTest", ":xcTest", ":test")
        test.assertTestCasesRan(testExecutionResult)
    }

    // TODO: Need to support test report for test case assertion
    @Requires(TestPrecondition.MAC_OS_X)
    def "build still fails when tests have unicode characters"() {
        given:
        def test = new XCTestSourceElement("app") {
            List<XCTestSourceFileElement> testSuites = [
                new XCTestSourceFileElement("NormalTestSuite") {
                    List<XCTestCaseElement> testCases = [
                        passingTestCase("testExpectedTestName")]
                },

                new XCTestSourceFileElement("SpecialCharsTestSuite") {
                    List<XCTestCaseElement> testCases = [
                        failingTestCase("test_name_with_leading_underscore"),
                        passingTestCase("testname_with_a_number_1234"),
                        failingTestCase("test·middle_dot"),
                        passingTestCase("test1234"),
                        passingTestCase("testᏀᎡᎪᎠᏞᎬ")
                    ]
                },

                new XCTestSourceFileElement("UnicodeᏀᎡᎪᎠᏞᎬSuite") {
                    List<XCTestCaseElement> testCases = [
                        failingTestCase("testSomething"),
                    ]
                }
            ]
        }
        settingsFile << "rootProject.name = '${test.projectName}'"
        test.writeToProject(testDirectory)

        when:
        fails("test")

        then:
        result.assertTasksExecuted(":compileTestSwift", ":linkTest", ":bundleSwiftTest", ":xcTest")
        test.assertTestCasesRan(testExecutionResult)
    }

    // TODO: Need to support _main symbol duplication
    @Requires(TestPrecondition.MAC_OS_X)
    def 'can build xctest bundle which depends multiple swift modules'() {
        when:
        succeeds 'test'

        then:
        result.assertTasksExecuted(':greeter:compileDebugSwift', ':greeter:compileTestSwift', ':greeter:linkDebug',
            ':greeter:linkTest', ':greeter:bundleSwiftTest', ':greeter:xcTest', ':greeter:test', ':compileDebugSwift',
            ':compileTestSwift', ':linkTest', ':bundleSwiftTest', ':xcTest', ':test')
    }

    // TODO: Need to support _main symbol duplication
    @Requires(TestPrecondition.MAC_OS_X)
    def 'can run xctest in swift package manager layout'() {
        when:
        succeeds 'test'

        then:
        result.assertTasksExecuted(':greeter:compileDebugSwift', ':greeter:compileTestSwift', ':greeter:linkDebug',
            ':greeter:linkTest', ':greeter:bundleSwiftTest', ':greeter:xcTest', ':greeter:test', ':app:compileDebugSwift',
            ':app:compileTestSwift', ':app:linkTest', ':app:bundleSwiftTest', ':app:xcTest', ':app:test', ':compileTestSwift',
            ':linkTest', ':bundleSwiftTest', ':xcTest', ':test')
    }

    private static void assertMainSymbolIsAbsent(List<NativeBinaryFixture> binaries) {
        binaries.each {
            assertMainSymbolIsAbsent(it)
        }
    }

    private static void assertMainSymbolIsAbsent(NativeBinaryFixture binary) {
        assert !binary.binaryInfo.listSymbols().contains('_main')
    }

    TestExecutionResult getTestExecutionResult() {
        return new DefaultTestExecutionResult(testDirectory, 'build', '', '', 'xcTest')
    }

    private static String bundleOrInstallTask(String project = '') {
        if (OperatingSystem.current().isMacOsX()) {
            return "$project:bundleSwiftTest"
        }
        return "$project:installTest"
    }
}
