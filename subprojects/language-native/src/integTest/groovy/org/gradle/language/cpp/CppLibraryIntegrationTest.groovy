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

package org.gradle.language.cpp

import org.gradle.nativeplatform.fixtures.app.CppAppWithLibraries
import org.gradle.nativeplatform.fixtures.app.CppAppWithLibrariesWithApiDependencies
import org.gradle.nativeplatform.fixtures.app.CppGreeterWithOptionalFeature
import org.gradle.nativeplatform.fixtures.app.CppLib
import org.hamcrest.Matchers

import static org.gradle.util.Matchers.containsText

class CppLibraryIntegrationTest extends AbstractCppInstalledToolChainIntegrationTest implements CppTaskNames {

    def "skip compile and link tasks when no source"() {
        given:
        buildFile << """
            apply plugin: 'cpp-library'
        """

        expect:
        succeeds "assemble"
        result.assertTasksExecuted(compileAndLinkTasks(debug), ":assemble")
        // TODO - should skip the task as NO-SOURCE
        result.assertTasksSkipped(compileAndLinkTasks(debug), ":assemble")
    }

    def "build fails when compilation fails"() {
        given:
        buildFile << """
            apply plugin: 'cpp-library'
         """

        and:
        file("src/main/cpp/broken.cpp") << """
        #include <iostream>

        'broken
"""

        expect:
        fails "assemble"
        failure.assertHasDescription("Execution failed for task ':compileDebugCpp'.")
        failure.assertHasCause("A build operation failed.")
        failure.assertThatCause(containsText("C++ compiler failed while compiling broken.cpp"))
    }

    def "finds C++ system headers"() {
        given:
        buildFile << """
            apply plugin: 'cpp-library'
         """

        and:
        file("src/main/cpp/includingIoStream.cpp") << """
            #include <iostream>
        """

        when:
        run "assemble"

        then:
        file('build/dependDebugCpp/inputs.txt').text.contains('iostream')
    }

    def "sources are compiled with C++ compiler"() {
        given:
        settingsFile << "rootProject.name = 'hello'"
        def lib = new CppLib()
        lib.writeToProject(testDirectory)

        and:
        buildFile << """
            apply plugin: 'cpp-library'
         """

        expect:
        succeeds "assemble"
        result.assertTasksExecuted(compileAndLinkTasks(debug), ":assemble")
        sharedLibrary("build/lib/main/debug/hello").assertExists()
    }

    def "can build debug and release variants of library"() {
        given:
        settingsFile << "rootProject.name = 'hello'"
        def lib = new CppGreeterWithOptionalFeature()
        lib.writeToProject(testDirectory)

        and:
        buildFile << """
            apply plugin: 'cpp-library'
            compileReleaseCpp.macros(WITH_FEATURE: "true")
         """

        expect:
        executer.withArgument("--info")
        succeeds "linkRelease"

        result.assertTasksExecuted(compileAndLinkTasks(release))
        sharedLibrary("build/lib/main/release/hello").assertExists()
        output.contains('compiling with feature enabled')

        executer.withArgument("--info")
        succeeds "linkDebug"

        result.assertTasksExecuted(compileAndLinkTasks(debug))
        sharedLibrary("build/lib/main/debug/hello").assertExists()
        !output.contains('compiling with feature enabled')
    }

    def "can use link file as task dependency"() {
        given:
        settingsFile << "rootProject.name = 'hello'"
        def lib = new CppLib()
        lib.writeToProject(testDirectory)

        and:
        buildFile << """
            apply plugin: 'cpp-library'
            
            task assembleDebug {
                dependsOn library.debugSharedLibrary.linkFile
            }
            task assembleRuntimeDebug {
                dependsOn library.debugSharedLibrary.runtimeFile
            }
         """

        expect:
        succeeds "assembleDebug"
        result.assertTasksExecuted(compileAndLinkTasks(debug), ":assembleDebug")
        sharedLibrary("build/lib/main/debug/hello").assertExists()

        succeeds "assembleRuntimeDebug"
        result.assertTasksExecuted(compileAndLinkTasks(debug), ":assembleRuntimeDebug")

    }

    def "can use objects as task dependency"() {
        given:
        settingsFile << "rootProject.name = 'hello'"
        def lib = new CppLib()
        lib.writeToProject(testDirectory)

        and:
        buildFile << """
            apply plugin: 'cpp-library'
            
            task compileDebug {
                dependsOn library.debugSharedLibrary.objects
            }
         """

        expect:
        succeeds "compileDebug"
        result.assertTasksExecuted(compileTasks(debug), ":compileDebug")
        objectFiles(lib.sources)*.assertExists()
    }

