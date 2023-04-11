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

package org.gradle.language.swift

import org.gradle.integtests.fixtures.CompilationOutputsFixture
import org.gradle.integtests.fixtures.SourceFile
import org.gradle.internal.os.OperatingSystem
import org.gradle.nativeplatform.fixtures.AbstractInstalledToolChainIntegrationSpec
import org.gradle.nativeplatform.fixtures.RequiresInstalledToolChain
import org.gradle.nativeplatform.fixtures.ToolChainRequirement
import org.gradle.nativeplatform.fixtures.app.IncrementalSwiftModifyExpectedOutputApp
import org.gradle.nativeplatform.fixtures.app.IncrementalSwiftModifyExpectedOutputAppWithLib
import org.gradle.nativeplatform.fixtures.app.IncrementalSwiftStaleCompileOutputApp
import org.gradle.nativeplatform.fixtures.app.IncrementalSwiftStaleCompileOutputLib
import org.gradle.nativeplatform.fixtures.app.IncrementalSwiftStaleLinkOutputApp
import org.gradle.nativeplatform.fixtures.app.IncrementalSwiftStaleLinkOutputAppWithLib
import org.gradle.nativeplatform.fixtures.app.IncrementalSwiftStaleLinkOutputLib
import org.gradle.nativeplatform.fixtures.app.SourceElement
import org.gradle.nativeplatform.fixtures.app.SwiftApp
import org.gradle.nativeplatform.fixtures.app.SwiftLib
import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.internal.VersionNumber
import spock.lang.IgnoreIf

// See https://github.com/gradle/dev-infrastructure/issues/538
@IgnoreIf({ OperatingSystem.current().isMacOsX() })
@RequiresInstalledToolChain(ToolChainRequirement.SWIFTC)
class SwiftIncrementalBuildIntegrationTest extends AbstractInstalledToolChainIntegrationSpec {
    def "rebuilds application when a single source file changes"() {
        settingsFile << "rootProject.name = 'app'"
        def app = new IncrementalSwiftModifyExpectedOutputApp()

        given:
        app.writeToProject(testDirectory)

        and:
        buildFile << """
            apply plugin: 'swift-application'
         """

        when:
        succeeds "assemble"

        then:
        result.assertTasksExecuted(assembleAppTasks)
        result.assertTasksNotSkipped(assembleAppTasks)
        executable("build/exe/main/debug/App").exec().out == app.expectedOutput

        when:
        app.applyChangesToProject(testDirectory)
        succeeds "assemble"

        then:
        result.assertTasksExecuted(assembleAppTasks)
        result.assertTasksNotSkipped(assembleAppTasks)
        executable("build/exe/main/debug/App").exec().out == app.expectedAlternateOutput

        when:
        succeeds "assemble"

        then:
        result.assertTasksExecuted(assembleAppTasks)
        result.assertTasksSkipped(assembleAppTasks)
    }

    def "rebuilds application when a single source file in library changes"() {
        settingsFile << "include 'app', 'greeter'"
        def app = new IncrementalSwiftModifyExpectedOutputAppWithLib()

        given:
        buildFile << """
            project(':app') {
                apply plugin: 'swift-application'
                dependencies {
                    implementation project(':greeter')
                }
            }
            project(':greeter') {
                apply plugin: 'swift-library'
            }
"""
        app.library.writeToProject(file("greeter"))
        app.application.writeToProject(file("app"))

        when:
        succeeds "assemble"

        then:
        result.assertTasksExecuted(assembleAppAndLibTasks, ":assemble")
        result.assertTasksNotSkipped(assembleAppAndLibTasks)
        installation("app/build/install/main/debug").exec().out == app.expectedOutput

        when:
        app.library.applyChangesToProject(file('greeter'))
        succeeds "assemble"

        then:
        result.assertTasksExecuted(assembleAppAndLibTasks, ":assemble")
        result.assertTasksNotSkipped(assembleAppAndLibTasks)
        installation("app/build/install/main/debug").exec().out == app.alternateLibraryOutput

        when:
        succeeds "assemble"

        then:
        result.assertTasksExecuted(assembleAppAndLibTasks, ":assemble")
        result.assertTasksSkipped(assembleAppAndLibTasks, ":assemble")
    }

    def "removes stale object files for executable"() {
        settingsFile << "rootProject.name = 'app'"
        def app = new IncrementalSwiftStaleCompileOutputApp()
        def outputDirectory = file("build/obj/main/debug")
        def outputs = createCompilationOutputs(outputDirectory)

        given:
        app.writeToProject(testDirectory)

        and:
        buildFile << """
            apply plugin: 'swift-application'
         """

        and:
        outputs.snapshot { succeeds "assemble" }
        app.applyChangesToProject(testDirectory)

        expect:
        succeeds "assemble"
        result.assertTasksExecuted(assembleAppTasks)
        result.assertTasksNotSkipped(assembleAppTasks)

        outputs.deletedClasses("multiply", "sum")

        // See https://github.com/gradle/gradle-native/issues/1004
        if (toolchainUnderTest.version.major == 5) {
            outputs.recompiledClasses('renamed-sum')
        } else {
            outputs.recompiledClasses('greeter', 'renamed-sum', 'main')
        }

        outputDirectory.assertContainsDescendants(expectedIntermediateDescendants(app.alternate))
        installation("build/install/main/debug").exec().out == app.expectedAlternateOutput
    }

