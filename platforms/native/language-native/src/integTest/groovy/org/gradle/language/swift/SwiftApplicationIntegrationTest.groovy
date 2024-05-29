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


import org.gradle.nativeplatform.fixtures.RequiresInstalledToolChain
import org.gradle.nativeplatform.fixtures.ToolChainRequirement
import org.gradle.nativeplatform.fixtures.app.SwiftApp
import org.gradle.nativeplatform.fixtures.app.SwiftAppWithLibraries
import org.gradle.nativeplatform.fixtures.app.SwiftAppWithLibrary
import org.gradle.nativeplatform.fixtures.app.SwiftAppWithLibraryAndOptionalFeature
import org.gradle.nativeplatform.fixtures.app.SwiftAppWithOptionalFeature
import org.gradle.nativeplatform.fixtures.app.SwiftCompilerDetectingApp

@RequiresInstalledToolChain(ToolChainRequirement.SWIFTC)
class SwiftApplicationIntegrationTest extends AbstractSwiftIntegrationTest implements SwiftTaskNames {
    @Override
    protected List<String> getTasksToAssembleDevelopmentBinary(String variant) {
        return [":compileDebug${variant.capitalize()}Swift", ":linkDebug${variant.capitalize()}", ":installDebug${variant.capitalize()}"]
    }

    @Override
    protected SwiftApp getComponentUnderTest() {
        return new SwiftApp()
    }

    @Override
    protected void makeSingleProject() {
        buildFile << """
            apply plugin: 'swift-application'
        """
    }

    @Override
    String getDevelopmentBinaryCompileTask() {
        return ':compileDebugSwift'
    }

    @Override
    void assertComponentUnderTestWasBuilt() {
        executable("build/exe/main/debug/${componentUnderTest.moduleName}").assertExists()
        file("build/modules/main/debug/${componentUnderTest.moduleName}.swiftmodule").assertIsFile()
        installation("build/install/main/debug").exec().out == componentUnderTest.expectedOutput
    }

    @Override
    protected String getComponentUnderTestDsl() {
        return "application"
    }

    def "relinks when an upstream dependency changes in ABI compatible way"() {
        createDirs("app", "greeter")
        settingsFile << "include 'app', 'greeter'"
        def app = new SwiftAppWithLibrary()

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
        app.executable.writeToProject(file("app"))

        expect:
        succeeds ":app:assemble"
        result.assertTasksExecuted(":greeter:compileDebugSwift", ":greeter:linkDebug", ":app:compileDebugSwift", ":app:linkDebug", ":app:installDebug", ":app:assemble")
        installation("app/build/install/main/debug").exec().out == app.expectedOutput

        when:
        file("greeter/src/main/swift/greeter.swift").replace("Hello,", "Goodbye,")
        then:
        succeeds ":app:assemble"
        result.assertTasksExecuted(":greeter:compileDebugSwift", ":greeter:linkDebug", ":app:compileDebugSwift", ":app:linkDebug", ":app:installDebug", ":app:assemble")
        result.assertTaskSkipped(":app:compileDebugSwift")
        result.assertTasksNotSkipped(":greeter:compileDebugSwift", ":greeter:linkDebug", ":app:linkDebug", ":app:installDebug", ":app:assemble")
        installation("app/build/install/main/debug").exec().out == app.expectedOutput.replace("Hello", "Goodbye")
    }

