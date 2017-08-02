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
        executable("build/exe/app").assertExists()
        installation("build/install/app").exec().out == app.englishOutput
    }

    def "honors changes to buildDir"() {
        settingsFile << "rootProject.name = 'app'"
        def app = new SwiftHelloWorldApp()

        given:
        app.writeSources(file('src/main'))

        and:
        buildFile << """
            apply plugin: 'swift-executable'
            buildDir = 'output'
         """

        expect:
        succeeds "assemble"
        result.assertTasksExecuted(":compileSwift", ":linkMain", ":installMain", ":assemble")

        !file("build").exists()
        file("output/main/objs").assertIsDir()
        executable("output/exe/app").assertExists()
        installation("output/install/app").exec().out == app.englishOutput
    }

    def "honors changes to task output locations"() {
        settingsFile << "rootProject.name = 'app'"
        def app = new SwiftHelloWorldApp()

        given:
        app.writeSources(file('src/main'))

        and:
        buildFile << """
            apply plugin: 'swift-executable'
            compileSwift.objectFileDirectory.set(layout.buildDirectory.dir("object-files"))
            linkMain.binaryFile.set(layout.buildDirectory.file("exe/some-app.exe"))
            installMain.installDirectory.set(layout.buildDirectory.dir("some-app"))
         """

        expect:
        succeeds "assemble"
        result.assertTasksExecuted(":compileSwift", ":linkMain", ":installMain", ":assemble")

        file("build/exe/some-app.exe").assertExists()
        installation("build/some-app").exec().out == app.englishOutput
    }

    def "can compile and link against a library"() {
        settingsFile << "include 'app', 'greeter'"
        def app = new SwiftHelloWorldApp()

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
        app.library.writeSources(file("greeter/src/main"))
        app.executable.writeSources(file('app/src/main'))

        expect:
        succeeds ":app:assemble"
        result.assertTasksExecuted(":greeter:compileSwift", ":greeter:linkMain", ":app:compileSwift", ":app:linkMain", ":app:installMain", ":app:assemble")
        executable("app/build/exe/app").assertExists()
        sharedLibrary("greeter/build/lib/greeter").assertExists()
        installation("app/build/install/app").exec().out == app.englishOutput
        sharedLibrary("app/build/install/app/lib/greeter").file.assertExists()
    }

    def "can compile and link against library with API dependencies"() {
        settingsFile << "include 'app', 'hello', 'greeting'"
        def app = new ExeWithLibraryUsingSwiftLibraryHelloWorldApp()

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
                dependencies {
                    api project(':greeting')
                }
            }
            project(':greeting') {
                apply plugin: 'swift-library'
            }
"""
        app.library.writeSources(file("hello/src/main"))
        app.greetingsLibrary.writeSources(file("greeting/src/main"))
        app.executable.writeSources(file('app/src/main'))

        expect:
        succeeds ":app:assemble"
        result.assertTasksExecuted(":hello:compileSwift", ":hello:linkMain", ":greeting:compileSwift", ":greeting:linkMain", ":app:compileSwift", ":app:linkMain", ":app:installMain", ":app:assemble")
        sharedLibrary("hello/build/lib/hello").assertExists()
        sharedLibrary("greeting/build/lib/greeting").assertExists()
        executable("app/build/exe/app").exec().out == app.englishOutput
        sharedLibrary("app/build/install/app/lib/hello").file.assertExists()
        sharedLibrary("app/build/install/app/lib/greeting").file.assertExists()
    }

    def "honors changes to library buildDir"() {
        settingsFile << "include 'app', 'hello', 'greeting'"
        def app = new ExeWithLibraryUsingSwiftLibraryHelloWorldApp()

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
                dependencies {
                    api project(':greeting')
                }
            }
            project(':greeting') {
                apply plugin: 'swift-library'
                buildDir = 'out'
            }
"""
        app.library.writeSources(file("hello/src/main"))
        app.greetingsLibrary.writeSources(file("greeting/src/main"))
        app.executable.writeSources(file('app/src/main'))

        expect:
        succeeds ":app:assemble"
        result.assertTasksExecuted(":hello:compileSwift", ":hello:linkMain", ":greeting:compileSwift", ":greeting:linkMain", ":app:compileSwift", ":app:linkMain", ":app:installMain", ":app:assemble")

        !file("greeting/build").exists()
        sharedLibrary("hello/build/lib/hello").assertExists()
        sharedLibrary("greeting/out/lib/greeting").assertExists()
        executable("app/build/exe/app").exec().out == app.englishOutput
        sharedLibrary("app/build/install/app/lib/hello").file.assertExists()
        sharedLibrary("app/build/install/app/lib/greeting").file.assertExists()
    }

    def "can compile and link against libraries in included builds"() {
        settingsFile << """
            rootProject.name = 'app'
            includeBuild 'hello'
            includeBuild 'greeting'
        """
        file("hello/settings.gradle") << "rootProject.name = 'hello'"
        file("greeting/settings.gradle") << "rootProject.name = 'greeting'"

        def app = new ExeWithLibraryUsingSwiftLibraryHelloWorldApp()

        given:
        buildFile << """
            apply plugin: 'swift-executable'
            dependencies {
                implementation 'test:hello:1.2'
            }
        """
        file("hello/build.gradle") << """
            apply plugin: 'swift-library'
            group = 'test'
            dependencies {
                api 'test:greeting:1.4'
            }
        """
        file("greeting/build.gradle") << """
            apply plugin: 'swift-library'
            group = 'test'
        """

        app.library.writeSources(file("hello/src/main"))
        app.greetingsLibrary.writeSources(file("greeting/src/main"))
        app.executable.writeSources(file('src/main'))

        expect:
        succeeds ":assemble"
        result.assertTasksExecuted(":hello:compileSwift", ":hello:linkMain", ":greeting:compileSwift", ":greeting:linkMain", ":compileSwift", ":linkMain", ":installMain", ":assemble")
        sharedLibrary("hello/build/lib/hello").assertExists()
        sharedLibrary("greeting/build/lib/greeting").assertExists()
        executable("build/exe/app").assertExists()
        installation("build/install/app").exec().out == app.englishOutput
        sharedLibrary("build/install/app/lib/hello").file.assertExists()
        sharedLibrary("build/install/app/lib/greeting").file.assertExists()
    }
}