    def "removes stale object files for library"() {
        def lib = new IncrementalSwiftStaleCompileOutputLib()
        def outputDirectory = file("build/obj/main/debug")
        def outputs = createCompilationOutputs(outputDirectory)
        settingsFile << "rootProject.name = 'hello'"

        given:
        lib.writeToProject(testDirectory)

        and:
        buildFile << """
            apply plugin: 'swift-library'
         """

        and:
        outputs.snapshot { succeeds "assemble" }
        lib.applyChangesToProject(testDirectory)

        expect:
        succeeds "assemble"
        result.assertTasksExecuted(assembleLibTasks)
        result.assertTasksNotSkipped(assembleLibTasks)
        outputs.deletedClasses("multiply", "sum")

        // See https://github.com/gradle/gradle-native/issues/1004
        if (toolchainUnderTest.version.major == 5) {
            outputs.recompiledClasses('renamed-sum')
        } else {
            outputs.recompiledClasses('greeter', 'renamed-sum')
        }

        outputDirectory.assertContainsDescendants(expectedIntermediateDescendants(lib.alternate))
        sharedLibrary("build/lib/main/debug/Hello").assertExists()
    }

    def "skips compile and link tasks for executable when source doesn't change"() {
        given:
        def app = new SwiftApp()
        settingsFile << "rootProject.name = '${app.projectName}'"
        app.writeToProject(testDirectory)

        and:
        buildFile << """
            apply plugin: 'swift-application'
         """

        and:
        succeeds "assemble"

        expect:
        succeeds "assemble"
        result.assertTasksExecuted(assembleAppTasks)
        result.assertTasksSkipped(assembleAppTasks)

        installation("build/install/main/debug").exec().out == app.expectedOutput
    }

    def "skips compile and link tasks for library when source doesn't change"() {
        given:
        def lib = new SwiftLib()
        settingsFile << "rootProject.name = '${lib.projectName}'"
        lib.writeToProject(testDirectory)

        and:
        buildFile << """
            apply plugin: 'swift-library'
         """

        and:
        succeeds "assemble"

        expect:
        succeeds "assemble"
        result.assertTasksExecuted(assembleLibTasks)
        result.assertTasksSkipped(assembleLibTasks)

        sharedLibrary("build/lib/main/debug/${lib.moduleName}").assertExists()
    }

    def "removes stale installed executable and library file when all source files for executable are removed"() {
        settingsFile << "include 'app', 'greeter'"
        def app = new IncrementalSwiftStaleLinkOutputAppWithLib()
        def outputDirectory = file("greeter/build/obj/main/debug")
        def outputs = createCompilationOutputs(outputDirectory)

        given:
        buildFile << """
            project(':app') {
                apply plugin: 'swift-application'
                dependencies {
                    implementation project(':greeter')
                }
            }
            project(':greeter') {
                apply plugin: 'swift-library'
            }
        """
        app.library.writeToProject(file("greeter"))
        app.application.writeToProject(file("app"))

        when:
        outputs.snapshot {
            succeeds "assemble"
        }

        then:
        file("app/build/obj/main/debug").assertHasDescendants(expectedIntermediateDescendants(app.application.original))
        installation("app/build/install/main/debug").assertInstalled()

        sharedLibrary("greeter/build/lib/main/debug/Greeter").assertExists()
        outputDirectory.assertHasDescendants(expectedIntermediateDescendants(app.library.original))

        when:
        app.library.applyChangesToProject(file('greeter'))
        app.application.applyChangesToProject(file('app'))
        succeeds "assemble"

        then:
        result.assertTasksExecuted(assembleAppAndLibTasks, ":assemble")
        result.assertTasksNotSkipped(getAssembleAppTasks(":app"))
        result.assertTasksSkipped(":assemble", getAssembleLibTasks(":greeter"))

        executable("app/build/exe/main/debug/App").assertDoesNotExist()
        file("app/build/exe/main/debug").assertDoesNotExist()
        file("app/build/obj/main/debug").assertDoesNotExist()
        installation("app/build/install/main/debug").assertNotInstalled()

        outputs.noneRecompiled()
        sharedLibrary("greeter/build/lib/main/debug/Greeter").assertExists()
        file("greeter/build/obj/main/debug").assertHasDescendants(expectedIntermediateDescendants(app.library.alternate))
    }