    def "recompiles when an upstream dependency changes in non-ABI compatible way"() {
        createDirs("app", "greeter")
        settingsFile << "include 'app', 'greeter'"
        def app = new SwiftAppWithLibrary()

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
        app.executable.writeToProject(file("app"))

        expect:
        succeeds ":app:assemble"
        result.assertTasksExecuted(":greeter:compileDebugSwift", ":greeter:linkDebug", ":app:compileDebugSwift", ":app:linkDebug", ":app:installDebug", ":app:assemble")
        installation("app/build/install/main/debug").exec().out == app.expectedOutput

        when:
        file("greeter/src/main/swift/greeter.swift").replace("sayHello", "sayAloha")
        then:
        fails ":app:compileDebugSwift"
        failure.assertHasErrorOutput("value of type 'Greeter' has no member 'sayHello'")

        when:
        file("app/src/main/swift/main.swift").replace("sayHello", "sayAloha")
        then:
        succeeds ":app:assemble"
        result.assertTasksExecuted(":greeter:compileDebugSwift", ":greeter:linkDebug", ":app:compileDebugSwift", ":app:linkDebug", ":app:installDebug", ":app:assemble")
        result.assertTasksNotSkipped(":greeter:linkDebug", ":app:compileDebugSwift", ":app:linkDebug", ":app:installDebug", ":app:assemble")
        installation("app/build/install/main/debug").exec().out == app.expectedOutput
    }

    def "can build debug and release variant of the executable"() {
        given:
        def app = new SwiftAppWithOptionalFeature()
        def debugBinary = executable("build/exe/main/debug/App")
        def releaseBinary = executable("build/exe/main/release/App")
        settingsFile << "rootProject.name = 'app'"
        app.writeToProject(testDirectory)

        and:
        buildFile << """
            apply plugin: 'swift-application'
            application.binaries.get { it.optimized }.configure {
                compileTask.get().compilerArgs.add('-DWITH_FEATURE')
            }
         """

        expect:
        succeeds "assembleRelease"
        result.assertTasksExecuted(":compileReleaseSwift", ":linkRelease", ":extractSymbolsRelease", ":stripSymbolsRelease", ":installRelease", ":assembleRelease")

        releaseBinary.assertExists()
        releaseBinary.exec().out == app.withFeatureEnabled().expectedOutput
        installation("build/install/main/release").exec().out == app.withFeatureEnabled().expectedOutput
        releaseBinary.assertHasStrippedDebugSymbolsFor(['main.o', 'greeter.o'])

        succeeds "assembleDebug"
        result.assertTasksExecuted(":compileDebugSwift", ":linkDebug", ":installDebug", ":assembleDebug")

        file("build/modules/main/release/App.swiftmodule").assertIsFile()
        debugBinary.assertExists()
        debugBinary.exec().out == app.withFeatureDisabled().expectedOutput
        installation("build/install/main/debug").exec().out == app.withFeatureDisabled().expectedOutput
        debugBinary.assertHasDebugSymbolsFor(['main.o', 'greeter.o'])
    }

    def "can use executable file as task dependency"() {
        given:
        def app = new SwiftApp()
        settingsFile << "rootProject.name = '${app.projectName}'"
        app.writeToProject(testDirectory)

        and:
        buildFile << """
            apply plugin: 'swift-application'

            task buildDebug {
                dependsOn application.binaries.get { it.debuggable && !it.optimized }.map { it.executableFile }
            }
         """

        expect:
        succeeds "buildDebug"
        result.assertTasksExecuted(":compileDebugSwift", ":linkDebug", ':buildDebug')
        executable("build/exe/main/debug/App").assertExists()
    }

    def "can use objects as task dependency"() {
        given:
        def app = new SwiftApp()
        settingsFile << "rootProject.name = '${app.projectName}'"
        app.writeToProject(testDirectory)

        and:
        buildFile << """
            apply plugin: 'swift-application'

            task compileDebug {
                dependsOn application.binaries.get { it.debuggable && !it.optimized }.map { it.objects }
            }
         """

        expect:
        succeeds "compileDebug"
        result.assertTasksExecuted(":compileDebugSwift", ':compileDebug')
        executable("build/exe/main/debug/App").assertDoesNotExist()
        objectFiles(app.main)*.assertExists()
    }

