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

import org.gradle.nativeplatform.fixtures.RequiresInstalledToolChain
import org.gradle.nativeplatform.fixtures.ToolChainRequirement
import org.gradle.nativeplatform.fixtures.app.CppAppWithLibraries
import org.gradle.nativeplatform.fixtures.app.CppAppWithLibrariesWithApiDependencies
import org.gradle.nativeplatform.fixtures.app.CppGreeterWithOptionalFeature
import org.gradle.nativeplatform.fixtures.app.CppLib
import org.gradle.nativeplatform.fixtures.app.SourceElement
import org.hamcrest.CoreMatchers

import static org.gradle.util.Matchers.containsText

class CppLibraryIntegrationTest extends AbstractCppIntegrationTest implements CppTaskNames {

    @Override
    protected String getComponentUnderTestDsl() {
        return "library"
    }

    @Override
    protected List<String> getTasksToAssembleDevelopmentBinary(String variant) {
        return [":compileDebug${variant.capitalize()}Cpp", ":linkDebug${variant.capitalize()}"]
    }

    @Override
    protected String getDevelopmentBinaryCompileTask() {
        return ":compileDebugCpp"
    }

    @Override
    protected void makeSingleProject() {
        buildFile << """
            apply plugin: 'cpp-library'
        """
    }

    @Override
    protected SourceElement getComponentUnderTest() {
        return new CppLib()
    }

    def "skip compile and link tasks when no source"() {
        given:
        buildFile << """
            apply plugin: 'cpp-library'
        """

        expect:
        succeeds "assemble"
        result.assertTasksExecuted(tasks.debug.allToLink, ":assemble")
        // TODO - should skip the task as NO-SOURCE
        result.assertTasksSkipped(tasks.debug.allToLink, ":assemble")
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

    def "finds C and C++ standard library headers"() {
        given:
        buildFile << """
            apply plugin: 'cpp-library'
         """

        and:
        file("src/main/cpp/includingIoStream.cpp") << """
            #include <stdio.h>
            #include <iostream>
        """

        when:
        executer.withArgument("--info")
        run "assemble"

        then:
        output.contains("Found all include files for ':compileDebugCpp'")
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
        result.assertTasksExecuted(tasks.debug.allToLink, ":assemble")
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
            library.binaries.get { it.optimized }.configure { compileTask.get().macros(WITH_FEATURE: "true") }
         """

        expect:
        executer.withArgument("--info")
        succeeds tasks.release.assemble

        result.assertTasksExecuted(tasks.release.allToLink, tasks.release.extract, tasks.release.assemble)
        sharedLibrary("build/lib/main/release/hello").assertExists()
        sharedLibrary("build/lib/main/release/hello").assertHasStrippedDebugSymbolsFor(lib.sourceFileNamesWithoutHeaders)
        output.contains('compiling with feature enabled')

        executer.withArgument("--info")
        succeeds tasks.debug.assemble

        result.assertTasksExecuted(tasks.debug.allToLink, tasks.debug.assemble)
        sharedLibrary("build/lib/main/debug/hello").assertExists()
        sharedLibrary("build/lib/main/debug/hello").assertHasDebugSymbolsFor(lib.sourceFileNamesWithoutHeaders)
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
            
            task assembleLinkDebug {
                dependsOn library.binaries.get { !it.optimized }.map { it.linkFile }
            }
         """

        expect:
        succeeds "assembleLinkDebug"
        result.assertTasksExecuted(tasks.debug.allToLink, ":assembleLinkDebug")
        sharedLibrary("build/lib/main/debug/hello").assertExists()
    }

    def "can use runtime file as task dependency"() {
        given:
        settingsFile << "rootProject.name = 'hello'"
        def lib = new CppLib()
        lib.writeToProject(testDirectory)

        and:
        buildFile << """
            apply plugin: 'cpp-library'
            
            task assembleRuntimeDebug {
                dependsOn library.binaries.get { !it.optimized }.map { it.runtimeFile }
            }
         """

        expect:
        succeeds "assembleRuntimeDebug"
        result.assertTasksExecuted(tasks.debug.allToLink, ":assembleRuntimeDebug")
        sharedLibrary("build/lib/main/debug/hello").assertExists()
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
                dependsOn library.binaries.get { !it.optimized }.map { it.objects }
            }
         """

        expect:
        succeeds "compileDebug"
        result.assertTasksExecuted(tasks.debug.compile, ":compileDebug")
        objectFiles(lib.sources)*.assertExists()
        sharedLibrary("build/lib/main/debug/hello").assertDoesNotExist()
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
        result.assertTasksExecuted(tasks.debug.allToLink, ":assemble")

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
        result.assertTasksExecuted(tasks.debug.allToLink, ":assemble")

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
        result.assertTasksExecuted(tasks.debug.allToLink, ":assemble")

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
            library.binaries.get { !it.optimized }.configure { 
                compileTask.get().objectFileDir = layout.buildDirectory.dir("object-files")
                def link = linkTask.get()
                link.linkedFile = layout.buildDirectory.file("shared/main.bin")
                if (link.importLibrary.present) {
                    link.importLibrary = layout.buildDirectory.file("import/main.lib")
                }
            }
         """

        expect:
        succeeds "assemble"
        result.assertTasksExecuted(tasks.debug.allToLink, ":assemble")

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
        result.assertTasksExecuted(tasks.debug.allToLink, ":assemble")
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
        succeeds tasks(':lib1').debug.assemble

        result.assertTasksExecuted([':lib3', ':lib2'].collect { tasks(it).debug.allToLink }, tasks(':lib1').debug.allToAssemble)
        sharedLibrary("lib1/build/lib/main/debug/lib1").assertExists()
        sharedLibrary("lib2/build/lib/main/debug/lib2").assertExists()
        sharedLibrary("lib3/build/lib/main/debug/lib3").assertExists()

        succeeds tasks(':lib1').release.assemble

        result.assertTasksExecuted([':lib3', ':lib2'].collect { tasks(it).release.allToLink }, tasks(':lib1').release.allToAssemble)
        sharedLibrary("lib1/build/lib/main/release/lib1").assertExists()
        sharedLibrary("lib2/build/lib/main/release/lib2").assertExists()
        sharedLibrary("lib3/build/lib/main/release/lib3").assertExists()

        sharedLibrary("lib1/build/lib/main/release/lib1").strippedRuntimeFile.assertExists()
        sharedLibrary("lib2/build/lib/main/release/lib2").strippedRuntimeFile.assertExists()
        sharedLibrary("lib3/build/lib/main/release/lib3").strippedRuntimeFile.assertExists()
    }

