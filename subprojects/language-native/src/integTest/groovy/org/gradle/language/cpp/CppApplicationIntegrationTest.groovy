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

import org.gradle.nativeplatform.fixtures.app.CppApp
import org.gradle.nativeplatform.fixtures.app.CppAppWithLibraries
import org.gradle.nativeplatform.fixtures.app.CppAppWithLibrariesWithApiDependencies
import org.gradle.nativeplatform.fixtures.app.CppAppWithLibrary
import org.gradle.nativeplatform.fixtures.app.CppAppWithLibraryAndOptionalFeature
import org.gradle.nativeplatform.fixtures.app.CppAppWithOptionalFeature
import org.gradle.nativeplatform.fixtures.app.CppCompilerDetectingTestApp
import org.gradle.nativeplatform.fixtures.app.SourceElement
import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform

import static org.gradle.util.Matchers.containsText

class CppApplicationIntegrationTest extends AbstractCppIntegrationTest implements CppTaskNames {

    @Override
    protected void makeSingleProject() {
        buildFile << """
            apply plugin: 'cpp-application'
        """
    }

    @Override
    protected String getComponentUnderTestDsl() {
        return "application"
    }

    @Override
    protected List<String> getTasksToAssembleDevelopmentBinary() {
        return [":compileDebugCpp", ":linkDebug", ":installDebug"]
    }

    @Override
    protected String getDevelopmentBinaryCompileTask() {
        return ":compileDebugCpp"
    }

    @Override
    protected SourceElement getComponentUnderTest() {
        return new CppApp()
    }

    def "skip compile, link and install tasks when no source"() {
        given:
        buildFile << """
            apply plugin: 'cpp-application'
        """

        expect:
        succeeds "assemble"
        result.assertTasksExecuted(compileTasksDebug(), linkTaskDebug(), installTaskDebug(), ':assemble')
        // TODO - should skip the task as NO-SOURCE
        result.assertTasksSkipped(compileTasksDebug(), linkTaskDebug(), installTaskDebug(), ':assemble')
    }

