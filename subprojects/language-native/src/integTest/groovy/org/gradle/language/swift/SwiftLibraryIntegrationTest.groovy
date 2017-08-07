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

import org.gradle.nativeplatform.fixtures.AbstractInstalledToolChainIntegrationSpec
import org.gradle.nativeplatform.fixtures.app.SwiftAppWithLibraries
import org.gradle.nativeplatform.fixtures.app.SwiftLib
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition

import static org.gradle.util.Matchers.containsText

@Requires(TestPrecondition.SWIFT_SUPPORT)
class SwiftLibraryIntegrationTest extends AbstractInstalledToolChainIntegrationSpec {
    def "build fails when compilation fails"() {
        given:
        buildFile << """
            apply plugin: 'swift-library'
         """

        and:
        file("src/main/swift/broken.swift") << "broken!"

        expect:
        fails "assemble"
        failure.assertHasDescription("Execution failed for task ':compileSwift'.")
        failure.assertHasCause("A build operation failed.")
        failure.assertThatCause(containsText("Swift compiler failed while compiling swift file(s)"))
    }

    def "sources are compiled with Swift compiler"() {
        def lib = new SwiftLib()
        settingsFile << "rootProject.name = 'hello'"

        given:
        lib.writeToProject(testDirectory)

        and:
        buildFile << """
            apply plugin: 'swift-library'
         """

        expect:
        succeeds "assemble"
        result.assertTasksExecuted(":compileSwift", ":linkMain", ":assemble")
        sharedLibrary("build/lib/hello").assertExists()
    }

    def "build logic can change source layout convention"() {
        def lib = new SwiftLib()
        settingsFile << "rootProject.name = 'hello'"

        given:
        lib.writeToSourceDir(file("Sources"))
        file("src/main/swift/broken.swift") << "ignore me!"

        and:
        buildFile << """
            apply plugin: 'swift-library'
            library {
                source.from 'Sources'
            }
         """

        expect:
        succeeds "assemble"
        result.assertTasksExecuted(":compileSwift", ":linkMain", ":assemble")

        sharedLibrary("build/lib/hello").assertExists()
    }

    def "build logic can add individual source files"() {
        def lib = new SwiftLib()
        settingsFile << "rootProject.name = 'hello'"

        given:
        lib.greeter.writeToSourceDir(file("src/one.swift"))
        lib.sum.writeToSourceDir(file("src/two.swift"))
        file("src/main/swift/broken.swift") << "ignore me!"

        and:
        buildFile << """
            apply plugin: 'swift-library'
            library {
                source {
                    from('src/one.swift')
                    from('src/two.swift')
                }
            }
         """

        expect:
        succeeds "assemble"
        result.assertTasksExecuted(":compileSwift", ":linkMain", ":assemble")

        sharedLibrary("build/lib/hello").assertExists()
    }

    def "build logic can change buildDir"() {
        given:
        settingsFile << "rootProject.name = 'hello'"
        def lib = new SwiftLib()
        lib.writeToProject(testDirectory)

        and:
        buildFile << """
            apply plugin: 'swift-library'
            buildDir = 'output'
         """

        expect:
        succeeds "assemble"
        result.assertTasksExecuted(":compileSwift", ":linkMain", ":assemble")

        !file("build").exists()
        file("output/main/objs").assertIsDir()
        sharedLibrary("output/lib/hello").assertExists()
    }

    def "build logic can change task output locations"() {
        given:
        settingsFile << "rootProject.name = 'hello'"
        def lib = new SwiftLib()
        lib.writeToProject(testDirectory)

        and:
        buildFile << """
            apply plugin: 'swift-library'
            compileSwift.objectFileDirectory.set(layout.buildDirectory.dir("object-files"))
            linkMain.binaryFile.set(layout.buildDirectory.file("some-lib/main.bin"))
         """

        expect:
        succeeds "assemble"
        result.assertTasksExecuted(":compileSwift", ":linkMain", ":assemble")

        file("build/object-files").assertIsDir()
        file("build/some-lib/main.bin").assertIsFile()
    }

    def "can define public library"() {
        settingsFile << "rootProject.name = 'hello'"
        given:
        def lib = new SwiftLib()
        lib.writeToProject(testDirectory)

        and:
        buildFile << """
            apply plugin: 'swift-library'
         """

        expect:
        succeeds "assemble"
        result.assertTasksExecuted(":compileSwift", ":linkMain", ":assemble")
        sharedLibrary("build/lib/hello").assertExists()
        file("build/main/objs/hello.swiftmodule").assertExists()
    }

    def "can compile and link against another library"() {
        settingsFile << "include 'hello', 'log'"
        def app = new SwiftAppWithLibraries()

        given:
        buildFile << """
            project(':hello') {
                apply plugin: 'swift-library'
                dependencies {
                    implementation project(':log')
                }
            }
            project(':log') {
                apply plugin: 'swift-library'
            }
"""
        app.library.writeToProject(file("hello"))
        app.logLibrary.writeToProject(file("log"))

        expect:
        succeeds ":hello:assemble"
        result.assertTasksExecuted(":log:compileSwift", ":log:linkMain", ":hello:compileSwift", ":hello:linkMain", ":hello:assemble")
        sharedLibrary("hello/build/lib/hello").assertExists()
        sharedLibrary("log/build/lib/log").assertExists()
    }

    def "can change default module name and successfully link against library"() {
        settingsFile << "include 'lib1', 'lib2'"
        def app = new SwiftAppWithLibraries()

        given:
        buildFile << """
            project(':lib1') {
                apply plugin: 'swift-library'
                dependencies {
                    implementation project(':lib2')
                }
                tasks.withType(SwiftCompile)*.moduleName = 'hello'
            }
            project(':lib2') {
                apply plugin: 'swift-library'
                tasks.withType(SwiftCompile)*.moduleName = 'log'
            }
"""
        app.library.writeToProject(file("lib1"))
        app.logLibrary.writeToProject(file("lib2"))

        expect:
        succeeds ":lib1:assemble"
        result.assertTasksExecuted(":lib2:compileSwift", ":lib2:linkMain", ":lib1:compileSwift", ":lib1:linkMain", ":lib1:assemble")
        sharedLibrary("lib1/build/lib/lib1").assertExists()
        sharedLibrary("lib2/build/lib/lib2").assertExists()
    }
}