    def "can use installDirectory as task dependency"() {
        given:
        def app = new SwiftApp()
        settingsFile << "rootProject.name = '${app.projectName}'"
        app.writeToProject(testDirectory)

        and:
        buildFile << """
            apply plugin: 'swift-application'

            task install {
                dependsOn application.binaries.get { it.debuggable && !it.optimized }.map { it.installDirectory }
            }
         """

        expect:
        succeeds "install"
        result.assertTasksExecuted(":compileDebugSwift", ":linkDebug", ':installDebug', ':install')
        installation("build/install/main/debug").exec().out == app.expectedOutput
    }

    def "ignores non-Swift source files in source directory"() {
        given:
        def app = new SwiftApp()
        settingsFile << "rootProject.name = '${app.projectName}'"
        app.writeToProject(testDirectory)

        file("src/main/swift/ignore.cpp") << 'broken!'
        file("src/main/swift/ignore.c") << 'broken!'
        file("src/main/swift/ignore.m") << 'broken!'
        file("src/main/swift/ignore.h") << 'broken!'
        file("src/main/swift/ignore.java") << 'broken!'

        and:
        buildFile << """
            apply plugin: 'swift-application'
         """

        expect:
        succeeds "assemble"
        result.assertTasksExecuted(":compileDebugSwift", ":linkDebug", ":installDebug", ":assemble")

        executable("build/exe/main/debug/App").assertExists()
        installation("build/install/main/debug").exec().out == app.expectedOutput
    }

    def "build logic can change source layout convention"() {
        given:
        def app = new SwiftApp()
        settingsFile << "rootProject.name = '${app.projectName}'"
        app.writeToSourceDir(file("Sources"))

        file("src/main/swift/broken.swift") << "ignore me!"

        and:
        buildFile << """
            apply plugin: 'swift-application'
            application {
                source.from 'Sources'
            }
         """

        expect:
        succeeds "assemble"
        result.assertTasksExecuted(":compileDebugSwift", ":linkDebug", ":installDebug", ":assemble")

        file("build/obj/main/debug").assertIsDir()
        executable("build/exe/main/debug/${app.moduleName}").assertExists()
        installation("build/install/main/debug").exec().out == app.expectedOutput
    }

