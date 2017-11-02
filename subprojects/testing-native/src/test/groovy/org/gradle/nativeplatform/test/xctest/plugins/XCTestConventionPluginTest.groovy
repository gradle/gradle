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

package org.gradle.nativeplatform.test.xctest.plugins

import org.gradle.internal.os.OperatingSystem
import org.gradle.language.swift.plugins.SwiftLibraryPlugin
import org.gradle.language.swift.tasks.CreateSwiftBundle
import org.gradle.language.swift.tasks.SwiftCompile
import org.gradle.nativeplatform.tasks.InstallExecutable
import org.gradle.nativeplatform.tasks.LinkExecutable
import org.gradle.nativeplatform.tasks.LinkMachOBundle
import org.gradle.nativeplatform.test.tasks.RunTestExecutable
import org.gradle.nativeplatform.test.xctest.SwiftXCTestSuite
import org.gradle.nativeplatform.test.xctest.tasks.XcTest
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import org.junit.Rule
import spock.lang.Specification

@Requires([TestPrecondition.MAC_OS_X, TestPrecondition.LINUX])
class XCTestConventionPluginTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()
    def projectDir = tmpDir.createDir("project")
    def project = ProjectBuilder.builder().withProjectDir(projectDir).withName("testApp").build()

    def "adds extension with convention for source layout"() {
        given:
        project.pluginManager.apply(SwiftLibraryPlugin)
        def src = projectDir.file("src/test/swift/test.swift").createFile()

        when:
        project.pluginManager.apply(XCTestConventionPlugin)

        then:
        project.xctest instanceof SwiftXCTestSuite
        project.xctest.swiftSource.files == [src] as Set
    }

    def "registers a component for the test suite"() {
        given:
        project.pluginManager.apply(SwiftLibraryPlugin)

        when:
        project.pluginManager.apply(XCTestConventionPlugin)

        then:
        project.components.test == project.xctest
        project.components."$developmentBinaryName" == project.xctest.developmentBinary
    }

    def "registers a component for the test suite when plugin is applied before"() {
        when:
        project.pluginManager.apply(XCTestConventionPlugin)
        project.pluginManager.apply(SwiftLibraryPlugin)

        then:
        project.components.test == project.xctest
        project.components."$developmentBinaryName" == project.xctest.developmentBinary
    }

    @Requires(TestPrecondition.MAC_OS_X)
    def "adds compile, link and install tasks on macOS"() {
        given:
        project.pluginManager.apply(SwiftLibraryPlugin)
        def src = projectDir.file("src/test/swift/test.swift").createFile()

        when:
        project.pluginManager.apply(XCTestConventionPlugin)

        then:
        def compileSwift = project.tasks.compileTestSwift
        compileSwift instanceof SwiftCompile
        compileSwift.source.files == [src] as Set
        compileSwift.objectFileDir.get().asFile == projectDir.file("build/obj/test")
        compileSwift.debuggable
        !compileSwift.optimized

        def link = project.tasks.linkTest
        link instanceof LinkMachOBundle
        link.binaryFile.get().asFile == projectDir.file("build/exe/test/" + OperatingSystem.current().getExecutableName("TestAppTest"))
        link.debuggable

        def bundle = project.tasks.bundleSwiftTest
        bundle instanceof CreateSwiftBundle
        bundle.outputDir.get().asFile == project.file("build/bundle/test/TestAppTest.xctest")

        def test = project.tasks.xcTest
        test instanceof XcTest
    }

    @Requires(TestPrecondition.LINUX)
    def "adds compile, link and install tasks on Linux"() {
        given:
        project.pluginManager.apply(SwiftLibraryPlugin)
        def src = projectDir.file("src/test/swift/test.swift").createFile()

        when:
        project.pluginManager.apply(XCTestConventionPlugin)

        then:
        def compileSwift = project.tasks.compileTestSwift
        compileSwift instanceof SwiftCompile
        compileSwift.source.files == [src] as Set
        compileSwift.objectFileDir.get().asFile == projectDir.file("build/obj/test")
        compileSwift.debuggable
        !compileSwift.optimized

        def link = project.tasks.linkTest
        link instanceof LinkExecutable
        link.binaryFile.get().asFile == projectDir.file("build/exe/test/" + OperatingSystem.current().getExecutableName("TestAppTest"))
        link.debuggable

        def install = project.tasks.installTest
        install instanceof InstallExecutable
        install.installDirectory.get().asFile == project.file("build/install/test")
        install.runScript.name == OperatingSystem.current().getScriptName("test_app_test")

        def test = project.tasks.xcTest
        test instanceof RunTestExecutable
    }

    @Requires(TestPrecondition.MAC_OS_X)
    def "output locations reflects changes to buildDir on macOS"() {
        given:
        project.pluginManager.apply(SwiftLibraryPlugin)

        when:
        project.pluginManager.apply(XCTestConventionPlugin)
        project.buildDir = project.file("output")

        then:
        def compileSwift = project.tasks.compileTestSwift
        compileSwift.objectFileDir.get().asFile == projectDir.file("output/obj/test")

        def link = project.tasks.linkTest
        link.binaryFile.get().asFile == projectDir.file("output/exe/test/" + OperatingSystem.current().getExecutableName("TestAppTest"))

        def bundle = project.tasks.bundleSwiftTest
        bundle.outputDir.get().asFile == project.file("output/bundle/test/TestAppTest.xctest")

        def test = project.tasks.xcTest
        test.workingDirectory.get().asFile == projectDir.file("output/bundle/test")
    }

    @Requires(TestPrecondition.LINUX)
    def "output locations reflects changes to buildDir on Linux"() {
        given:
        project.pluginManager.apply(SwiftLibraryPlugin)

        when:
        project.pluginManager.apply(XCTestConventionPlugin)
        project.buildDir = project.file("output")

        then:
        def compileSwift = project.tasks.compileTestSwift
        compileSwift.objectFileDir.get().asFile == projectDir.file("output/obj/test")

        def link = project.tasks.linkTest
        link.binaryFile.get().asFile == projectDir.file("output/exe/test/" + OperatingSystem.current().getExecutableName("TestAppTest"))

        def install = project.tasks.installTest
        install.installDirectory.get().asFile == project.file("output/install/test")
        install.runScript.name == OperatingSystem.current().getScriptName("test_app_test")

        def test = project.tasks.xcTest
        test.outputDir == projectDir.file("output/test-results/xctest")
    }

    private String getDevelopmentBinaryName() {
        if (OperatingSystem.current().isMacOsX()) {
            return "testBundle"
        }
        return "testExecutable"
    }
}
