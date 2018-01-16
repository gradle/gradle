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

import org.gradle.integtests.fixtures.SourceFile
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

@RequiresInstalledToolChain(ToolChainRequirement.SWIFT)
class SwiftIncrementalCompileIntegrationTest extends AbstractInstalledToolChainIntegrationSpec {
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
        result.assertTasksExecuted(":compileDebugSwift", ":linkDebug", ":installDebug", ":assemble")
        result.assertTasksNotSkipped(":compileDebugSwift", ":linkDebug", ":installDebug", ":assemble")
        executable("build/exe/main/debug/App").exec().out == app.expectedOutput

        when:
        app.applyChangesToProject(testDirectory)
        succeeds "assemble"

        then:
        result.assertTasksExecuted(":compileDebugSwift", ":linkDebug", ":installDebug", ":assemble")
        result.assertTasksNotSkipped(":compileDebugSwift", ":linkDebug", ":installDebug", ":assemble")
        executable("build/exe/main/debug/App").exec().out == app.expectedAlternateOutput

        when:
        succeeds "assemble"

        then:
        result.assertTasksExecuted(":compileDebugSwift", ":linkDebug", ":installDebug", ":assemble")
        result.assertTasksSkipped(":compileDebugSwift", ":linkDebug", ":installDebug", ":assemble")
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
        succeeds ":app:assemble"

        then:
        result.assertTasksExecuted(":greeter:compileDebugSwift", ":greeter:linkDebug", ":app:compileDebugSwift", ":app:linkDebug", ":app:installDebug", ":app:assemble")
        result.assertTasksNotSkipped(":greeter:compileDebugSwift", ":greeter:linkDebug", ":app:compileDebugSwift", ":app:linkDebug", ":app:installDebug", ":app:assemble")
        installation("app/build/install/main/debug").exec().out == app.expectedOutput

        when:
        app.library.applyChangesToProject(file('greeter'))
        succeeds ":app:assemble"

        then:
        result.assertTasksExecuted(":greeter:compileDebugSwift", ":greeter:linkDebug", ":app:compileDebugSwift", ":app:linkDebug", ":app:installDebug", ":app:assemble")
        result.assertTasksNotSkipped(":greeter:compileDebugSwift", ":greeter:linkDebug", ":app:compileDebugSwift", ":app:linkDebug", ":app:installDebug", ":app:assemble")
        installation("app/build/install/main/debug").exec().out == app.alternateLibraryOutput

        when:
        succeeds ":app:assemble"