    def "build logic can add individual source files"() {
        given:
        def app = new SwiftApp()
        settingsFile << "rootProject.name = '${app.projectName}'"
        app.writeToProject(testDirectory)

        app.main.writeToSourceDir(file("src/main.swift"))
        app.greeter.writeToSourceDir(file("src/one.swift"))
        app.sum.writeToSourceDir(file("src/two.swift"))
        file("src/main/swift/broken.swift") << "ignore me!"

        and:
        buildFile << """
            apply plugin: 'swift-application'
            application {
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
        given:
        def app = new SwiftApp()
        settingsFile << "rootProject.name = '${app.projectName}'"
        app.writeToProject(testDirectory)

        and:
        buildFile << """
            apply plugin: 'swift-application'
            buildDir = 'output'
         """

        expect:
        succeeds "assemble"
        result.assertTasksExecuted(":compileDebugSwift", ":linkDebug", ":installDebug", ":assemble")

        !file("build").exists()
        file("output/obj/main/debug").assertIsDir()
        executable("output/exe/main/debug/App").assertExists()
        file("output/modules/main/debug/App.swiftmodule").assertIsFile()
        installation("output/install/main/debug").exec().out == app.expectedOutput
    }

    def "build logic can define the module name"() {
        given:
        def app = new SwiftApp()
        settingsFile << "rootProject.name = '${app.projectName}'"
        app.writeToProject(testDirectory)

        and:
        buildFile << """
            apply plugin: 'swift-application'
            application.module = 'TestApp'
         """

        expect:
        succeeds "assemble"
        result.assertTasksExecuted(":compileDebugSwift", ":linkDebug", ":installDebug", ":assemble")

        file("build/obj/main/debug").assertIsDir()
        executable("build/exe/main/debug/TestApp").assertExists()
        installation("build/install/main/debug").exec().out == app.expectedOutput
    }

    def "build logic can change task output locations"() {
        given:
        def app = new SwiftApp()
        settingsFile << "rootProject.name = '${app.projectName}'"
        app.writeToProject(testDirectory)

        and:
        buildFile << """
            apply plugin: 'swift-application'

            application.binaries.configureEach {
                compileTask.get().objectFileDir = layout.buildDirectory.dir("object-files")
                compileTask.get().moduleFile = layout.buildDirectory.file("some-app.swiftmodule")
                linkTask.get().linkedFile = layout.buildDirectory.file("exe/some-app.exe")
                installTask.get().installDirectory = layout.buildDirectory.dir("some-app")
            }
         """

        expect:
        succeeds "assemble"
        result.assertTasksExecuted(":compileDebugSwift", ":linkDebug", ":installDebug", ":assemble")

        file("build/object-files").assertIsDir()
        file("build/exe/some-app.exe").assertIsFile()
        file("build/some-app.swiftmodule").assertIsFile()
        installation("build/some-app").exec().out == app.expectedOutput
    }

    def "can compile and link against a library"() {
        createDirs("app", "greeter")
        settingsFile << "include 'app', 'greeter'"
        def app = new SwiftAppWithLibrary()

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
        app.executable.writeToProject(file("app"))

        expect:
        succeeds ":app:assemble"
        result.assertTasksExecuted(":greeter:compileDebugSwift", ":greeter:linkDebug", ":app:compileDebugSwift", ":app:linkDebug", ":app:installDebug", ":app:assemble")

        executable("app/build/exe/main/debug/App").assertExists()
        sharedLibrary("greeter/build/lib/main/debug/Greeter").assertExists()
        def installation = installation("app/build/install/main/debug")
        installation.exec().out == app.expectedOutput
        installation.assertIncludesLibraries("Greeter")
    }

    def "can compile and link against a library specifying target machines"() {
        createDirs("app", "greeter")
        settingsFile << "include 'app', 'greeter'"
        def app = new SwiftAppWithLibrary()

        given:
        buildFile << """
            project(':app') {
                apply plugin: 'swift-application'
                dependencies {
                    implementation project(':greeter')
                }
                application {
                    targetMachines = [machines.macOS, machines.linux]
                }
            }
            project(':greeter') {
                apply plugin: 'swift-library'
                library {
                    targetMachines = [machines.macOS, machines.linux]
                }
            }
        """
        app.library.writeToProject(file("greeter"))
        app.executable.writeToProject(file("app"))

        expect:
        succeeds ":app:assemble"
        result.assertTasksExecuted(tasks(':greeter').withOperatingSystemFamily(currentOsFamilyName).debug.allToLink, tasks(':app').withOperatingSystemFamily(currentOsFamilyName).debug.allToInstall, ":app:assemble")

        executable("app/build/exe/main/debug/${currentOsFamilyName}/App").assertExists()
        sharedLibrary("greeter/build/lib/main/debug/${currentOsFamilyName}/Greeter").assertExists()
        def installation = installation("app/build/install/main/debug/${currentOsFamilyName}")
        installation.exec().out == app.expectedOutput
        installation.assertIncludesLibraries("Greeter")
    }

    def "fails when dependency library does not specify the same target machines"() {
        createDirs("app", "greeter")
        settingsFile << "include 'app', 'greeter'"
        def app = new SwiftAppWithLibrary()

        given:
        buildFile << """
            project(':app') {
                apply plugin: 'swift-application'
                application {
                    targetMachines = [machines.${currentHostOperatingSystemFamilyDsl}]
                    dependencies {
                        implementation project(':greeter')
                    }
                }
            }
            project(':greeter') {
                apply plugin: 'swift-library'
                library {
                    targetMachines = [machines.os('os-family')]
                }
            }
        """
        app.library.writeToProject(file("greeter"))
        app.executable.writeToProject(file("app"))

        expect:
        fails ":app:assemble"

        and:
        failure.assertHasCause("Could not resolve project :greeter.")
        failure.assertHasCause("No matching variant of project :greeter was found. The consumer was configured to find attribute 'org.gradle.native.architecture' with value 'x86-64', attribute 'org.gradle.native.debuggable' with value 'true', attribute 'org.gradle.native.operatingSystem' with value 'linux', attribute 'org.gradle.native.optimized' with value 'false', attribute 'org.gradle.usage' with value 'native-runtime' but:\n" +
                               "  - No variants exist.")
    }

    def "can compile and link against a static library"() {
        createDirs("app", "greeter")
        settingsFile << "include 'app', 'greeter'"
        def app = new SwiftAppWithLibrary()

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

                library.linkage = [Linkage.STATIC]
            }
"""
        app.library.writeToProject(file("greeter"))
        app.executable.writeToProject(file("app"))

        expect:
        succeeds ":app:assemble"
        result.assertTasksExecuted(":greeter:compileDebugSwift", ":greeter:createDebug", ":app:compileDebugSwift", ":app:linkDebug", ":app:installDebug", ":app:assemble")

        executable("app/build/exe/main/debug/App").assertExists()
        staticLibrary("greeter/build/lib/main/debug/Greeter").assertExists()
        def installation = installation("app/build/install/main/debug")
        installation.exec().out == app.expectedOutput
        installation.assertIncludesLibraries()
    }

