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
import org.gradle.nativeplatform.fixtures.NativeBinaryFixture
import org.gradle.nativeplatform.fixtures.RequiresInstalledToolChain
import org.gradle.nativeplatform.fixtures.ToolChainRequirement
import org.gradle.nativeplatform.fixtures.app.SwiftAppWithLibraries
import org.gradle.nativeplatform.fixtures.app.SwiftLib
import org.gradle.nativeplatform.fixtures.app.SwiftSingleFileLib

import static org.gradle.util.Matchers.containsText

@RequiresInstalledToolChain(ToolChainRequirement.SWIFTC)
class SwiftLibraryIntegrationTest extends AbstractInstalledToolChainIntegrationSpec {
    def "skip compile and link tasks when no source"() {
        given:
        buildFile << """
            apply plugin: 'swift-library'
        """

        expect:
        succeeds "assemble"
        result.assertTasksExecuted(":compileDebugSwift", ":linkDebug", ":assemble")
        // TODO - should skip the task as NO-SOURCE
        result.assertTasksSkipped(":compileDebugSwift", ":linkDebug", ":assemble")
    }

    def "build fails when compilation fails"() {
        given:
        buildFile << """
            apply plugin: 'swift-library'
         """

        and:
        file("src/main/swift/broken.swift") << "broken!"

        expect:
        fails "assemble"
        failure.assertHasDescription("Execution failed for task ':compileDebugSwift'.")
        failure.assertHasCause("A build operation failed.")
        failure.assertThatCause(containsText("Swift compiler failed while compiling swift file(s)"))
    }

    def "can build debug and release variants of library"() {
        given:
        def lib = new SwiftLib()
        settingsFile << "rootProject.name = '${lib.projectName}'"
        lib.writeToProject(testDirectory)

        and:
        buildFile << """
            apply plugin: 'swift-library'
         """

        when:
        succeeds "assembleDebug"

        then:
        result.assertTasksExecuted(":compileDebugSwift", ":linkDebug", ":assembleDebug")
        file("build/modules/main/debug/${lib.moduleName}.swiftmodule").assertIsFile()
        sharedLibrary("build/lib/main/debug/${lib.moduleName}").assertExists()

        when:
        succeeds "assembleRelease"

        then:
        result.assertTasksExecuted(":compileReleaseSwift", ":linkRelease", ":extractSymbolsRelease", ":stripSymbolsRelease", ":assembleRelease")
        file("build/modules/main/release/${lib.moduleName}.swiftmodule").assertIsFile()
        sharedLibrary("build/lib/main/release/${lib.moduleName}").assertExists()
        sharedLibrary("build/lib/main/release/${lib.moduleName}").assertHasStrippedDebugSymbolsFor(lib.sourceFileNames)
    }

    def "can use link file as task dependency"() {
        given:
        def lib = new SwiftLib()
        settingsFile << "rootProject.name = '${lib.projectName}'"
        lib.writeToProject(testDirectory)

        and:
        buildFile << """
            apply plugin: 'swift-library'

            task assembleLinkDebug {
                dependsOn library.binaries.getByName('mainDebug').map { it.linkFile }
            }
         """

        expect:
        succeeds "assembleLinkDebug"
        result.assertTasksExecuted(":compileDebugSwift", ":linkDebug", ":assembleLinkDebug")
        sharedLibrary("build/lib/main/debug/${lib.moduleName}").assertExists()
    }

    def "can use runtime file as task dependency"() {
        given:
        def lib = new SwiftLib()
        settingsFile << "rootProject.name = '${lib.projectName}'"
        lib.writeToProject(testDirectory)

        and:
        buildFile << """
            apply plugin: 'swift-library'

            task assembleRuntimeDebug {
                dependsOn library.binaries.getByName('mainDebug').map { it.runtimeFile }
            }
         """

        expect:
        succeeds "assembleRuntimeDebug"
        result.assertTasksExecuted(":compileDebugSwift", ":linkDebug", ":assembleRuntimeDebug")
        sharedLibrary("build/lib/main/debug/${lib.moduleName}").assertExists()
    }

    def "can use objects as task dependency"() {
        given:
        def lib = new SwiftLib()
        settingsFile << "rootProject.name = '${lib.projectName}'"
        lib.writeToProject(testDirectory)

        and:
        buildFile << """
            apply plugin: 'swift-library'

            task compileDebug {
                dependsOn library.binaries.getByName('mainDebug').map { it.objects }
            }
         """

        expect:
        succeeds "compileDebug"
        result.assertTasksExecuted(":compileDebugSwift", ":compileDebug")
        objectFiles(lib)*.assertExists()
        sharedLibrary("build/lib/main/debug/${lib.moduleName}").assertDoesNotExist()
    }

    def "build logic can change source layout convention"() {
        given:
        def lib = new SwiftLib()
        settingsFile << "rootProject.name = '${lib.projectName}'"
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
        result.assertTasksExecuted(":compileDebugSwift", ":linkDebug", ":assemble")
        sharedLibrary("build/lib/main/debug/${lib.moduleName}").assertExists()
    }

    def "build logic can add individual source files"() {
        given:
        def lib = new SwiftLib()
        settingsFile << "rootProject.name = '${lib.projectName}'"
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
        result.assertTasksExecuted(":compileDebugSwift", ":linkDebug", ":assemble")
        sharedLibrary("build/lib/main/debug/${lib.moduleName}").assertExists()
    }