    def "build logic can change source layout convention"() {
        def lib = new CppLib()
        settingsFile << "rootProject.name = 'hello'"

        given:
        lib.sources.writeToSourceDir(file("srcs"))
        lib.privateHeaders.writeToSourceDir(file("include"))
        lib.publicHeaders.writeToSourceDir(file("pub"))
        file("src/main/public/${lib.greeter.header.sourceFile.name}") << "ignore me!"
        file("src/main/headers/${lib.greeter.privateHeader.sourceFile.name}") << "ignore me!"
        file("src/main/cpp/broken.cpp") << "ignore me!"

        and:
        buildFile << """
            apply plugin: 'cpp-library'
            library {
                source.from 'srcs'
                publicHeaders.from 'pub'
                privateHeaders.from 'include'
            }
         """

        expect:
        succeeds "assemble"
        result.assertTasksExecuted(compileAndLinkTasks(debug), ":assemble")

        sharedLibrary("build/lib/main/debug/hello").assertExists()
    }

    def "build logic can add individual source files"() {
        def lib = new CppLib()
        settingsFile << "rootProject.name = 'hello'"

        given:
        lib.headers.writeToProject(testDirectory)
        lib.greeter.source.writeToSourceDir(file("src/one.cpp"))
        lib.sum.source.writeToSourceDir(file("src/two.cpp"))
        file("src/main/cpp/broken.cpp") << "ignore me!"

        and:
        buildFile << """
            apply plugin: 'cpp-library'
            library {
                source {
                    from('src/one.cpp')
                    from('src/two.cpp')
                }
            }
         """

        expect:
        succeeds "assemble"
        result.assertTasksExecuted(compileAndLinkTasks(debug), ":assemble")

        sharedLibrary("build/lib/main/debug/hello").assertExists()
    }

    def "honors changes to buildDir"() {
        given:
        settingsFile << "rootProject.name = 'hello'"
        def lib = new CppLib()
        lib.writeToProject(testDirectory)

        and:
        buildFile << """
            apply plugin: 'cpp-library'
            buildDir = 'output'
         """

        expect:
        succeeds "assemble"
        result.assertTasksExecuted(compileAndLinkTasks(debug), ":assemble")

        !file("build").exists()
        file("output/obj/main/debug").assertIsDir()
        sharedLibrary("output/lib/main/debug/hello").assertExists()
    }

    def "honors changes to task output locations"() {
        given:
        settingsFile << "rootProject.name = 'hello'"
        def lib = new CppLib()
        lib.writeToProject(testDirectory)

        and:
        buildFile << """
            apply plugin: 'cpp-library'
            compileDebugCpp.objectFileDir = layout.buildDirectory.dir("object-files")
            linkDebug.binaryFile = layout.buildDirectory.file("shared/main.bin")
            if (linkDebug.importLibrary.present) {
                linkDebug.importLibrary = layout.buildDirectory.file("import/main.lib")
            }
         """

        expect:
        succeeds "assemble"
        result.assertTasksExecuted(compileAndLinkTasks(debug), ":assemble")

        file("build/object-files").assertIsDir()
        file("build/shared/main.bin").assertIsFile()
        if (toolChain.visualCpp) {
            file("build/import/main.lib").assertIsFile()
        }
    }

    def "library can define public and implementation headers"() {
        given:
        settingsFile << "rootProject.name = 'hello'"
        def lib = new CppLib()
        lib.greeter.header.writeToSourceDir(file("src/main/public"))
        lib.greeter.privateHeader.writeToSourceDir(file("src/main/headers"))
        lib.sum.header.writeToSourceDir(file("src/main/public"))
        lib.sources.writeToProject(testDirectory)

        and:
        buildFile << """
            apply plugin: 'cpp-library'
         """

        expect:
        succeeds "assemble"
        result.assertTasksExecuted(compileAndLinkTasks(debug), ":assemble")
        sharedLibrary("build/lib/main/debug/hello").assertExists()
    }