    def "can compile and link against a library with both linkage defined"() {
        createDirs("app", "greeter")
        settingsFile << "include 'app', 'greeter'"
        def app = new SwiftAppWithLibrary()

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

                library.linkage = [Linkage.SHARED, Linkage.STATIC]
            }
"""
        app.library.writeToProject(file("greeter"))
        app.executable.writeToProject(file("app"))

        expect:
        succeeds ":app:assemble"
        result.assertTasksExecuted(":greeter:compileDebugSharedSwift", ":greeter:linkDebugShared", ":app:compileDebugSwift", ":app:linkDebug", ":app:installDebug", ":app:assemble")

        executable("app/build/exe/main/debug/App").assertExists()
        sharedLibrary("greeter/build/lib/main/debug/shared/Greeter").assertExists()
        def installation = installation("app/build/install/main/debug")
        installation.exec().out == app.expectedOutput
        installation.assertIncludesLibraries("Greeter")
    }

    def "can compile and link against library with API dependencies"() {
        createDirs("app", "hello", "log")
        settingsFile << "include 'app', 'hello', 'log'"
        def app = new SwiftAppWithLibraries()

        given:
        buildFile << """
            project(':app') {
                apply plugin: 'swift-application'
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
        app.application.writeToProject(file("app"))

        expect:
        succeeds ":app:assemble"

        result.assertTasksExecuted(":hello:compileDebugSwift", ":hello:linkDebug", ":log:compileDebugSwift", ":log:linkDebug", ":app:compileDebugSwift", ":app:linkDebug", ":app:installDebug", ":app:assemble")

        sharedLibrary("hello/build/lib/main/debug/Hello").assertExists()
        sharedLibrary("log/build/lib/main/debug/Log").assertExists()
        executable("app/build/exe/main/debug/App").assertExists()

        def installationDebug = installation("app/build/install/main/debug")
        installationDebug.exec().out == app.expectedOutput
        installationDebug.assertIncludesLibraries("Hello", "Log")

        succeeds ":app:assembleRelease"

        result.assertTasksExecuted(":hello:compileReleaseSwift", ":hello:linkRelease", ":hello:stripSymbolsRelease",
            ":log:compileReleaseSwift", ":log:linkRelease", ":log:stripSymbolsRelease",
            ":app:compileReleaseSwift", ":app:linkRelease", ":app:extractSymbolsRelease", ":app:stripSymbolsRelease", ":app:installRelease", ":app:assembleRelease")

        sharedLibrary("hello/build/lib/main/release/Hello").assertExists()
        sharedLibrary("log/build/lib/main/release/Log").assertExists()
        executable("app/build/exe/main/release/App").assertExists()
        installation("app/build/install/main/release").exec().out == app.expectedOutput
    }

