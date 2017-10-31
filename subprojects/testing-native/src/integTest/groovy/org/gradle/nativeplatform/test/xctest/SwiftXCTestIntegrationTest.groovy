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
import org.gradle.nativeplatform.fixtures.app.SwiftXCTestWithUnicodeCharactersInTestName
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import org.junit.Rule
import spock.lang.Unroll

@Requires([TestPrecondition.SWIFT_SUPPORT, TestPrecondition.NOT_WINDOWS])
class SwiftXCTestIntegrationTest extends AbstractInstalledToolChainIntegrationSpec {
    @Rule
    TestResources resources = new TestResources(temporaryFolder)

    String getRootProjectName() {
        'Foo'
    }

    def setup() {
        settingsFile << """
rootProject.name = '$rootProjectName'
"""
        buildFile << """
apply plugin: 'xctest'
"""

        if (OperatingSystem.current().linux) {
            buildFile << """
    dependencies {
        swiftImportTest files('${xcTestImportPath}')
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

    @Requires(TestPrecondition.MAC_OS_X)
    def "fails when test cases fail"() {
        def testBundle = new SwiftFailingXCTestBundle().asModule(rootProjectName + "Test")

        given:
        buildFile << "apply plugin: 'swift-library'"
        testBundle.writeToProject(testDirectory)

        when:
        executer.withTasks("tasks").run()
        fails("test")

        then:
        result.assertTasksExecuted(":compileDebugSwift", ":compileTestSwift", ":linkTest", bundleOrInstallTask(), ":xcTest")
        testBundle.assertTestCasesRan(testExecutionResult)
    }

    @Requires(TestPrecondition.MAC_OS_X)
    def "succeeds when test cases pass"() {
        def lib = new SwiftLibWithXCTest().inProject(rootProjectName)

        given:
        buildFile << "apply plugin: 'swift-library'"
        lib.writeToProject(testDirectory)

        when:
        succeeds("test")

        then:
        result.assertTasksExecuted(":compileDebugSwift", ":compileTestSwift", ":linkTest", bundleOrInstallTask(), ":xcTest", ":test")
        lib.assertTestCasesRan(testExecutionResult)
    }

    @Requires(TestPrecondition.MAC_OS_X)
    def "can build xctest bundle when Info.plist is provided"() {
        def lib = new SwiftLibWithXCTest().withInfoPlist().inProject(rootProjectName)

        given:
        buildFile << "apply plugin: 'swift-library'"
        lib.writeToProject(testDirectory)

        when:
        succeeds("test")

        then:
        result.assertTasksExecuted(":compileDebugSwift", ":compileTestSwift", ":linkTest", bundleOrInstallTask(), ":xcTest", ":test")
        lib.assertTestCasesRan(testExecutionResult)
    }

    @Unroll
    @Requires(TestPrecondition.MAC_OS_X)
    def "runs tests when #task lifecycle task executes"() {
        def lib = new SwiftLibWithXCTest().inProject(rootProjectName)

        given:
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

    @Requires(TestPrecondition.MAC_OS_X)
    def "can test public and internal features of a Swift library"() {
        def lib = new SwiftLibWithXCTest().inProject(rootProjectName)

        given:
        buildFile << """
apply plugin: 'swift-library'
"""
        lib.writeToProject(testDirectory)

        when:
        succeeds("test")

        then:
        result.assertTasksExecuted(":compileDebugSwift", ":compileTestSwift", ":linkTest", bundleOrInstallTask(), ":xcTest", ":test")
        lib.assertTestCasesRan(testExecutionResult)
    }

    @Requires(TestPrecondition.MAC_OS_X)
    def "does not execute removed test suite and case"() {
        def testBundle = new IncrementalSwiftXCTestRemoveDiscoveryBundle()

        given:
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

    @Requires(TestPrecondition.MAC_OS_X)
    def "executes added test suite and case"() {
        def testBundle = new IncrementalSwiftXCTestAddDiscoveryBundle()

        given:
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

    @Requires(TestPrecondition.MAC_OS_X)
    def "skips test tasks as up-to-date when nothing changes between invocation"() {
        def lib = new SwiftLibWithXCTest().inProject(rootProjectName)

        given:
        buildFile << "apply plugin: 'swift-library'"
        lib.writeToProject(testDirectory)

        when:
        succeeds("test")
        succeeds("test")

        then:
        result.assertTasksExecuted(":compileDebugSwift", ":compileTestSwift", ":linkTest", bundleOrInstallTask(), ":xcTest", ":test")
        result.assertTasksSkipped(":compileDebugSwift", ":compileTestSwift", ":linkTest", bundleOrInstallTask(), ":xcTest", ":test")
    }

    @Requires(TestPrecondition.MAC_OS_X)
    def "build logic can change source layout convention"() {
        def lib = new SwiftLibWithXCTest().inProject(rootProjectName)

        given:
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
        executable("build/exe/test/${rootProjectName}Test").assertExists()
        lib.assertTestCasesRan(testExecutionResult)
    }

    def "can specify a test dependency on another library"() {
        def lib = new SwiftLib()
        def test = new SwiftLibTest(lib.greeter, lib.sum, lib.multiply).asModule(rootProjectName).withImport("Greeter")

        given:
        settingsFile << """
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
        lib.asModule("Greeter").writeToProject(file('greeter'))
        test.writeToProject(testDirectory)

        when:
        succeeds("test")

        then:
        result.assertTasksExecuted(":greeter:compileDebugSwift", ":greeter:linkDebug", ":compileDebugSwift", ":compileTestSwift", ":linkTest", bundleOrInstallTask(), ":xcTest", ":test")
    }

    def "does not build or run any of the tests when assemble task executes"() {
        def testBundle = new SwiftFailingXCTestBundle()

        given:
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

    @Requires(TestPrecondition.MAC_OS_X)
    def "can test public and internal features of a Swift executable"() {
        def app = new SwiftAppWithXCTest()

        given:
        settingsFile << "rootProject.name = 'app'"
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

    @Requires(TestPrecondition.MAC_OS_X)
    def "can test features of a Swift executable using a single test source file"() {
        def app = new SwiftAppWithSingleXCTestSuite().inProject(rootProjectName)

        given:
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

    @Requires(TestPrecondition.MAC_OS_X)
    def "can test features of a single file Swift library using a single test source file"() {
        def lib = new SwiftSingleFileLibWithSingleXCTestSuite().inProject(rootProjectName)

        given:
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

    def "build passes when tests have unicode characters"() {
        given:
        def test = new SwiftXCTestWithUnicodeCharactersInTestName().asModule('AppTest')
        test.writeToProject(testDirectory)

        when:
        succeeds("test")

        then:
        result.assertTasksExecuted(":compileTestSwift", ":linkTest", ":bundleSwiftTest", ":xcTest", ":test")
        test.assertTestCasesRan(testExecutionResult)
    }

    def "build still fails when tests have unicode characters"() {
        given:
        def test = new SwiftXCTestWithUnicodeCharactersInTestName().withFailures().asModule('AppTest')
        test.writeToProject(testDirectory)

        when:
        fails("test")

        then:
        result.assertTasksExecuted(":compileTestSwift", ":linkTest", ":bundleSwiftTest", ":xcTest")
        test.assertTestCasesRan(testExecutionResult)
    }

    def 'can build xctest bundle which depends multiple swift modules'() {
        when:
        succeeds 'test'

        then:
        result.assertTasksExecuted(':greeter:compileDebugSwift', ':greeter:compileTestSwift', ':greeter:linkDebug',
            ':greeter:linkTest', ':greeter:bundleSwiftTest', ':greeter:xcTest', ':greeter:test', ':compileDebugSwift',
            ':compileTestSwift', ':linkTest', ':bundleSwiftTest', ':xcTest', ':test')
    }

    def 'can run xctest in swift package manager layout'() {
        when:
        succeeds 'test'

        then:
        result.assertTasksExecuted(':greeter:compileDebugSwift', ':greeter:compileTestSwift', ':greeter:linkDebug',
            ':greeter:linkTest', ':greeter:bundleSwiftTest', ':greeter:xcTest', ':greeter:test', ':app:compileDebugSwift',
            ':app:compileTestSwift', ':app:linkTest', ':app:bundleSwiftTest', ':app:xcTest', ':app:test', ':compileTestSwift',
            ':linkTest', ':bundleSwiftTest', ':xcTest', ':test')
    }

    private void assertMainSymbolIsAbsent(List<NativeBinaryFixture> binaries) {
        binaries.each {
            assertMainSymbolIsAbsent(it)
        }
    }

    private void assertMainSymbolIsAbsent(NativeBinaryFixture binary) {
        assert !binary.binaryInfo.listSymbols().contains('_main')
    }

    TestExecutionResult getTestExecutionResult() {
        return new DefaultTestExecutionResult(testDirectory, 'build', '', '', 'xcTest')
    }

    private String bundleOrInstallTask(String project = '') {
        if (OperatingSystem.current().isMacOsX()) {
            return "$project:bundleSwiftTest"
        }
        return "$project:installTest"
    }
}
