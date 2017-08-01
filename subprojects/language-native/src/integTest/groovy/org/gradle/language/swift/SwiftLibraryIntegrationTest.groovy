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
import org.gradle.nativeplatform.fixtures.app.ExeWithLibraryUsingSwiftLibraryHelloWorldApp
import org.gradle.nativeplatform.fixtures.app.SwiftHelloWorldApp
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition

import static org.gradle.util.Matchers.containsText

@Requires(TestPrecondition.SWIFT_SUPPORT)
class SwiftLibraryIntegrationTest extends AbstractInstalledToolChainIntegrationSpec {
    def "build fails when compilation fails"() {
        def app = new SwiftHelloWorldApp()

        given:
        buildFile << """
            apply plugin: 'swift-library'
         """

        and:
        app.brokenFile.writeToDir(file("src/main"))

        expect:
        fails "assemble"
        failure.assertHasDescription("Execution failed for task ':compileSwift'.");
        failure.assertHasCause("A build operation failed.")
        failure.assertThatCause(containsText("Swift compiler failed while compiling swift file(s)"))
    }

    def "sources are compiled with Swift compiler"() {
        def app = new SwiftHelloWorldApp()
        settingsFile << "rootProject.name = 'hello'"

        given:
        app.library.writeSources(file('src/main'))

        and:
        buildFile << """
            apply plugin: 'swift-library'
         """

        expect:
        succeeds "assemble"
        result.assertTasksExecuted(":compileSwift", ":linkMain", ":assemble")
        sharedLibrary("build/lib/hello").assertExists()
    }

    def "honors changes to buildDir"() {
        given:
        settingsFile << "rootProject.name = 'hello'"
        def app = new SwiftHelloWorldApp()
        app.library.writeSources(file('src/main'))

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

    def "honors changes to task output locations"() {
        given:
        settingsFile << "rootProject.name = 'hello'"
        def app = new SwiftHelloWorldApp()
        app.library.writeSources(file('src/main'))

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
        def app = new SwiftHelloWorldApp()
        app.library.writeSources(file("src/main"))

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
        settingsFile << "include 'hello', 'greeting'"
        def app = new ExeWithLibraryUsingSwiftLibraryHelloWorldApp()

        given:
        buildFile << """
            project(':hello') {
                apply plugin: 'swift-library'
                dependencies {
                    implementation project(':greeting')
                }
            }
            project(':greeting') {
                apply plugin: 'swift-library'
            }
"""
        app.library.writeSources(file("hello/src/main"))
        app.greetingsLibrary.writeSources(file("greeting/src/main"))

        expect:
        succeeds ":hello:assemble"
        result.assertTasksExecuted(":greeting:compileSwift", ":greeting:linkMain", ":hello:compileSwift", ":hello:linkMain", ":hello:assemble")
        sharedLibrary("hello/build/lib/hello").assertExists()
        sharedLibrary("greeting/build/lib/greeting").assertExists()
    }

    def "can change default module name and successfully link against library"() {
        settingsFile << "include 'lib1', 'lib2'"
        def app = new ExeWithLibraryUsingSwiftLibraryHelloWorldApp()

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
                tasks.withType(SwiftCompile)*.moduleName = 'greeting'
            }
"""
        app.library.writeSources(file("lib1/src/main"))
        app.greetingsLibrary.writeSources(file("lib2/src/main"))

        expect:
        succeeds ":lib1:assemble"
        result.assertTasksExecuted(":lib2:compileSwift", ":lib2:linkMain", ":lib1:compileSwift", ":lib1:linkMain", ":lib1:assemble")
        sharedLibrary("lib1/build/lib/lib1").assertExists()
        sharedLibrary("lib2/build/lib/lib2").assertExists()
    }
}