    def "build fails when compilation fails"() {
        given:
        buildFile << """
            apply plugin: 'cpp-application'
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

    def "sources are compiled and linked with with C++ tools"() {
        settingsFile << "rootProject.name = 'app'"
        def app = new CppCompilerDetectingTestApp()

        given:
        app.writeSources(file('src/main'))

        and:
        buildFile << """
            apply plugin: 'cpp-application'
         """

        expect:
        succeeds "assemble"
        result.assertTasksExecuted(compileTasksDebug(), linkTaskDebug(), installTaskDebug(), ':assemble')

        executable("build/exe/main/debug/app").assertExists()
        installation("build/install/main/debug").exec().out == app.expectedOutput(toolChain)
    }

    def "can build debug and release variants of executable"() {
        settingsFile << "rootProject.name = 'app'"
        def app = new CppAppWithOptionalFeature()

        given:
        app.writeToProject(testDirectory)

        and:
        buildFile << """
            apply plugin: 'cpp-application'
            application.binaries.get { it.optimized }.configure {
                compileTask.get().macros(WITH_FEATURE: "true")
            }
         """

        expect:
        succeeds assembleTaskRelease()
        result.assertTasksExecuted(compileTasksRelease(), linkTaskRelease(), extractAndStripSymbolsTasksRelease(toolChain), installTaskRelease(), assembleTaskRelease())

        executable("build/exe/main/release/app").assertExists()
        executable("build/exe/main/release/app").assertHasStrippedDebugSymbolsFor(app.sourceFileNamesWithoutHeaders)
        installation("build/install/main/release").exec().out == app.withFeatureEnabled().expectedOutput

        succeeds assembleTaskDebug()
        result.assertTasksExecuted(compileTasksDebug(), linkTaskDebug(), installTaskDebug(), assembleTaskDebug())

        executable("build/exe/main/debug/app").assertExists()
        executable("build/exe/main/debug/app").assertHasDebugSymbolsFor(app.sourceFileNamesWithoutHeaders)
        installation("build/install/main/debug").exec().out == app.withFeatureDisabled().expectedOutput
    }

    def "can use executable file as task dependency"() {
        settingsFile << "rootProject.name = 'app'"
        def app = new CppApp()

        given:
        app.writeToProject(testDirectory)

        and:
        buildFile << """
            apply plugin: 'cpp-application'

            task buildDebug {
                dependsOn application.binaries.get { !it.optimized }.map { it.executableFile }
            }
         """

        expect:
        succeeds "buildDebug"
        result.assertTasksExecuted(compileTasksDebug(), linkTaskDebug(), ':buildDebug')
        executable("build/exe/main/debug/app").assertExists()
    }

    def "can use objects as task dependency"() {
        settingsFile << "rootProject.name = 'app'"
        def app = new CppApp()

        given:
        app.writeToProject(testDirectory)

        and:
        buildFile << """
            apply plugin: 'cpp-application'

            task compileDebug {
                dependsOn application.binaries.get { !it.optimized }.map { it.objects }
            }
         """

        expect:
        succeeds "compileDebug"
        result.assertTasksExecuted(compileTasksDebug(), ':compileDebug')
        executable("build/exe/main/debug/app").assertDoesNotExist()
        objectFiles(app.main)*.assertExists()
    }

    def "can use installDirectory as task dependency"() {
        settingsFile << "rootProject.name = 'app'"
        def app = new CppApp()

        given:
        app.writeToProject(testDirectory)

        and:
        buildFile << """
            apply plugin: 'cpp-application'

            task install {
                dependsOn application.binaries.get { !it.optimized }.map { it.installDirectory }
            }
         """

        expect:
        succeeds "install"
        result.assertTasksExecuted(compileTasksDebug(), linkTaskDebug(), ':installDebug', ':install')
        installation("build/install/main/debug").exec().out == app.expectedOutput
    }

    def "ignores non-C++ source files in source directory"() {
        settingsFile << "rootProject.name = 'app'"
        def app = new CppApp()

        given:
        app.writeToProject(testDirectory)
        file("src/main/cpp/ignore.swift") << 'broken!'
        file("src/main/cpp/ignore.c") << 'broken!'
        file("src/main/cpp/ignore.m") << 'broken!'
        file("src/main/cpp/ignore.h") << 'broken!'
        file("src/main/cpp/ignore.java") << 'broken!'

        and:
        buildFile << """
            apply plugin: 'cpp-application'
         """

        expect:
        succeeds "assemble"
        result.assertTasksExecuted(compileTasksDebug(), linkTaskDebug(), installTaskDebug(), ":assemble")

        executable("build/exe/main/debug/app").assertExists()
        installation("build/install/main/debug").exec().out == app.expectedOutput
    }

    def "build logic can change source layout convention"() {
        settingsFile << "rootProject.name = 'app'"
        def app = new CppApp()

        given:
        app.sources.writeToSourceDir(file("srcs"))
        app.headers.writeToSourceDir(file("include"))
        file("src/main/headers/${app.greeter.header.sourceFile.name}") << 'broken!'
        file("src/main/cpp/broken.cpp") << "ignore me!"

        and:
        buildFile << """
            apply plugin: 'cpp-application'
            application {
                source.from 'srcs'
                privateHeaders.from 'include'
            }
         """

        expect:
        succeeds "assemble"
        result.assertTasksExecuted(compileTasksDebug(), linkTaskDebug(), installTaskDebug(), ':assemble')

        file("build/obj/main/debug").assertIsDir()
        executable("build/exe/main/debug/app").assertExists()
        installation("build/install/main/debug").exec().out == app.expectedOutput
    }

    def "build logic can add individual source files"() {
        settingsFile << "rootProject.name = 'app'"
        def app = new CppApp()

        given:
        app.headers.writeToProject(testDirectory)
        app.main.writeToSourceDir(file("srcs/main.cpp"))
        app.greeter.writeToSourceDir(file("srcs/one.cpp"))
        app.sum.writeToSourceDir(file("srcs/two.cpp"))
        file("src/main/cpp/broken.cpp") << "ignore me!"

        and:
        buildFile << """
            apply plugin: 'cpp-application'
            application {
                source {
                    from('srcs/main.cpp')
                    from('srcs/one.cpp')
                    from('srcs/two.cpp')
                }
            }
         """

        expect:
        succeeds "assemble"
        result.assertTasksExecuted(compileTasksDebug(), linkTaskDebug(), installTaskDebug(), ':assemble')

        file("build/obj/main/debug").assertIsDir()
        executable("build/exe/main/debug/app").assertExists()
        installation("build/install/main/debug").exec().out == app.expectedOutput
    }

    def "build logic can change buildDir"() {
        settingsFile << "rootProject.name = 'app'"
        def app = new CppApp()

        given:
        app.writeToProject(testDirectory)

        and:
        buildFile << """
            apply plugin: 'cpp-application'
            buildDir = 'output'
         """

        expect:
        succeeds "assemble"
        result.assertTasksExecuted(compileTasksDebug(), linkTaskDebug(), installTaskDebug(), ':assemble')

        !file("build").exists()
        file("output/obj/main/debug").assertIsDir()
        executable("output/exe/main/debug/app").assertExists()
        installation("output/install/main/debug").exec().out == app.expectedOutput
    }

    def "build logic can define the base name"() {
        def app = new CppApp()

        given:
        app.writeToProject(testDirectory)

        and:
        buildFile << """
            apply plugin: 'cpp-application'
            application.baseName = 'test_app'
         """

        expect:
        succeeds "assemble"
        result.assertTasksExecuted(compileTasksDebug(), linkTaskDebug(), installTaskDebug(), ':assemble')

        file("build/obj/main/debug").assertIsDir()
        executable("build/exe/main/debug/test_app").assertExists()
        installation("build/install/main/debug").exec().out == app.expectedOutput
    }

    def "build logic can change task output locations"() {
        settingsFile << "rootProject.name = 'app'"
        def app = new CppApp()

        given:
        app.writeToProject(testDirectory)

        and:
        buildFile << """
            apply plugin: 'cpp-application'
            application.binaries.get { !it.optimized }.configure {
                compileTask.get().objectFileDir = layout.buildDirectory.dir("object-files")
                linkTask.get().linkedFile = layout.buildDirectory.file("exe/some-app.exe")
                installTask.get().installDirectory = layout.buildDirectory.dir("some-app")
            }
         """

        expect:
        succeeds "assemble"
        result.assertTasksExecuted(compileTasksDebug(), linkTaskDebug(), installTaskDebug(), ':assemble')

        file("build/object-files").assertIsDir()
        file("build/exe/some-app.exe").assertIsFile()
        installation("build/some-app").exec().out == app.expectedOutput
    }

    def "can compile and link against a library"() {
        settingsFile << "include 'app', 'hello'"
        def app = new CppAppWithLibrary()

        given:
        buildFile << """
            project(':app') {
                apply plugin: 'cpp-application'
                dependencies {
                    implementation project(':hello')
                }
            }
            project(':hello') {
                apply plugin: 'cpp-library'
            }
        """
        app.greeter.writeToProject(file("hello"))
        app.main.writeToProject(file("app"))

        expect:
        succeeds ":app:assemble"

        result.assertTasksExecuted(compileAndLinkTasks([':hello', ':app'], debug), installTaskDebug(':app'), ":app:assemble")
        executable("app/build/exe/main/debug/app").assertExists()
        sharedLibrary("hello/build/lib/main/debug/hello").assertExists()
        def installation = installation("app/build/install/main/debug")
        installation.exec().out == app.expectedOutput
        installation.assertIncludesLibraries("hello")
    }

    def "can directly depend on generated sources on includePath"() {
        settingsFile << "rootProject.name = 'app'"

        given:
        file("src/main/cpp/main.cpp") << """
            #include "foo.h"
            
            int main(int argc, char** argv) {
                return EXIT_VALUE;
            }
        """

        and:
        buildFile << """
            apply plugin: 'cpp-application'
            
            task generateHeader {
                ext.headerDirectory = newOutputDirectory()
                headerDirectory.set(project.layout.buildDirectory.dir("headers"))
                doLast {
                    def fooH = headerDirectory.file("foo.h").get().asFile
                    fooH.parentFile.mkdirs()
                    fooH << '''
                        #define EXIT_VALUE 0
                    '''
                }
            }
            
            application.binaries.whenElementFinalized { binary ->
                def dependency = project.dependencies.create(files(generateHeader.headerDirectory))
                binary.getIncludePathConfiguration().dependencies.add(dependency)
            }
         """

        expect:
        succeeds "compileDebug"
    }

    def "can compile and link against a library with explicit operating system family defined"() {
        settingsFile << "include 'app', 'hello'"
        def app = new CppAppWithLibrary()

        given:
        buildFile << """
            project(':app') {
                apply plugin: 'cpp-application'
                application {
                    dependencies {
                        implementation project(':hello')
                    }
                    operatingSystems = [objects.named(OperatingSystemFamily, '${DefaultNativePlatform.currentOperatingSystem.toFamilyName()}')]
                }
            }
            project(':hello') {
                apply plugin: 'cpp-library'
                library {
                    operatingSystems = [objects.named(OperatingSystemFamily, '${DefaultNativePlatform.currentOperatingSystem.toFamilyName()}')]
                }
            }
        """
        app.greeter.writeToProject(file("hello"))
        app.main.writeToProject(file("app"))

        expect:
        succeeds ":app:assemble"

        result.assertTasksExecuted(compileAndLinkTasks([':hello', ':app'], debug), installTaskDebug(':app'), ":app:assemble")
        executable("app/build/exe/main/debug/app").assertExists()
        sharedLibrary("hello/build/lib/main/debug/hello").assertExists()
        def installation = installation("app/build/install/main/debug")
        installation.exec().out == app.expectedOutput
        installation.assertIncludesLibraries("hello")
    }

    def "fails compile and link against a library with different operating system family support"() {
        settingsFile << "include 'app', 'hello'"
        def app = new CppAppWithLibrary()

        given:
        def currentOperatingSystemFamily = DefaultNativePlatform.currentOperatingSystem.toFamilyName()
        buildFile << """
            project(':app') {
                apply plugin: 'cpp-application'
                application {
                    dependencies {
                        implementation project(':hello')
                    }
                    operatingSystems = [objects.named(OperatingSystemFamily, '${currentOperatingSystemFamily}')]
                }
            }
            project(':hello') {
                apply plugin: 'cpp-library'
                library {
                    operatingSystems = [objects.named(OperatingSystemFamily, 'some-other-family')]
                }
            }
        """
        app.greeter.writeToProject(file("hello"))
        app.main.writeToProject(file("app"))

        expect:
        fails ":app:assemble"

        failure.assertHasCause """Unable to find a matching configuration of project :hello: Configuration 'cppApiElements':
  - Required org.gradle.native.debuggable 'true' but no value provided.
  - Required org.gradle.native.operatingSystem '${currentOperatingSystemFamily}' but no value provided.
  - Required org.gradle.native.optimized 'false' but no value provided.
  - Required org.gradle.usage 'native-runtime' and found incompatible value 'cplusplus-api'."""
    }

    def "can compile and link against a static library"() {
        settingsFile << "include 'app', 'hello'"
        def app = new CppAppWithLibrary()

        given:
        buildFile << """
            project(':app') {
                apply plugin: 'cpp-application'
                dependencies {
                    implementation project(':hello')
                }
            }
            project(':hello') {
                apply plugin: 'cpp-library'
                
                library.linkage = [Linkage.STATIC]
            }
        """
        app.greeter.writeToProject(file("hello"))
        app.main.writeToProject(file("app"))

        expect:
        succeeds ":app:assemble"

        result.assertTasksExecuted(compileAndStaticLinkTasks([':hello'], debug), compileAndLinkTasks([':app'], debug), installTaskDebug(':app'), ":app:assemble")
        executable("app/build/exe/main/debug/app").assertExists()
        staticLibrary("hello/build/lib/main/debug/hello").assertExists()
        def installation = installation("app/build/install/main/debug")
        installation.exec().out == app.expectedOutput
        installation.assertIncludesLibraries()
    }

    def "can compile and link against a library with both linkages defined"() {
        settingsFile << "include 'app', 'hello'"
        def app = new CppAppWithLibrary()

        given:
        buildFile << """
            project(':app') {
                apply plugin: 'cpp-application'
                dependencies {
                    implementation project(':hello')
                }
            }
            project(':hello') {
                apply plugin: 'cpp-library'
                
                library.linkage = [Linkage.STATIC, Linkage.SHARED]
            }
        """
        app.greeter.writeToProject(file("hello"))
        app.main.writeToProject(file("app"))

        expect:
        succeeds ":app:assemble"

        result.assertTasksExecuted(compileAndLinkTasks([':hello'], debugShared), compileAndLinkTasks([':app'], debug), installTaskDebug(':app'), ":app:assemble")
        executable("app/build/exe/main/debug/app").assertExists()
        sharedLibrary("hello/build/lib/main/debug/shared/hello").assertExists()
        def installation = installation("app/build/install/main/debug")
        installation.exec().out == app.expectedOutput
        installation.assertIncludesLibraries("hello")
    }

    def "can compile and link against a library with debug and release variants"() {
        settingsFile << "include 'app', 'hello'"
        def app = new CppAppWithLibraryAndOptionalFeature()

        given:
        buildFile << """
            project(':app') {
                apply plugin: 'cpp-application'
                dependencies {
                    implementation project(':hello')
                }
                application.binaries.get { it.optimized }.configure {
                    compileTask.get().macros(WITH_FEATURE: "true")
                }
            }
            project(':hello') {
                apply plugin: 'cpp-library'
                library.binaries.get { it.optimized }.configure {
                    compileTask.get().macros(WITH_FEATURE: "true")
                }
            }
        """
        app.greeterLib.writeToProject(file("hello"))
        app.main.writeToProject(file("app"))

        expect:
        succeeds assembleTaskRelease(':app')

        result.assertTasksExecuted(compileAndLinkTasks([':hello', ':app'], release), stripSymbolsTasksRelease(':hello', toolChain), extractAndStripSymbolsTasksRelease(':app', toolChain), installTaskRelease(':app'), assembleTaskRelease(':app'))
        executable("app/build/exe/main/release/app").assertExists()
        executable("app/build/exe/main/release/app").assertHasStrippedDebugSymbolsFor(app.main.sourceFileNames)
        sharedLibrary("hello/build/lib/main/release/hello").assertExists()
        sharedLibrary("hello/build/lib/main/release/hello").assertHasDebugSymbolsFor(app.greeterLib.sourceFileNamesWithoutHeaders)
        installation("app/build/install/main/release").exec().out == app.withFeatureEnabled().expectedOutput

        succeeds assembleTaskDebug(':app')

        result.assertTasksExecuted(compileAndLinkTasks([':hello', ':app'], debug), installTaskDebug(':app'), assembleTaskDebug(':app'))

        executable("app/build/exe/main/debug/app").assertExists()
        executable("app/build/exe/main/debug/app").assertHasDebugSymbolsFor(app.main.sourceFileNames)
        sharedLibrary("hello/build/lib/main/debug/hello").assertExists()
        sharedLibrary("hello/build/lib/main/debug/hello").assertHasDebugSymbolsFor(app.greeterLib.sourceFileNamesWithoutHeaders)
        installation("app/build/install/main/debug").exec().out == app.withFeatureDisabled().expectedOutput
    }

    def "can compile and link against library with api and implementation dependencies"() {
        settingsFile << "include 'app', 'deck', 'card', 'shuffle'"
        def app = new CppAppWithLibrariesWithApiDependencies()

        given:
        buildFile << """
            project(':app') {
                apply plugin: 'cpp-application'
                dependencies {
                    implementation project(':deck')
                }
            }
            project(':deck') {
                apply plugin: 'cpp-library'
                dependencies {
                    api project(':card')
                    implementation project(':shuffle')
                }
            }
            project(':card') {
                apply plugin: 'cpp-library'
            }
            project(':shuffle') {
                apply plugin: 'cpp-library'
            }
        """
        app.deck.writeToProject(file("deck"))
        app.card.writeToProject(file("card"))
        app.shuffle.writeToProject(file("shuffle"))
        app.main.writeToProject(file("app"))

        expect:
        succeeds ":app:assemble"

        result.assertTasksExecuted(compileAndLinkTasks([':card', ':deck', ':shuffle', ':app'], debug), installTaskDebug(':app'), ":app:assemble")
        sharedLibrary("deck/build/lib/main/debug/deck").assertExists()
        sharedLibrary("card/build/lib/main/debug/card").assertExists()
        sharedLibrary("shuffle/build/lib/main/debug/shuffle").assertExists()
        executable("app/build/exe/main/debug/app").assertExists()
        def installation = installation("app/build/install/main/debug")
        installation.assertIncludesLibraries("deck", "card", "shuffle")
        installation.exec().out == app.expectedOutput
    }

    def "can compile and link against a static library with api and implementation dependencies"() {
        settingsFile << "include 'app', 'deck', 'card', 'shuffle'"
        def app = new CppAppWithLibrariesWithApiDependencies()

        given:
        buildFile << """
            project(':app') {
                apply plugin: 'cpp-application'
                dependencies {
                    implementation project(':deck')
                }
            }
            project(':deck') {
                apply plugin: 'cpp-library'
                library.linkage = [Linkage.STATIC]
                dependencies {
                    api project(':card')
                    implementation project(':shuffle')
                }
            }
            project(':card') {
                apply plugin: 'cpp-library'
                library.linkage = [Linkage.STATIC]
            }
            project(':shuffle') {
                apply plugin: 'cpp-library'
                library.linkage = [Linkage.STATIC]
            }
        """
        app.deck.writeToProject(file("deck"))
        app.card.writeToProject(file("card"))
        app.shuffle.writeToProject(file("shuffle"))
        app.main.writeToProject(file("app"))

        expect:
        succeeds ":app:assemble"

        result.assertTasksExecuted(compileAndStaticLinkTasks([':card', ':deck', ':shuffle'], debug), compileAndLinkTasks([':app'], debug), installTaskDebug(':app'), ":app:assemble")
        staticLibrary("deck/build/lib/main/debug/deck").assertExists()
        staticLibrary("card/build/lib/main/debug/card").assertExists()
        staticLibrary("shuffle/build/lib/main/debug/shuffle").assertExists()
        executable("app/build/exe/main/debug/app").assertExists()
        def installation = installation("app/build/install/main/debug")
        installation.assertIncludesLibraries()
        installation.exec().out == app.expectedOutput
    }

    def "honors changes to library buildDir"() {
        settingsFile << "include 'app', 'lib1', 'lib2'"
        def app = new CppAppWithLibraries()

        given:
        buildFile << """
            project(':app') {
                apply plugin: 'cpp-application'
                dependencies {
                    implementation project(':lib1')
                }
            }
            project(':lib1') {
                apply plugin: 'cpp-library'
                dependencies {
                    implementation project(':lib2')
                }
            }
            project(':lib2') {
                apply plugin: 'cpp-library'
                buildDir = 'out'
            }
"""
        app.greeterLib.writeToProject(file("lib1"))
        app.loggerLib.writeToProject(file("lib2"))
        app.main.writeToProject(file("app"))

        expect:
        succeeds ":app:assemble"

        result.assertTasksExecuted(compileAndLinkTasks([':lib1', ':lib2', ':app'], debug), installTaskDebug(':app'), ":app:assemble")

        !file("lib2/build").exists()
        sharedLibrary("lib1/build/lib/main/debug/lib1").assertExists()
        sharedLibrary("lib2/out/lib/main/debug/lib2").assertExists()
        executable("app/build/exe/main/debug/app").assertExists()
        installation("app/build/install/main/debug").exec().out == app.expectedOutput
        sharedLibrary("app/build/install/main/debug/lib/lib1").file.assertExists()
        sharedLibrary("app/build/install/main/debug/lib/lib2").file.assertExists()
    }

    def "honors changes to library output locations"() {
        settingsFile << "include 'app', 'lib1', 'lib2'"
        def app = new CppAppWithLibraries()

        given:
        buildFile << """
            project(':app') {
                apply plugin: 'cpp-application'
                dependencies {
                    implementation project(':lib1')
                }
            }
            project(':lib1') {
                apply plugin: 'cpp-library'
                dependencies {
                    implementation project(':lib2')
                }
            }
            project(':lib2') {
                apply plugin: 'cpp-library'
                library.binaries.get { !it.optimized }.configure {
                    def link = linkTask.get()
                    link.linkedFile = layout.buildDirectory.file("shared/lib1_debug.dll")
                    if (link.importLibrary.present) {
                        link.importLibrary = layout.buildDirectory.file("import/lib1_import.lib")
                    }
                }
            }
        """
        app.greeterLib.writeToProject(file("lib1"))
        app.loggerLib.writeToProject(file("lib2"))
        app.main.writeToProject(file("app"))

        expect:
        succeeds ":app:assemble"

        result.assertTasksExecuted(compileAndLinkTasks([':lib1', ':lib2', ':app'], debug), installTaskDebug(':app'), ":app:assemble")

        file("lib2/build/shared/lib1_debug.dll").assertIsFile()
        if (toolChain.visualCpp) {
            file("lib2/build/import/lib1_import.lib").assertIsFile()
        }
        installation("app/build/install/main/debug").exec().out == app.expectedOutput
        sharedLibrary("app/build/install/main/debug/lib/lib1").file.assertExists()
        file("app/build/install/main/debug/lib/lib1_debug.dll").assertIsFile()
    }

    def "honors changes to library public header location"() {
        settingsFile << "include 'app', 'lib1', 'lib2'"
        def app = new CppAppWithLibraries()

        given:
        buildFile << """
            project(':app') {
                apply plugin: 'cpp-application'
                dependencies {
                    implementation project(':lib1')
                }
            }
            project(':lib1') {
                apply plugin: 'cpp-library'
                dependencies {
                    implementation project(':lib2')
                }
                library {
                    publicHeaders.from('include')
                }
            }
            project(':lib2') {
                apply plugin: 'cpp-library'
                library {
                    publicHeaders.from('include')
                }
            }
"""
        app.greeterLib.publicHeaders.writeToSourceDir(file("lib1/include"))
        app.greeterLib.privateHeaders.writeToProject(file("lib1"))
        app.greeterLib.sources.writeToProject(file("lib1"))
        app.loggerLib.publicHeaders.writeToSourceDir(file("lib2/include"))
        app.loggerLib.sources.writeToProject(file("lib2"))
        app.main.writeToProject(file("app"))

        expect:
        succeeds ":app:assemble"

        result.assertTasksExecuted(compileAndLinkTasks([':lib1', ':lib2', ':app'], debug), installTaskDebug(':app'), ":app:assemble")

        sharedLibrary("lib1/build/lib/main/debug/lib1").assertExists()
        sharedLibrary("lib2/build/lib/main/debug/lib2").assertExists()
        executable("app/build/exe/main/debug/app").assertExists()
        installation("app/build/install/main/debug").exec().out == app.expectedOutput
        sharedLibrary("app/build/install/main/debug/lib/lib1").file.assertExists()
        sharedLibrary("app/build/install/main/debug/lib/lib2").file.assertExists()
    }

    def "multiple components can share the same source directory"() {
        settingsFile << "include 'app', 'greeter', 'logger'"
        def app = new CppAppWithLibraries()

        given:
        buildFile << """
            project(':app') {
                apply plugin: 'cpp-application'
                dependencies {
                    implementation project(':greeter')
                }
                application {
                    source.from '../Sources/main.cpp'
                }
            }
            project(':greeter') {
                apply plugin: 'cpp-library'
                dependencies {
                    implementation project(':logger')
                }
                library {
                    source.from '../Sources/greeter.cpp'
                }
            }
            project(':logger') {
                apply plugin: 'cpp-library'
                library {
                    source.from '../Sources/logger.cpp'
                }
            }
"""
        app.main.writeToSourceDir(file("Sources"))
        app.greeterLib.sources.writeToSourceDir(file("Sources"))
        app.greeterLib.headers.writeToProject(file("greeter"))
        app.loggerLib.sources.writeToSourceDir(file("Sources"))
        app.loggerLib.headers.writeToProject(file("logger"))

        expect:
        succeeds ":app:assemble"
        result.assertTasksExecuted(compileAndLinkTasks([':greeter', ":logger", ':app'], debug), installTaskDebug(':app'), ":app:assemble")

        sharedLibrary("greeter/build/lib/main/debug/greeter").assertExists()
        sharedLibrary("logger/build/lib/main/debug/logger").assertExists()
        executable("app/build/exe/main/debug/app").assertExists()
        installation("app/build/install/main/debug").exec().out == app.expectedOutput
        sharedLibrary("app/build/install/main/debug/lib/greeter").file.assertExists()
        sharedLibrary("app/build/install/main/debug/lib/logger").file.assertExists()
    }

    def "can compile and link against libraries in included builds"() {
        settingsFile << """
            rootProject.name = 'app'
            includeBuild 'lib1'
            includeBuild 'lib2'
        """
        file("lib1/settings.gradle") << "rootProject.name = 'lib1'"
        file("lib2/settings.gradle") << "rootProject.name = 'lib2'"

        def app = new CppAppWithLibraries()

        given:
        buildFile << """
            apply plugin: 'cpp-application'
            dependencies {
                implementation 'test:lib1:1.2'
            }
        """
        file("lib1/build.gradle") << """
            apply plugin: 'cpp-library'
            group = 'test'
            dependencies {
                implementation 'test:lib2:1.4'
            }
        """
        file("lib2/build.gradle") << """
            apply plugin: 'cpp-library'
            group = 'test'
        """

        app.greeterLib.writeToProject(file("lib1"))
        app.loggerLib.writeToProject(file("lib2"))
        app.main.writeToProject(testDirectory)

        expect:
        succeeds ":assemble"
        result.assertTasksExecuted(compileAndLinkTasks([':lib1', ":lib2", ''], debug), installTaskDebug(), ":assemble")
        sharedLibrary("lib1/build/lib/main/debug/lib1").assertExists()
        sharedLibrary("lib2/build/lib/main/debug/lib2").assertExists()
        executable("build/exe/main/debug/app").assertExists()
        installation("build/install/main/debug").exec().out == app.expectedOutput
        sharedLibrary("build/install/main/debug/lib/lib1").file.assertExists()
        sharedLibrary("build/install/main/debug/lib/lib2").file.assertExists()
    }
}