        then:
        result.assertTasksExecuted(":greeter:compileDebugSwift", ":greeter:linkDebug", ":app:compileDebugSwift", ":app:linkDebug", ":app:installDebug", ":app:assemble")
        result.assertTasksSkipped(":greeter:compileDebugSwift", ":greeter:linkDebug", ":app:compileDebugSwift", ":app:linkDebug", ":app:installDebug", ":app:assemble")
    }

    def "removes stale object files for executable"() {
        settingsFile << "rootProject.name = 'app'"
        def app = new IncrementalSwiftStaleCompileOutputApp()

        given:
        app.writeToProject(testDirectory)

        and:
        buildFile << """
            apply plugin: 'swift-application'
         """

        and:
        succeeds "assemble"
        app.applyChangesToProject(testDirectory)

        expect:
        succeeds "assemble"
        result.assertTasksExecuted(":compileDebugSwift", ":linkDebug", ":installDebug", ":assemble")
        result.assertTasksNotSkipped(":compileDebugSwift", ":linkDebug", ":installDebug", ":assemble")

        file("build/obj/main/debug").assertHasDescendants(expectIntermediateDescendants(app.alternate))
        executable("build/exe/main/debug/App").assertExists()
        installation("build/install/main/debug").exec().out == app.expectedAlternateOutput
    }

    def "removes stale object files for library"() {
        def lib = new IncrementalSwiftStaleCompileOutputLib()
        settingsFile << "rootProject.name = 'hello'"

        given:
        lib.writeToProject(testDirectory)

        and:
        buildFile << """
            apply plugin: 'swift-library'
         """

        and:
        succeeds "assemble"
        lib.applyChangesToProject(testDirectory)

        expect:
        succeeds "assemble"
        result.assertTasksExecuted(":compileDebugSwift", ":linkDebug", ":assemble")
        result.assertTasksNotSkipped(":compileDebugSwift", ":linkDebug", ":assemble")

        file("build/obj/main/debug").assertHasDescendants(expectIntermediateDescendants(lib.alternate))
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
        result.assertTasksExecuted(":compileDebugSwift", ":linkDebug", ":installDebug", ":assemble")
        result.assertTasksSkipped(":compileDebugSwift", ":linkDebug", ":installDebug", ":assemble")

        executable("build/exe/main/debug/App").assertExists()
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
        result.assertTasksExecuted(":compileDebugSwift", ":linkDebug", ":assemble")
        result.assertTasksSkipped(":compileDebugSwift", ":linkDebug", ":assemble")

        sharedLibrary("build/lib/main/debug/${lib.moduleName}").assertExists()
    }

    def "removes stale installed executable and library file when all source files for executable are removed"() {
        settingsFile << "include 'app', 'greeter'"
        def app = new IncrementalSwiftStaleLinkOutputAppWithLib()

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
        executable("app/build/exe/main/debug/App").assertExists()
        file("app/build/obj/main/debug").assertHasDescendants(expectIntermediateDescendants(app.application.original))
        installation("app/build/install/main/debug").assertInstalled()

        sharedLibrary("greeter/build/lib/main/debug/Greeter").assertExists()
        file("greeter/build/obj/main/debug").assertHasDescendants(expectIntermediateDescendants(app.library.original))

        when:
        app.library.applyChangesToProject(file('greeter'))
        app.application.applyChangesToProject(file('app'))
        succeeds "assemble"

        then:
        result.assertTasksExecuted(":greeter:compileDebugSwift", ":greeter:linkDebug", ":greeter:assemble",
            ":app:compileDebugSwift", ":app:linkDebug", ":app:installDebug", ":app:assemble",
            ":assemble")
        result.assertTasksNotSkipped(":app:compileDebugSwift", ":app:linkDebug", ":app:installDebug", ":app:assemble")
        result.assertTasksSkipped(":assemble", ":greeter:compileDebugSwift", ":greeter:linkDebug", ":greeter:assemble")

        executable("app/build/exe/main/debug/App").assertDoesNotExist()
        file("app/build/exe/main/debug").assertHasDescendants()
        file("app/build/obj/main/debug").assertHasDescendants()
        installation("app/build/install/main/debug").assertNotInstalled()

        sharedLibrary("greeter/build/lib/main/debug/Greeter").assertExists()
        file("greeter/build/obj/main/debug").assertHasDescendants(expectIntermediateDescendants(app.library.alternate))
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
        executable("build/exe/main/debug/App").assertExists()
        file("build/obj/main/debug").assertHasDescendants(expectIntermediateDescendants(app.original))
        installation("build/install/main/debug").assertInstalled()

        when:
        app.applyChangesToProject(testDirectory)
        succeeds "assemble"

        then:
        result.assertTasksExecuted(":compileDebugSwift", ":linkDebug", ":installDebug", ":assemble")
        result.assertTasksNotSkipped(":compileDebugSwift", ":linkDebug", ":installDebug", ":assemble")

        executable("build/exe/main/debug/App").assertDoesNotExist()
        file("build/exe/main/debug").assertHasDescendants()
        file("build/obj/main/debug").assertHasDescendants()
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
        file("build/obj/main/debug").assertHasDescendants(expectIntermediateDescendants(lib.original))

        when:
        lib.applyChangesToProject(testDirectory)
        succeeds "assemble"

        then:
        result.assertTasksExecuted(":compileDebugSwift", ":linkDebug", ":assemble")
        result.assertTasksNotSkipped(":compileDebugSwift", ":linkDebug", ":assemble")

        sharedLibrary("build/lib/main/debug/Hello").assertDoesNotExist()
        file("build/lib/main/debug").assertHasDescendants()
        file("build/obj/main/debug").assertHasDescendants()
    }

    private List<String> expectIntermediateDescendants(SourceElement sourceElement) {
        List<String> result = new ArrayList<String>()

        String sourceSetName = sourceElement.getSourceSetName()
        String intermediateFilesDirPath = "build/obj/main/debug"
        File intermediateFilesDir = file(intermediateFilesDirPath)
        for (SourceFile sourceFile : sourceElement.getFiles()) {
            def swiftFile = file("src", sourceSetName, sourceFile.path, sourceFile.name)
            result.add(objectFileFor(swiftFile, intermediateFilesDirPath).relativizeFrom(intermediateFilesDir).path)
            result.add(swiftmoduleFileFor(swiftFile).relativizeFrom(intermediateFilesDir).path)
            result.add(swiftdocFileFor(swiftFile).relativizeFrom(intermediateFilesDir).path)
        }
        result.add("output-file-map.json")
        return result
    }

    def swiftmoduleFileFor(File sourceFile, String intermediateFilesDir = "build/obj/main/debug") {
        return intermediateFileFor(sourceFile, intermediateFilesDir, "~partial.swiftmodule")
    }

    def swiftdocFileFor(File sourceFile, String intermediateFilesDir = "build/obj/main/debug") {
        return intermediateFileFor(sourceFile, intermediateFilesDir, "~partial.swiftdoc")
    }
}
