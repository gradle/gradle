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
        result.assertTasksExecuted(":compileSwift", ":linkMain", ":installMain", ":assemble")
        executable("build/exe/app").exec().out == app.englishOutput
    }

    def "can compile and link against a library"() {
        settingsFile << "include 'app', 'Greeter'"
        def app = new SwiftHelloWorldApp()

        given:
        buildFile << """
            project(':app') {
                apply plugin: 'swift-executable'
                dependencies {
                    implementation project(':Greeter')
                }
            }
            project(':Greeter') {
                apply plugin: 'swift-module'
            }
"""
        app.library.sourceFiles.each { it.writeToFile(file("Greeter/src/main/swift/$it.name")) }
        app.executable.sourceFiles.each { it.writeToDir(file('app/src/main')) }
        def f = file('app/src/main/swift/main.swift')
        f.text = """import Greeter

${f.text}"""

        expect:
        succeeds ":app:assemble"
        result.assertTasksExecuted(":Greeter:compileSwift", ":Greeter:linkMain", ":app:compileSwift", ":app:linkMain", ":app:installMain", ":app:assemble")
        executable("app/build/exe/app").assertExists()
        sharedLibrary("Greeter/build/lib/Greeter").assertExists()
        installation("app/build/install/app").exec().out == app.englishOutput
        sharedLibrary("app/build/install/app/lib/Greeter").file.assertExists()
    }

    def "can compile and link against library with dependencies"() {
        settingsFile << "include 'app', 'Hello', 'Greeting'"
        def app = new ExeWithLibraryUsingSwiftLibraryHelloWorldApp()

        given:
        buildFile << """
            project(':app') {
                apply plugin: 'swift-executable'
                dependencies {
                    implementation project(':Hello')
                }
            }
            project(':Hello') {
                apply plugin: 'swift-module'
                dependencies {
                    api project(':Greeting')
                }
            }
            project(':Greeting') {
                apply plugin: 'swift-module'
            }
"""
        app.library.sourceFiles.each { it.writeToFile(file("Hello/src/main/swift/$it.name")) }
        app.greetingsLibrary.sourceFiles.each { it.writeToFile(file("Greeting/src/main/swift/$it.name")) }
        app.executable.sourceFiles.each { it.writeToDir(file('app/src/main')) }

        expect:
        succeeds ":app:assemble"
        result.assertTasksExecuted(":Hello:compileSwift", ":Hello:linkMain", ":Greeting:compileSwift", ":Greeting:linkMain", ":app:compileSwift", ":app:linkMain", ":app:installMain", ":app:assemble")
        sharedLibrary("Hello/build/lib/Hello").assertExists()
        sharedLibrary("Greeting/build/lib/Greeting").assertExists()
        executable("app/build/exe/app").exec().out == app.englishOutput
        sharedLibrary("app/build/install/app/lib/Hello").file.assertExists()
        sharedLibrary("app/build/install/app/lib/Greeting").file.assertExists()
    }

    def "can compile and link against libraries in included builds"() {
        settingsFile << """
            rootProject.name = 'app'
            includeBuild 'Hello'
            includeBuild 'Greeting'
        """
        file("Hello/settings.gradle") << "rootProject.name = 'Hello'"
        file("Greeting/settings.gradle") << "rootProject.name = 'Greeting'"

        def app = new ExeWithLibraryUsingSwiftLibraryHelloWorldApp()

        given:
        buildFile << """
            apply plugin: 'swift-executable'
            dependencies {
                implementation 'test:Hello:1.2'
            }
        """
        file("Hello/build.gradle") << """
            apply plugin: 'swift-module'
            group = 'test'
            dependencies {
                api 'test:Greeting:1.4'
            }
        """
        file("Greeting/build.gradle") << """
            apply plugin: 'swift-module'
            group = 'test'
        """

        app.library.sourceFiles.each { it.writeToFile(file("Hello/src/main/swift/$it.name")) }
        app.greetingsLibrary.sourceFiles.each { it.writeToFile(file("Greeting/src/main/swift/$it.name")) }
        app.executable.sourceFiles.each { it.writeToDir(file('src/main')) }

        expect:
        succeeds ":assemble"
        result.assertTasksExecuted(":Hello:compileSwift", ":Hello:linkMain", ":Greeting:compileSwift", ":Greeting:linkMain", ":compileSwift", ":linkMain", ":installMain", ":assemble")
        sharedLibrary("Hello/build/lib/Hello").assertExists()
        sharedLibrary("Greeting/build/lib/Greeting").assertExists()
        executable("build/exe/app").assertExists()
        installation("build/install/app").exec().out == app.englishOutput
        sharedLibrary("build/install/app/lib/Hello").file.assertExists()
        sharedLibrary("build/install/app/lib/Greeting").file.assertExists()
    }
}