    def "can compile and link against static library with API dependencies"() {
        createDirs("app", "hello", "log")
        settingsFile << "include 'app', 'hello', 'log'"
        def app = new SwiftAppWithLibraries()

        given:
        buildFile << """
            project(':app') {
                apply plugin: 'swift-application'
                dependencies {
                    implementation project(':hello')
                }
            }
            project(':hello') {
                apply plugin: 'swift-library'
                library.linkage = [Linkage.STATIC]
                dependencies {
                    api project(':log')
                }
            }
            project(':log') {
                apply plugin: 'swift-library'
                library.linkage = [Linkage.STATIC]
            }
"""
        app.library.writeToProject(file("hello"))
        app.logLibrary.writeToProject(file("log"))
        app.application.writeToProject(file("app"))

        expect:
        succeeds ":app:assemble"

        result.assertTasksExecuted(":hello:compileDebugSwift", ":hello:createDebug",
            ":log:compileDebugSwift", ":log:createDebug",
            ":app:compileDebugSwift", ":app:linkDebug", ":app:installDebug", ":app:assemble")

        staticLibrary("hello/build/lib/main/debug/Hello").assertExists()
        staticLibrary("log/build/lib/main/debug/Log").assertExists()
        executable("app/build/exe/main/debug/App").assertExists()
        def installationDebug = installation("app/build/install/main/debug")
        installationDebug.exec().out == app.expectedOutput
        installationDebug.assertIncludesLibraries()

        succeeds ":app:assembleRelease"

        result.assertTasksExecuted(":hello:compileReleaseSwift", ":hello:createRelease",
            ":log:compileReleaseSwift", ":log:createRelease",
            ":app:compileReleaseSwift", ":app:linkRelease", ":app:extractSymbolsRelease", ":app:stripSymbolsRelease", ":app:installRelease", ":app:assembleRelease")

        staticLibrary("hello/build/lib/main/release/Hello").assertExists()
        staticLibrary("log/build/lib/main/release/Log").assertExists()
        executable("app/build/exe/main/release/App").assertExists()
        installation("app/build/install/main/release").exec().out == app.expectedOutput
    }

    def "can compile and link against static library with API dependencies to shared library"() {
        createDirs("app", "hello", "log")
        settingsFile << "include 'app', 'hello', 'log'"
        def app = new SwiftAppWithLibraries()

        given:
        buildFile << """
            project(':app') {
                apply plugin: 'swift-application'
                dependencies {
                    implementation project(':hello')
                }
            }
            project(':hello') {
                apply plugin: 'swift-library'
                library.linkage = [Linkage.STATIC]
                dependencies {
                    api project(':log')
                }
            }
            project(':log') {
                apply plugin: 'swift-library'
                library.linkage = [Linkage.SHARED]
            }
"""
        app.library.writeToProject(file("hello"))
        app.logLibrary.writeToProject(file("log"))
        app.application.writeToProject(file("app"))

        expect:
        succeeds ":app:assemble"

        result.assertTasksExecuted(":hello:compileDebugSwift", ":hello:createDebug",
            ":log:compileDebugSwift", ":log:linkDebug",
            ":app:compileDebugSwift", ":app:linkDebug", ":app:installDebug", ":app:assemble")

        staticLibrary("hello/build/lib/main/debug/Hello").assertExists()
        sharedLibrary("log/build/lib/main/debug/Log").assertExists()
        executable("app/build/exe/main/debug/App").assertExists()
        def installationDebug = installation("app/build/install/main/debug")
        installationDebug.exec().out == app.expectedOutput
        installationDebug.assertIncludesLibraries("Log")

        succeeds ":app:assembleRelease"

        result.assertTasksExecuted(":hello:compileReleaseSwift", ":hello:createRelease",
            ":log:compileReleaseSwift", ":log:linkRelease", ":log:stripSymbolsRelease",
            ":app:compileReleaseSwift", ":app:linkRelease", ":app:extractSymbolsRelease", ":app:stripSymbolsRelease", ":app:installRelease", ":app:assembleRelease")

        staticLibrary("hello/build/lib/main/release/Hello").assertExists()
        sharedLibrary("log/build/lib/main/release/Log").assertExists()
        executable("app/build/exe/main/release/App").assertExists()
        installation("app/build/install/main/release").exec().out == app.expectedOutput
    }

