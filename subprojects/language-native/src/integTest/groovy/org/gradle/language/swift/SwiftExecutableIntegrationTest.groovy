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
import org.gradle.nativeplatform.fixtures.app.SwiftApp
import org.gradle.nativeplatform.fixtures.app.SwiftAppWithLibraries
import org.gradle.nativeplatform.fixtures.app.SwiftAppWithLibrary
import org.gradle.nativeplatform.fixtures.app.SwiftAppWithLibraryAndOptionalFeature
import org.gradle.nativeplatform.fixtures.app.SwiftAppWithOptionalFeature
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition

import static org.gradle.util.Matchers.containsText

@Requires(TestPrecondition.SWIFT_SUPPORT)
class SwiftExecutableIntegrationTest extends AbstractInstalledToolChainIntegrationSpec {
    def "skip compile, link and install tasks when no source"() {
        given:
        buildFile << """
            apply plugin: 'swift-executable'
        """

        expect:
        succeeds "assemble"
        result.assertTasksExecuted(":compileDebugSwift", ":linkDebug", ":installDebug", ":assemble")
        // TODO - should skip the task as NO-SOURCE
        result.assertTasksSkipped(":compileDebugSwift", ":linkDebug", ":installDebug", ":assemble")
    }

    def "build fails when compilation fails"() {
        given:
        buildFile << """
            apply plugin: 'swift-executable'
         """

        and:
        file("src/main/swift/broken.swift") << "broken!"

        expect:
        fails "assemble"
        failure.assertHasDescription("Execution failed for task ':compileDebugSwift'.")
        failure.assertHasCause("A build operation failed.")
        failure.assertThatCause(containsText("Swift compiler failed while compiling swift file(s)"))
    }

    def "sources are compiled and linked with Swift tools"() {
        settingsFile << "rootProject.name = 'app'"
        def app = new SwiftApp()

        given:
        app.writeToProject(testDirectory)

        and:
        buildFile << """
            apply plugin: 'swift-executable'
         """

        expect:
        succeeds "assemble"
        result.assertTasksExecuted(":compileDebugSwift", ":linkDebug", ":installDebug", ":assemble")

        executable("build/exe/main/debug/App").assertExists()
        installation("build/install/main/debug").exec().out == app.expectedOutput
    }

    def "can build debug and release variant of the executable"() {
        settingsFile << "rootProject.name = 'app'"
        def app = new SwiftAppWithOptionalFeature()

        given:
        app.writeToProject(testDirectory)

        and:
        buildFile << """
            apply plugin: 'swift-executable'
            compileReleaseSwift.compilerArgs = ['-DWITH_FEATURE']
         """

        expect:
        succeeds "installRelease"
        result.assertTasksExecuted(":compileReleaseSwift", ":linkRelease", ":installRelease")

        executable("build/exe/main/release/App").assertExists()
        executable("build/exe/main/release/App").exec().out == app.withFeatureEnabled().expectedOutput
        installation("build/install/main/release").exec().out == app.withFeatureEnabled().expectedOutput

        succeeds "installDebug"
        result.assertTasksExecuted(":compileDebugSwift", ":linkDebug", ":installDebug")

        executable("build/exe/main/debug/App").assertExists()
        executable("build/exe/main/debug/App").exec().out == app.withFeatureDisabled().expectedOutput
        installation("build/install/main/debug").exec().out == app.withFeatureDisabled().expectedOutput
    }

    def "ignores non-Swift source files in source directory"() {
        settingsFile << "rootProject.name = 'app'"
        def app = new SwiftApp()

        given:
        app.writeToProject(testDirectory)
        file("src/main/swift/ignore.cpp") << 'broken!'
        file("src/main/swift/ignore.c") << 'broken!'
        file("src/main/swift/ignore.m") << 'broken!'
        file("src/main/swift/ignore.h") << 'broken!'
        file("src/main/swift/ignore.java") << 'broken!'

        and:
        buildFile << """
            apply plugin: 'swift-executable'
         """

        expect:
        succeeds "assemble"
        result.assertTasksExecuted(":compileDebugSwift", ":linkDebug", ":installDebug", ":assemble")

        executable("build/exe/main/debug/App").assertExists()
        installation("build/install/main/debug").exec().out == app.expectedOutput
    }

