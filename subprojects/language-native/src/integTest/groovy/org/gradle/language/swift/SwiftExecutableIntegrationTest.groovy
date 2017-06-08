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
import org.gradle.nativeplatform.fixtures.NativeLanguageRequirement
import org.gradle.nativeplatform.fixtures.RequiresSupportedLanguage
import org.gradle.nativeplatform.fixtures.app.ExeWithLibraryUsingSwiftLibraryHelloWorldApp
import org.gradle.nativeplatform.fixtures.app.SwiftHelloWorldApp

import static org.gradle.util.Matchers.containsText

@RequiresSupportedLanguage(NativeLanguageRequirement.SWIFT)
class SwiftExecutableIntegrationTest extends AbstractInstalledToolChainIntegrationSpec {
    def helloWorldApp = new SwiftHelloWorldApp()

    def "build fails when compilation fails"() {
        given:
        buildFile << """
            apply plugin: 'swift-executable'
         """

        and:
        helloWorldApp.brokenFile.writeToDir(file("src/main"))

        expect:
        fails "assemble"
        failure.assertHasDescription("Execution failed for task ':compileSwift'.");
        failure.assertHasCause("A build operation failed.")
        failure.assertThatCause(containsText("Swift compiler failed while compiling swift file(s)"))
    }

    def "sources are compiled with Swift compiler"() {
        settingsFile << "rootProject.name = 'app'"
        def app = new SwiftHelloWorldApp()

        given:
        app.writeSources(file('src/main'))

        and:
        buildFile << """
            apply plugin: 'swift-executable'
         """

        expect:
        succeeds "assemble"
        result.assertTasksExecuted(":compileSwift", ":installMain", ":assemble")
        executable("build/exe/app").exec().out == app.englishOutput
    }

    def "can compile and link against a library"() {
        settingsFile << "include 'app', 'hello'"
        def app = new SwiftHelloWorldApp()

        given:
        buildFile << """
            project(':app') {
                apply plugin: 'swift-executable'
                dependencies {
                    implementation project(':hello')
                }
            }
            project(':hello') {
                apply plugin: 'swift-library'

                tasks.withType(SwiftCompile)*.moduleName = 'Greeter'
            }
"""
        app.library.sourceFiles.each { it.writeToFile(file("hello/src/main/swift/$it.name")) }
        app.executable.sourceFiles.each { it.writeToDir(file('app/src/main')) }
        def f = file('app/src/main/swift/main.swift')
        f.text = """import Greeter

${f.text}"""

        expect:
        succeeds ":app:assemble"
        result.assertTasksExecuted(":hello:compileSwift", ":app:compileSwift", ":app:installMain", ":app:assemble")
        executable("app/build/exe/app").assertExists()
        sharedLibrary("hello/build/lib/hello").assertExists()
        installation("app/build/install/app").exec().out == app.englishOutput
        sharedLibrary("app/build/install/app/lib/hello").file.assertExists()
    }

    def "can compile and link against library with dependencies"() {
        settingsFile << "include 'app', 'lib1', 'lib2'"
        def app = new ExeWithLibraryUsingSwiftLibraryHelloWorldApp()

        given:
        buildFile << """
            project(':app') {
                apply plugin: 'swift-executable'
                dependencies {
                    implementation project(':lib1')
                    swiftImportPath project(':lib2')  // TODO(daniel): Not sure why this is required
                }
            }
            project(':lib1') {
                apply plugin: 'swift-library'
                dependencies {
                    implementation project(':lib2')
                }

                tasks.withType(SwiftCompile)*.moduleName = 'Hello'
            }
            project(':lib2') {
                apply plugin: 'swift-library'

                tasks.withType(SwiftCompile)*.moduleName = 'Greeting'
            }
"""
        app.library.sourceFiles.each { it.writeToFile(file("lib1/src/main/swift/$it.name")) }
        app.greetingsLibrary.sourceFiles.each { it.writeToFile(file("lib2/src/main/swift/$it.name")) }
        app.executable.sourceFiles.each { it.writeToDir(file('app/src/main')) }

        expect:
        succeeds ":app:assemble"
        result.assertTasksExecuted(":lib1:compileSwift", ":lib2:compileSwift", ":app:compileSwift", ":app:installMain", ":app:assemble")
        sharedLibrary("lib1/build/lib/lib1").assertExists()
        sharedLibrary("lib2/build/lib/lib2").assertExists()
        executable("app/build/exe/app").exec().out == app.englishOutput
        sharedLibrary("app/build/install/app/lib/lib1").file.assertExists()
        sharedLibrary("app/build/install/app/lib/lib2").file.assertExists()
    }


    def "can compile and link against libraries in included builds"() {
        settingsFile << """
            rootProject.name = 'app'
            includeBuild 'lib1'
            includeBuild 'lib2'
        """
        file("lib1/settings.gradle") << "rootProject.name = 'lib1'"
        file("lib2/settings.gradle") << "rootProject.name = 'lib2'"

        def app = new ExeWithLibraryUsingSwiftLibraryHelloWorldApp()

        given:
        buildFile << """
            apply plugin: 'swift-executable'
            dependencies {
                implementation 'test:lib1:1.2'
                swiftImportPath 'test:lib2:1.4'  // TODO(daniel): Not sure why this is required
            }
        """
        file("lib1/build.gradle") << """
            apply plugin: 'swift-library'
            group = 'test'
            dependencies {
                implementation 'test:lib2:1.4'
            }

            tasks.withType(SwiftCompile)*.moduleName = 'Hello'
        """
        file("lib2/build.gradle") << """
            apply plugin: 'swift-library'
            group = 'test'

            tasks.withType(SwiftCompile)*.moduleName = 'Greeting'
        """

        app.library.sourceFiles.each { it.writeToFile(file("lib1/src/main/swift/$it.name")) }
        app.greetingsLibrary.sourceFiles.each { it.writeToFile(file("lib2/src/main/swift/$it.name")) }
        app.executable.sourceFiles.each { it.writeToDir(file('src/main')) }

        expect:
        succeeds ":assemble"
        result.assertTasksExecuted(":lib1:compileSwift", ":lib2:compileSwift", ":compileSwift", ":installMain", ":assemble")
        sharedLibrary("lib1/build/lib/lib1").assertExists()
        sharedLibrary("lib2/build/lib/lib2").assertExists()
        executable("build/exe/app").assertExists()
        installation("build/install/app").exec().out == app.englishOutput
        sharedLibrary("build/install/app/lib/lib1").file.assertExists()
        sharedLibrary("build/install/app/lib/lib2").file.assertExists()
    }
}