    def "can compile and link against shared library with API dependencies to static library"() {
        createDirs("app", "hello", "log")
        settingsFile << "include 'app', 'hello', 'log'"
        def app = new SwiftAppWithLibraries()

        given:
        buildFile << """
            project(':app') {
                apply plugin: 'swift-application'
                dependencies {
                    implementation project(':hello')
                }
            }
            project(':hello') {
                apply plugin: 'swift-library'
                library.linkage = [Linkage.SHARED]
                dependencies {
                    api project(':log')
                }
            }
            project(':log') {
                apply plugin: 'swift-library'
                library.linkage = [Linkage.STATIC]
            }
"""
        app.library.writeToProject(file("hello"))
        app.logLibrary.writeToProject(file("log"))
        app.application.writeToProject(file("app"))

        expect:
        succeeds ":app:assemble"

        result.assertTasksExecuted(":hello:compileDebugSwift", ":hello:linkDebug",
            ":log:compileDebugSwift", ":log:createDebug",
            ":app:compileDebugSwift", ":app:linkDebug", ":app:installDebug", ":app:assemble")

        sharedLibrary("hello/build/lib/main/debug/Hello").assertExists()
        staticLibrary("log/build/lib/main/debug/Log").assertExists()
        executable("app/build/exe/main/debug/App").assertExists()
        def installationDebug = installation("app/build/install/main/debug")
        installationDebug.exec().out == app.expectedOutput
        installationDebug.assertIncludesLibraries("Hello")

        succeeds ":app:assembleRelease"

        result.assertTasksExecuted(":hello:compileReleaseSwift", ":hello:linkRelease", ":hello:stripSymbolsRelease",
            ":log:compileReleaseSwift", ":log:createRelease",
            ":app:compileReleaseSwift", ":app:linkRelease", ":app:extractSymbolsRelease", ":app:stripSymbolsRelease", ":app:installRelease", ":app:assembleRelease")

        sharedLibrary("hello/build/lib/main/release/Hello").assertExists()
        staticLibrary("log/build/lib/main/release/Log").assertExists()
        executable("app/build/exe/main/release/App").assertExists()
        installation("app/build/install/main/release").exec().out == app.expectedOutput
    }

    def "can compile and link against a library with debug and release variants"() {
        createDirs("app", "hello")
        settingsFile << "include 'app', 'hello'"
        def app = new SwiftAppWithLibraryAndOptionalFeature()

        given:
        buildFile << """
            project(':app') {
                apply plugin: 'swift-application'
                dependencies {
                    implementation project(':hello')
                }
                application.binaries.get { it.optimized }.configure {
                    compileTask.get().compilerArgs.add('-DWITH_FEATURE')
                }
            }
            project(':hello') {
                apply plugin: 'swift-library'
                library.module = 'Greeter'
                library.binaries.get { it.optimized }.configure {
                    compileTask.get().compilerArgs.add('-DWITH_FEATURE')
                }
            }
"""
        app.library.writeToProject(file("hello"))
        app.application.writeToProject(file("app"))

        expect:
        succeeds ":app:linkRelease"

        result.assertTasksExecuted(":hello:compileReleaseSwift", ":hello:linkRelease", ":hello:stripSymbolsRelease", ":app:compileReleaseSwift", ":app:linkRelease")

        sharedLibrary("hello/build/lib/main/release/Greeter").assertExists()
        sharedLibrary("hello/build/lib/main/release/Greeter").assertHasDebugSymbolsFor(['greeter.o'])
        executable("app/build/exe/main/release/App").assertHasDebugSymbolsFor(['main.o'])
        executable("app/build/exe/main/release/App").exec().out == app.withFeatureEnabled().expectedOutput

        succeeds ":app:linkDebug"

        result.assertTasksExecuted(":hello:compileDebugSwift", ":hello:linkDebug", ":app:compileDebugSwift", ":app:linkDebug")

        sharedLibrary("hello/build/lib/main/debug/Greeter").assertExists()
        sharedLibrary("hello/build/lib/main/debug/Greeter").assertHasDebugSymbolsFor(['greeter.o'])
        executable("app/build/exe/main/debug/App").assertHasDebugSymbolsFor(['main.o'])
        executable("app/build/exe/main/debug/App").exec().out == app.withFeatureDisabled().expectedOutput
    }