    def "build logic can change source layout convention"() {
        settingsFile << "rootProject.name = 'app'"
        def app = new SwiftApp()

        given:
        app.writeToSourceDir(file("Sources"))
        file("src/main/swift/broken.swift") << "ignore me!"

        and:
        buildFile << """
            apply plugin: 'swift-executable'
            executable {
                source.from 'Sources'
            }
         """

        expect:
        succeeds "assemble"
        result.assertTasksExecuted(":compileDebugSwift", ":linkDebug", ":installDebug", ":assemble")

        file("build/obj/main/debug").assertIsDir()
        executable("build/exe/main/debug/App").assertExists()
        installation("build/install/main/debug").exec().out == app.expectedOutput
    }

    def "build logic can add individual source files"() {
        settingsFile << "rootProject.name = 'app'"
        def app = new SwiftApp()

        given:
        app.main.writeToSourceDir(file("src/main.swift"))
        app.greeter.writeToSourceDir(file("src/one.swift"))
        app.sum.writeToSourceDir(file("src/two.swift"))
        file("src/main/swift/broken.swift") << "ignore me!"

        and:
        buildFile << """
            apply plugin: 'swift-executable'
            executable {
                source {
                    from('src/main.swift')
                    from('src/one.swift')
                    from('src/two.swift')
                }
            }
         """

        expect:
        succeeds "assemble"
        result.assertTasksExecuted(":compileDebugSwift", ":linkDebug", ":installDebug", ":assemble")

        file("build/obj/main/debug").assertIsDir()
        executable("build/exe/main/debug/App").assertExists()
        installation("build/install/main/debug").exec().out == app.expectedOutput
    }

    def "build logic can change buildDir"() {
        settingsFile << "rootProject.name = 'app'"
        def app = new SwiftApp()

        given:
        app.writeToProject(testDirectory)

        and:
        buildFile << """
            apply plugin: 'swift-executable'
            buildDir = 'output'
         """

        expect:
        succeeds "assemble"
        result.assertTasksExecuted(":compileDebugSwift", ":linkDebug", ":installDebug", ":assemble")

        !file("build").exists()
        file("output/obj/main/debug").assertIsDir()
        executable("output/exe/main/debug/App").assertExists()
        installation("output/install/main/debug").exec().out == app.expectedOutput
    }

    def "build logic can define the module name"() {
        settingsFile << "rootProject.name = 'app'"
        def app = new SwiftApp()

        given:
        app.writeToProject(testDirectory)

        and:
        buildFile << """
            apply plugin: 'swift-executable'
            executable.module = 'TestApp'
         """

        expect:
        succeeds "assemble"
        result.assertTasksExecuted(":compileDebugSwift", ":linkDebug", ":installDebug", ":assemble")

        file("build/obj/main/debug").assertIsDir()
        executable("build/exe/main/debug/TestApp").assertExists()
        installation("build/install/main/debug").exec().out == app.expectedOutput
    }

    def "build logic can change task output locations"() {
        settingsFile << "rootProject.name = 'app'"
        def app = new SwiftApp()

        given:
        app.writeToProject(testDirectory)

        and:
        buildFile << """
            apply plugin: 'swift-executable'
            compileDebugSwift.objectFileDir = layout.buildDirectory.dir("object-files")
            linkDebug.binaryFile = layout.buildDirectory.file("exe/some-app.exe")
            installDebug.installDirectory = layout.buildDirectory.dir("some-app")
         """

        expect:
        succeeds "assemble"
        result.assertTasksExecuted(":compileDebugSwift", ":linkDebug", ":installDebug", ":assemble")

        file("build/object-files").assertIsDir()
        file("build/exe/some-app.exe").assertIsFile()
        installation("build/some-app").exec().out == app.expectedOutput
    }

