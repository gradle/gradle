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
import org.gradle.nativeplatform.fixtures.app.IncrementalSwiftModifyExpectedOutputApp
import org.gradle.nativeplatform.fixtures.app.IncrementalSwiftModifyExpectedOutputAppWithLib
import org.gradle.nativeplatform.fixtures.app.IncrementalSwiftStaleCompileOutputApp
import org.gradle.nativeplatform.fixtures.app.IncrementalSwiftStaleCompileOutputLib
import org.gradle.nativeplatform.fixtures.app.SourceElement
import org.gradle.nativeplatform.fixtures.app.SwiftApp
import org.gradle.nativeplatform.fixtures.app.SwiftLib
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition

@Requires(TestPrecondition.SWIFT_SUPPORT)
class SwiftIncrementalCompileIntegrationTest extends AbstractInstalledToolChainIntegrationSpec {
    def "rebuilds application when a single source file changes"() {
        settingsFile << "rootProject.name = 'app'"
        def app = new IncrementalSwiftModifyExpectedOutputApp()

        given:
        app.writeToProject(testDirectory)

        and:
        buildFile << """
            apply plugin: 'swift-executable'
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
                apply plugin: 'swift-executable'
                dependencies {
                    implementation project(':greeter')
                }
            }
            project(':greeter') {
                apply plugin: 'swift-library'
            }
"""
        app.library.writeToProject(file("greeter"))
        app.executable.writeToProject(file("app"))

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
        result.assertTasksNotSkipped(":greeter:compileDebugSwift", ":greeter:linkDebug", ":app:linkDebug", ":app:installDebug", ":app:assemble")
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
            apply plugin: 'swift-executable'
         """

        and:
        succeeds "assemble"
        app.applyChangesToProject(testDirectory)

        expect:
        succeeds "assemble"
        result.assertTasksExecuted(":compileDebugSwift", ":linkDebug", ":installDebug", ":assemble")
        result.assertTasksNotSkipped(":compileDebugSwift", ":linkDebug", ":installDebug", ":assemble")

        file("build/obj/main/debug").assertHasDescendants(expectIntermediateDescendants(app.alternate, app.moduleName))
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

        file("build/obj/main/debug").assertHasDescendants(expectIntermediateDescendants(lib.alternate, lib.moduleName))
        sharedLibrary("build/lib/main/debug/Hello").assertExists()
    }

    def "skips compile and link tasks for executable when source doesn't change"() {
        def app = new SwiftApp()
        settingsFile << "rootProject.name = 'app'"

        given:
        app.writeToProject(testDirectory)

        and:
        buildFile << """
            apply plugin: 'swift-executable'
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
        def lib = new SwiftLib()
        settingsFile << "rootProject.name = 'hello'"

        given:
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

        sharedLibrary("build/lib/main/debug/Hello").assertExists()
    }

    private List<String> expectIntermediateDescendants(SourceElement sourceElement, String moduleName) {
        List<String> result = new ArrayList<String>()

        String sourceSetName = sourceElement.getSourceSetName()
        String intermediateFilesDirPath = "build/obj/main/debug"
        File intermediateFilesDir = file(intermediateFilesDirPath)
        for (SourceFile sourceFile : sourceElement.getFiles()) {
            if (!sourceFile.getName().endsWith(".h")) {
                def cppFile = file("src", sourceSetName, sourceFile.path, sourceFile.name)
                result.add(objectFileFor(cppFile, intermediateFilesDirPath).relativizeFrom(intermediateFilesDir).path)
                result.add(swiftmoduleFileFor(cppFile).relativizeFrom(intermediateFilesDir).path)
                result.add(swiftdocFileFor(cppFile).relativizeFrom(intermediateFilesDir).path)
            }
        }
        result.add("output-file-map.json")
        result.add(moduleName + ".swiftmodule")
        result.add(moduleName + ".swiftdoc")
        return result
    }

    def swiftmoduleFileFor(File sourceFile, String intermediateFilesDir = "build/obj/main/debug") {
        return intermediateFileFor(sourceFile, intermediateFilesDir, "~partial.swiftmodule")
    }

    def swiftdocFileFor(File sourceFile, String intermediateFilesDir = "build/obj/main/debug") {
        return intermediateFileFor(sourceFile, intermediateFilesDir, "~partial.swiftdoc")
    }
}