    def "can compile and link against a static library with debug and release variants"() {
        createDirs("app", "hello")
        settingsFile << "include 'app', 'hello'"
        def app = new SwiftAppWithLibraryAndOptionalFeature()

        given:
        buildFile << """
            project(':app') {
                apply plugin: 'swift-application'
                dependencies {
                    implementation project(':hello')
                }
                application.binaries.get { it.optimized }.configure {
                    compileTask.get().compilerArgs.add('-DWITH_FEATURE')
                }
            }
            project(':hello') {
                apply plugin: 'swift-library'
                library.module = 'Greeter'
                library.linkage = [Linkage.STATIC]
                library.binaries.get {it.optimized }.configure {
                    compileTask.get().compilerArgs.add('-DWITH_FEATURE')
                }
            }
"""
        app.library.writeToProject(file("hello"))
        app.application.writeToProject(file("app"))

        expect:
        succeeds ":app:linkRelease"

        result.assertTasksExecuted(":hello:compileReleaseSwift", ":hello:createRelease", ":app:compileReleaseSwift", ":app:linkRelease")

        staticLibrary("hello/build/lib/main/release/Greeter").assertExists()
        executable("app/build/exe/main/release/App").assertHasDebugSymbolsFor(['main.o', 'greeter.o'])
        executable("app/build/exe/main/release/App").exec().out == app.withFeatureEnabled().expectedOutput

        succeeds ":app:linkDebug"

        result.assertTasksExecuted(":hello:compileDebugSwift", ":hello:createDebug", ":app:compileDebugSwift", ":app:linkDebug")

        staticLibrary("hello/build/lib/main/debug/Greeter").assertExists()
        executable("app/build/exe/main/debug/App").assertHasDebugSymbolsFor(['main.o', 'greeter.o'])
        executable("app/build/exe/main/debug/App").exec().out == app.withFeatureDisabled().expectedOutput
    }

    def "honors changes to library buildDir"() {
        createDirs("app", "hello", "log")
        settingsFile << "include 'app', 'hello', 'log'"
        def app = new SwiftAppWithLibraries()

        given:
        buildFile << """
            project(':app') {
                apply plugin: 'swift-application'
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
        app.application.writeToProject(file("app"))

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
        createDirs("app", "hello", "log")
        settingsFile << "include 'app', 'hello', 'log'"
        def app = new SwiftAppWithLibraries()

        given:
        buildFile << """
            project(':app') {
                apply plugin: 'swift-application'
                dependencies {
                    implementation project(':hello')
                }
                application {
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
        app.application.writeToSourceDir(file("Sources"))

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
        createDirs("hello", "log")
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
            apply plugin: 'swift-application'
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
        app.application.writeToProject(testDirectory)

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

    def "can detect Swift compiler version"() {
        def app = new SwiftCompilerDetectingApp(toolChain.version.major)

        given:
        buildFile << """
            apply plugin: 'swift-application'
        """
        app.writeToProject(testDirectory)

        expect:
        succeeds ":assemble"
        result.assertTasksExecuted(":compileDebugSwift", ":linkDebug", ":installDebug", ":assemble")
        installation("build/install/main/debug").exec().out == app.expectedOutput
    }
}