    def "can compile and link against a library"() {
        settingsFile << "include 'app', 'greeter'"
        def app = new SwiftAppWithLibrary()

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

        expect:
        succeeds ":app:assemble"
        result.assertTasksExecuted(":greeter:compileDebugSwift", ":greeter:linkDebug", ":app:compileDebugSwift", ":app:linkDebug", ":app:installDebug", ":app:assemble")

        executable("app/build/exe/main/debug/App").assertExists()
        sharedLibrary("greeter/build/lib/main/debug/Greeter").assertExists()
        installation("app/build/install/main/debug").exec().out == app.expectedOutput
        sharedLibrary("app/build/install/main/debug/lib/Greeter").assertExists()
    }

    def "can compile and link against library with API dependencies"() {
        settingsFile << "include 'app', 'hello', 'log'"
        def app = new SwiftAppWithLibraries()

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
                    api project(':log')
                }
            }
            project(':log') {
                apply plugin: 'swift-library'
            }
"""
        app.library.writeToProject(file("hello"))
        app.logLibrary.writeToProject(file("log"))
        app.executable.writeToProject(file("app"))

        expect:
        succeeds ":app:assemble"

        result.assertTasksExecuted(":hello:compileDebugSwift", ":hello:linkDebug", ":log:compileDebugSwift", ":log:linkDebug", ":app:compileDebugSwift", ":app:linkDebug", ":app:installDebug", ":app:assemble")

        sharedLibrary("hello/build/lib/main/debug/Hello").assertExists()
        sharedLibrary("log/build/lib/main/debug/Log").assertExists()
        executable("app/build/exe/main/debug/App").assertExists()
        installation("app/build/install/main/debug").exec().out == app.expectedOutput
        sharedLibrary("app/build/install/main/debug/lib/Hello").assertExists()
        sharedLibrary("app/build/install/main/debug/lib/Log").assertExists()

        succeeds ":app:installRelease"

        result.assertTasksExecuted(":hello:compileReleaseSwift", ":hello:linkRelease", ":log:compileReleaseSwift", ":log:linkRelease", ":app:compileReleaseSwift", ":app:linkRelease", ":app:installRelease")

        sharedLibrary("hello/build/lib/main/release/Hello").assertExists()
        sharedLibrary("log/build/lib/main/release/Log").assertExists()
        executable("app/build/exe/main/release/App").assertExists()
        installation("app/build/install/main/release").exec().out == app.expectedOutput
    }

    def "can compile and link against a library with debug and release variants"() {
        settingsFile << "include 'app', 'hello', 'log'"
        def app = new SwiftAppWithLibraryAndOptionalFeature()

        given:
        buildFile << """
            project(':app') {
                apply plugin: 'swift-executable'
                dependencies {
                    implementation project(':hello')
                }
                compileReleaseSwift.compilerArgs = ['-DWITH_FEATURE']
            }
            project(':hello') {
                apply plugin: 'swift-library'
                library.module = 'Greeter'
                compileReleaseSwift.compilerArgs = ['-DWITH_FEATURE']
            }