    def "can compile and link against implementation and api libraries"() {
        settingsFile << "include 'lib1', 'lib2', 'lib3'"
        def app = new CppAppWithLibrariesWithApiDependencies()

        given:
        buildFile << """
            project(':lib1') {
                apply plugin: 'cpp-library'
                dependencies {
                    api project(':lib2')
                    implementation project(':lib3')
                }
            }
            project(':lib2') {
                apply plugin: 'cpp-library'
            }
            project(':lib3') {
                apply plugin: 'cpp-library'
            }
"""
        app.deck.writeToProject(file("lib1"))
        app.card.writeToProject(file("lib2"))
        app.shuffle.writeToProject(file("lib3"))

        expect:
        succeeds ":lib1:assemble"

        result.assertTasksExecuted(compileAndLinkTasks([':lib3', ':lib2', ':lib1'], debug), ":lib1:assemble")
        sharedLibrary("lib1/build/lib/main/debug/lib1").assertExists()
        sharedLibrary("lib2/build/lib/main/debug/lib2").assertExists()
        sharedLibrary("lib3/build/lib/main/debug/lib3").assertExists()

        succeeds ":lib1:linkRelease"

        result.assertTasksExecuted compileAndLinkTasks([':lib3', ':lib2', ':lib1'], release)
        sharedLibrary("lib1/build/lib/main/release/lib1").assertExists()
        sharedLibrary("lib2/build/lib/main/release/lib2").assertExists()
        sharedLibrary("lib3/build/lib/main/release/lib3").assertExists()
    }

    def "private headers are not visible to consumer"() {
        def lib = new CppLib()

        given:
        settingsFile << "include 'greeter', 'consumer'"
        buildFile << """
            subprojects {
                apply plugin: 'cpp-library'
            }
            project(':consumer') {
                dependencies { implementation project(':greeter') }
            }
"""
        lib.sources.writeToProject(file('greeter'))
        lib.publicHeaders.writeToSourceDir(file('greeter/src/main/headers'))
        lib.privateHeaders.writeToSourceDir(file('greeter/src/main/headers'))
        file("consumer/src/main/cpp/main.cpp") << """
#include "greeter_consts.h"
"""

        when:
        fails(":consumer:compileDebugCpp")

        then:
        failure.assertHasDescription("Execution failed for task ':consumer:compileDebugCpp'.")
        failure.assertThatCause(Matchers.containsString("C++ compiler failed while compiling main.cpp."))

        when:
        buildFile << """
project(':greeter') {
    library.privateHeaders.from = []
    library.publicHeaders.from = ['src/main/headers']
}
"""

        then:
        succeeds(":consumer:compileDebugCpp")
    }

    def "implementation dependencies are not visible to consumer"() {
        def app = new CppAppWithLibraries()

        given:
        settingsFile << "include 'greeter', 'logger', 'consumer'"
        buildFile << """
            subprojects {
                apply plugin: 'cpp-library'
            }
            project(':greeter') {
                dependencies { implementation project(':logger') }
            }
            project(':consumer') {
                dependencies { implementation project(':greeter') }
            }
"""
        app.greeterLib.writeToProject(file('greeter'))
        app.loggerLib.writeToProject(file('logger'))
        file("consumer/src/main/cpp/main.cpp") << """
#include "logger.h"
"""

        when:
        fails(":consumer:compileDebugCpp")

        then:
        failure.assertHasDescription("Execution failed for task ':consumer:compileDebugCpp'.")
        failure.assertThatCause(Matchers.containsString("C++ compiler failed while compiling main.cpp."))

        when:
        buildFile.text = buildFile.text.replace("dependencies { implementation project(':logger')", "dependencies { api project(':logger')")

        then:
        succeeds(":consumer:compileDebugCpp")
    }

    def "can change default base name and successfully link against library"() {
        settingsFile << "include 'lib1', 'lib2'"
        def app = new CppAppWithLibraries()

        given:
        buildFile << """
            project(':lib1') {
                apply plugin: 'cpp-library'
                library {
                    baseName = 'hello'
                }
                dependencies {
                    implementation project(':lib2')
                }
            }
            project(':lib2') {
                apply plugin: 'cpp-library'
                library {
                    baseName = 'log'
                }
            }
"""
        app.greeterLib.writeToProject(file("lib1"))
        app.loggerLib.writeToProject(file("lib2"))

        expect:
        succeeds ":lib1:assemble"
        result.assertTasksExecuted(compileAndLinkTasks([':lib2', ':lib1'], debug), ":lib1:assemble")
        sharedLibrary("lib1/build/lib/main/debug/hello").assertExists()
        sharedLibrary("lib2/build/lib/main/debug/log").assertExists()
    }

}