    def "removes stale executable file when all source files are removed"() {
        settingsFile << "rootProject.name = 'app'"
        def app = new IncrementalSwiftStaleLinkOutputApp()
        given:
        buildFile << """
            apply plugin: 'swift-application'
        """
        app.writeToProject(testDirectory)

        when:
        succeeds "assemble"

        then:

        file("build/obj/main/debug").assertHasDescendants(expectedIntermediateDescendants(app.original))
        executable("build/exe/main/debug/App").assertExists()
        installation("build/install/main/debug").assertInstalled()

        when:
        app.applyChangesToProject(testDirectory)
        succeeds "assemble"

        then:
        result.assertTasksExecuted(assembleAppTasks)
        result.assertTasksNotSkipped(assembleAppTasks)

        executable("build/exe/main/debug/App").assertDoesNotExist()
        file("build/exe/main/debug").assertDoesNotExist()
        file("build/obj/main/debug").assertDoesNotExist()
        installation("build/install/main/debug").assertNotInstalled()
    }

    def "removes stale library file when all source files are removed"() {
        def lib = new IncrementalSwiftStaleLinkOutputLib()
        settingsFile << "rootProject.name = 'greeter'"

        given:
        lib.writeToProject(testDirectory)

        and:
        buildFile << """
            apply plugin: 'swift-library'
         """

        when:
        succeeds "assemble"

        then:
        sharedLibrary("build/lib/main/debug/Greeter").assertExists()
        file("build/obj/main/debug").assertHasDescendants(expectedIntermediateDescendants(lib.original))

        when:
        lib.applyChangesToProject(testDirectory)
        succeeds "assemble"

        then:
        result.assertTasksExecuted(assembleLibTasks)
        result.assertTasksNotSkipped(assembleLibTasks)

        sharedLibrary("build/lib/main/debug/Hello").assertDoesNotExist()
        file("build/lib/main/debug").assertDoesNotExist()
        file("build/obj/main/debug").assertDoesNotExist()
    }

    private List<String> expectedIntermediateDescendants(SourceElement sourceElement) {
        List<String> result = new ArrayList<String>()

        String sourceSetName = sourceElement.getSourceSetName()
        String intermediateFilesDirPath = "build/obj/main/debug"
        File intermediateFilesDir = file(intermediateFilesDirPath)
        for (SourceFile sourceFile : sourceElement.getFiles()) {
            def swiftFile = file("src", sourceSetName, sourceFile.path, sourceFile.name)
            result.add(objectFileFor(swiftFile, intermediateFilesDirPath).relativizeFrom(intermediateFilesDir).path)
            result.add(swiftmoduleFileFor(swiftFile).relativizeFrom(intermediateFilesDir).path)
            result.add(swiftdocFileFor(swiftFile).relativizeFrom(intermediateFilesDir).path)

            if (toolChain.version >= VersionNumber.parse("5.3")) {
                // Seems to be introduced by 5.3:
                // https://github.com/bazelbuild/rules_swift/issues/496
                result.add(swiftsourceinfoFileFor(swiftFile).relativizeFrom(intermediateFilesDir).path)
            }

            result.add(dependFileFor(swiftFile).relativizeFrom(intermediateFilesDir).path)
            result.add(swiftDepsFileFor(swiftFile).relativizeFrom(intermediateFilesDir).path)
        }
        if (toolChain.version >= VersionNumber.parse("4.2")) {
            result.add("module.swiftdeps~moduleonly")
        }

        result.add("module.swiftdeps")
        result.add("output-file-map.json")
        return result
    }

    def swiftmoduleFileFor(File sourceFile, String intermediateFilesDir = "build/obj/main/debug") {
        return intermediateFileFor(sourceFile, intermediateFilesDir, "~partial.swiftmodule")
    }

    def swiftsourceinfoFileFor(File sourceFile, String intermediateFilesDir = "build/obj/main/debug") {
        return intermediateFileFor(sourceFile, intermediateFilesDir, "~partial.swiftsourceinfo")
    }

    def swiftdocFileFor(File sourceFile, String intermediateFilesDir = "build/obj/main/debug") {
        return intermediateFileFor(sourceFile, intermediateFilesDir, "~partial.swiftdoc")
    }

    def swiftDepsFileFor(File sourceFile, String intermediateFilesDir = "build/obj/main/debug") {
        return intermediateFileFor(sourceFile, intermediateFilesDir, ".swiftdeps")
    }

    def dependFileFor(File sourceFile, String intermediateFilesDir = "build/obj/main/debug") {
        return intermediateFileFor(sourceFile, intermediateFilesDir, ".d")
    }

    private List<String> getCompileAndLinkTasks(String projectPath = "") {
        ["${projectPath}:compileDebugSwift", "${projectPath}:linkDebug"]
    }

    private List<String> getAssembleAppTasks(String projectPath = "") {
        getCompileAndLinkTasks(projectPath) + ["${projectPath}:installDebug", "${projectPath}:assemble"]
    }

    private List<String> getAssembleLibTasks(String projectPath = "") {
        getCompileAndLinkTasks(projectPath) + ["${projectPath}:assemble"]
    }

    private List<String> getAssembleAppAndLibTasks() {
        getAssembleLibTasks(":greeter") + getAssembleAppTasks(":app")
    }

    private CompilationOutputsFixture createCompilationOutputs(TestFile outputDirectory) {
        return new CompilationOutputsFixture(outputDirectory, ['.o'])
    }
}