"""
        app.library.writeToProject(file("hello"))
        app.executable.writeToProject(file("app"))

        expect:
        succeeds ":app:linkRelease"

        result.assertTasksExecuted(":hello:compileReleaseSwift", ":hello:linkRelease", ":app:compileReleaseSwift", ":app:linkRelease")

        sharedLibrary("hello/build/lib/main/release/Greeter").assertExists()
        executable("app/build/exe/main/release/App").exec().out == app.withFeatureEnabled().expectedOutput

        succeeds ":app:linkDebug"

        result.assertTasksExecuted(":hello:compileDebugSwift", ":hello:linkDebug", ":app:compileDebugSwift", ":app:linkDebug")

        sharedLibrary("hello/build/lib/main/debug/Greeter").assertExists()
        executable("app/build/exe/main/debug/App").exec().out == app.withFeatureDisabled().expectedOutput
    }

    def "honors changes to library buildDir"() {
        settingsFile << "include 'app', 'hello', 'log'"
        def app = new SwiftAppWithLibraries()

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
                    api project(':log')
                }
            }
            project(':log') {
                apply plugin: 'swift-library'
                buildDir = 'out'
            }
"""
        app.library.writeToProject(file("hello"))
        app.logLibrary.writeToProject(file("log"))
        app.executable.writeToProject(file("app"))

        expect:
        succeeds ":app:assemble"
        result.assertTasksExecuted(":hello:compileDebugSwift", ":hello:linkDebug", ":log:compileDebugSwift", ":log:linkDebug", ":app:compileDebugSwift", ":app:linkDebug", ":app:installDebug", ":app:assemble")

        !file("log/build").exists()
        sharedLibrary("hello/build/lib/main/debug/Hello").assertExists()
        sharedLibrary("log/out/lib/main/debug/Log").assertExists()
        executable("app/build/exe/main/debug/App").assertExists()
        installation("app/build/install/main/debug").exec().out == app.expectedOutput
        sharedLibrary("app/build/install/main/debug/lib/Hello").file.assertExists()
        sharedLibrary("app/build/install/main/debug/lib/Log").file.assertExists()
    }

    def "multiple components can share the same source directory"() {
        settingsFile << "include 'app', 'hello', 'log'"
        def app = new SwiftAppWithLibraries()

        given:
        buildFile << """
            project(':app') {
                apply plugin: 'swift-executable'
                dependencies {
                    implementation project(':hello')
                }
                executable {
                    source.from '../Sources/${app.main.sourceFile.name}'
                }
            }
            project(':hello') {
                apply plugin: 'swift-library'
                dependencies {
                    api project(':log')
                }
                library {
                    source.from '../Sources/${app.greeter.sourceFile.name}'
                }
            }
            project(':log') {
                apply plugin: 'swift-library'
                library {
                    source.from '../Sources/${app.logger.sourceFile.name}'
                }
            }
"""
        app.library.writeToSourceDir(file("Sources"))
        app.logLibrary.writeToSourceDir(file("Sources"))
        app.executable.writeToSourceDir(file("Sources"))

        expect:
        succeeds ":app:assemble"
        result.assertTasksExecuted(":hello:compileDebugSwift", ":hello:linkDebug", ":log:compileDebugSwift", ":log:linkDebug", ":app:compileDebugSwift", ":app:linkDebug", ":app:installDebug", ":app:assemble")

        sharedLibrary("hello/build/lib/main/debug/Hello").assertExists()
        sharedLibrary("log/build/lib/main/debug/Log").assertExists()
        executable("app/build/exe/main/debug/App").exec().out == app.expectedOutput
        sharedLibrary("app/build/install/main/debug/lib/Hello").file.assertExists()
        sharedLibrary("app/build/install/main/debug/lib/Log").file.assertExists()
    }

    def "can compile and link against libraries in included builds"() {
        settingsFile << """
            rootProject.name = 'app'
            includeBuild 'hello'
            includeBuild 'log'
        """
        file("hello/settings.gradle") << "rootProject.name = 'hello'"
        file("log/settings.gradle") << "rootProject.name = 'log'"

        def app = new SwiftAppWithLibraries()

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
                api 'test:log:1.4'
            }
        """
        file("log/build.gradle") << """
            apply plugin: 'swift-library'
            group = 'test'
        """

        app.library.writeToProject(file("hello"))
        app.logLibrary.writeToProject(file("log"))
        app.executable.writeToProject(testDirectory)

        expect:
        succeeds ":assemble"
        result.assertTasksExecuted(":hello:compileDebugSwift", ":hello:linkDebug", ":log:compileDebugSwift", ":log:linkDebug", ":compileDebugSwift", ":linkDebug", ":installDebug", ":assemble")

        sharedLibrary("hello/build/lib/main/debug/Hello").assertExists()
        sharedLibrary("log/build/lib/main/debug/Log").assertExists()
        executable("build/exe/main/debug/App").assertExists()
        installation("build/install/main/debug").exec().out == app.expectedOutput
        sharedLibrary("build/install/main/debug/lib/Hello").file.assertExists()
        sharedLibrary("build/install/main/debug/lib/Log").file.assertExists()
    }
}