    def "build logic can change buildDir"() {
        given:
        def lib = new SwiftLib()
        settingsFile << "rootProject.name = '${lib.projectName}'"
        lib.writeToProject(testDirectory)

        and:
        buildFile << """
            apply plugin: 'swift-library'
            buildDir = 'output'
         """

        expect:
        succeeds "assemble"
        result.assertTasksExecuted(":compileDebugSwift", ":linkDebug", ":assemble")

        !file("build").exists()
        file("output/obj/main/debug").assertIsDir()
        file("output/modules/main/debug/${lib.moduleName}.swiftmodule").assertIsFile()
        sharedLibrary("output/lib/main/debug/${lib.moduleName}").assertExists()
    }

    def "build logic can change task output locations"() {
        given:
        def lib = new SwiftLib()
        settingsFile << "rootProject.name = '${lib.projectName}'"
        lib.writeToProject(testDirectory)

        and:
        buildFile << """
            apply plugin: 'swift-library'
            library.binaries.getByName('mainDebug').configure {
                compileTask.get().objectFileDir = layout.buildDirectory.dir("object-files")
                compileTask.get().moduleFile = layout.buildDirectory.file("some-lib.swiftmodule")
                linkTask.get().linkedFile = layout.buildDirectory.file("some-lib/main.bin")
            }
         """

        expect:
        succeeds "assemble"
        result.assertTasksExecuted(":compileDebugSwift", ":linkDebug", ":assemble")

        file("build/object-files").assertIsDir()
        file("build/some-lib.swiftmodule").assertIsFile()
        file("build/some-lib/main.bin").assertIsFile()
    }

    def "can define public library"() {
        given:
        def lib = new SwiftLib()
        settingsFile << "rootProject.name = '${lib.projectName}'"
        lib.writeToProject(testDirectory)

        and:
        buildFile << """
            apply plugin: 'swift-library'
         """

        expect:
        succeeds "assemble"
        result.assertTasksExecuted(":compileDebugSwift", ":linkDebug", ":assemble")
        file("build/modules/main/debug/${lib.moduleName}.swiftmodule").assertExists()
        sharedLibrary("build/lib/main/debug/${lib.moduleName}").assertExists()
    }

    def "can compile and link against another library"() {
        createDirs("hello", "log")
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

        result.assertTasksExecuted(":log:compileDebugSwift", ":log:linkDebug", ":hello:compileDebugSwift", ":hello:linkDebug", ":hello:assemble")
        sharedLibrary("hello/build/lib/main/debug/Hello").assertExists()
        sharedLibrary("log/build/lib/main/debug/Log").assertExists()

        succeeds ":hello:assembleRelease"

        result.assertTasksExecuted(":log:compileReleaseSwift", ":log:linkRelease", ":log:stripSymbolsRelease", ":hello:compileReleaseSwift", ":hello:linkRelease", ":hello:extractSymbolsRelease", ":hello:stripSymbolsRelease", ":hello:assembleRelease")
        sharedLibrary("hello/build/lib/main/release/Hello").assertExists()
        sharedLibrary("hello/build/lib/main/release/Hello").assertHasStrippedDebugSymbolsFor(app.library.sourceFileNames)
        sharedLibrary("log/build/lib/main/release/Log").assertExists()
        sharedLibrary("log/build/lib/main/release/Log").assertHasDebugSymbolsFor(app.logLibrary.sourceFileNames)
    }

    def "can change default module name and successfully link against library"() {
        createDirs("lib1", "lib2")
        settingsFile << "include 'lib1', 'lib2'"
        def app = new SwiftAppWithLibraries()

        given:
        buildFile << """
            project(':lib1') {
                apply plugin: 'swift-library'
                library {
                    module.set('Hello')
                }
                dependencies {
                    implementation project(':lib2')
                }
            }
            project(':lib2') {
                apply plugin: 'swift-library'
                library {
                    module.set('Log')
                }
            }
"""
        app.library.writeToProject(file("lib1"))
        app.logLibrary.writeToProject(file("lib2"))

        expect:
        succeeds ":lib1:assemble"
        result.assertTasksExecuted(":lib2:compileDebugSwift", ":lib2:linkDebug", ":lib1:compileDebugSwift", ":lib1:linkDebug", ":lib1:assemble")
        sharedLibrary("lib1/build/lib/main/debug/Hello").assertExists()
        sharedLibrary("lib2/build/lib/main/debug/Log").assertExists()
    }

    def "doesn't have implicit _main symbols declared in the object file of single file Swift library"() {
        given:
        def lib = new SwiftSingleFileLib()
        settingsFile << "rootProject.name = '${lib.projectName}'"
        lib.writeToProject(testDirectory)

        and:
        buildFile << """
            apply plugin: 'swift-library'
         """

        expect:
        succeeds "assemble"
        result.assertTasksExecuted(":compileDebugSwift", ":linkDebug", ":assemble")
        assertMainSymbolIsAbsent(objectFiles(lib))
        assertMainSymbolIsAbsent(sharedLibrary("build/lib/main/debug/Greeter"))
    }

    def "fails when no linkage is specified"() {
        def library = new SwiftLib()
        buildFile << """
            apply plugin: 'swift-library'

            library {
                linkage = []
            }
        """
        settingsFile << """
            rootProject.name = 'foo'
        """
        library.writeToProject(testDirectory)

        when:
        fails('assemble')

        then:
        failure.assertHasCause('A linkage needs to be specified for the library.')
    }

    private static void assertMainSymbolIsAbsent(List<NativeBinaryFixture> binaries) {
        binaries.each {
            assertMainSymbolIsAbsent(it)
        }
    }

    private static void assertMainSymbolIsAbsent(NativeBinaryFixture binary) {
        assert binary.binaryInfo.listSymbols().every { it.name != '_main' }
    }
}