    def "can compile and link against static implementation and api libraries"() {
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
                library.linkage = [Linkage.STATIC]
            }
            project(':lib3') {
                apply plugin: 'cpp-library'
                library.linkage = [Linkage.STATIC]
            }
        """
        app.deck.writeToProject(file("lib1"))
        app.card.writeToProject(file("lib2"))
        app.shuffle.writeToProject(file("lib3"))

        expect:
        succeeds tasks(':lib1').debug.assemble

        result.assertTasksExecuted([':lib3', ':lib2'].collect { tasks(it).debug.allToCreate }, tasks(':lib1').debug.allToAssemble)
        sharedLibrary("lib1/build/lib/main/debug/lib1").assertExists()
        staticLibrary("lib2/build/lib/main/debug/lib2").assertExists()
        staticLibrary("lib3/build/lib/main/debug/lib3").assertExists()

        succeeds tasks(':lib1').release.assemble

        result.assertTasksExecuted([':lib3', ':lib2'].collect { tasks(it).release.allToCreate }, tasks(':lib1').release.allToAssemble)
        sharedLibrary("lib1/build/lib/main/release/lib1").assertExists()
        staticLibrary("lib2/build/lib/main/release/lib2").assertExists()
        staticLibrary("lib3/build/lib/main/release/lib3").assertExists()

        sharedLibrary("lib1/build/lib/main/release/lib1").strippedRuntimeFile.assertExists()
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
        failure.assertThatCause(CoreMatchers.containsString("C++ compiler failed while compiling main.cpp."))

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
        failure.assertThatCause(CoreMatchers.containsString("C++ compiler failed while compiling main.cpp."))

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
        result.assertTasksExecuted([':lib2', ':lib1'].collect { tasks(it).debug.allToLink }, ":lib1:assemble")
        sharedLibrary("lib1/build/lib/main/debug/hello").assertExists()
        sharedLibrary("lib2/build/lib/main/debug/log").assertExists()
    }

    @RequiresInstalledToolChain(ToolChainRequirement.GCC_COMPATIBLE)
    def "system headers are not evaluated when compiler warnings are enabled"() {
        given:
        settingsFile << "rootProject.name = 'hello'"

        and:
        file("src/main/cpp/includingIoStream.cpp") << """
            #include <stdio.h>
            #include <iostream>
        """
        buildFile << """
            apply plugin: 'cpp-library'
            
            library {
                binaries.configureEach {
                    compileTask.get().compilerArgs.add("-Wall")
                    compileTask.get().compilerArgs.add("-Werror")
                }
            }
         """

        expect:
        succeeds "assemble"
        result.assertTasksExecuted(tasks.debug.allToLink, ":assemble")
        sharedLibrary("build/lib/main/debug/hello").assertExists()
    }
}
