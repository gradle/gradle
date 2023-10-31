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

import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.internal.component.ResolutionFailureHandler
import org.gradle.nativeplatform.fixtures.RequiresInstalledToolChain
import org.gradle.nativeplatform.fixtures.ToolChainRequirement
import org.gradle.nativeplatform.fixtures.app.CppApp
import org.gradle.nativeplatform.fixtures.app.CppAppWithLibraries
import org.gradle.nativeplatform.fixtures.app.CppAppWithLibrariesWithApiDependencies
import org.gradle.nativeplatform.fixtures.app.CppAppWithLibrary
import org.gradle.nativeplatform.fixtures.app.CppAppWithLibraryAndOptionalFeature
import org.gradle.nativeplatform.fixtures.app.CppAppWithOptionalFeature
import org.gradle.nativeplatform.fixtures.app.CppCompilerDetectingTestApp
import org.gradle.nativeplatform.fixtures.app.SourceElement
import spock.lang.Issue

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
    protected List<String> getTasksToAssembleDevelopmentBinary(String variant) {
        return [":compileDebug${variant.capitalize()}Cpp", ":linkDebug${variant.capitalize()}", ":installDebug${variant.capitalize()}"]
    }

    @Override
    protected String getDevelopmentBinaryCompileTask() {
        return ":compileDebugCpp"
    }

    @Override
    protected SourceElement getComponentUnderTest() {
        return new CppApp()
    }

    @ToBeFixedForConfigurationCache
    def "skip compile, link and install tasks when no source"() {
        given:
        buildFile << """
            apply plugin: 'cpp-application'
        """

        expect:
        succeeds "assemble"
        result.assertTasksExecuted(tasks.debug.allToInstall, ':assemble')
        // TODO - should skip the task as NO-SOURCE
        result.assertTasksSkipped(tasks.debug.allToInstall, ':assemble')
    }

    @ToBeFixedForConfigurationCache
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

    @ToBeFixedForConfigurationCache
    def "sources are compiled and linked with C++ tools"() {
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
        result.assertTasksExecuted(tasks.debug.allToInstall, ':assemble')

        executable("build/exe/main/debug/app").assertExists()
        installation("build/install/main/debug").exec().out == app.expectedOutput(toolChain)
    }

    @ToBeFixedForConfigurationCache
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
        succeeds tasks.release.assemble
        result.assertTasksExecuted(tasks.release.allToInstall, tasks.release.extract, tasks.release.assemble)

        executable("build/exe/main/release/app").assertExists()
        executable("build/exe/main/release/app").assertHasStrippedDebugSymbolsFor(app.sourceFileNamesWithoutHeaders)
        installation("build/install/main/release").exec().out == app.withFeatureEnabled().expectedOutput

        succeeds tasks.debug.assemble
        result.assertTasksExecuted(tasks.debug.allToInstall, tasks.debug.assemble)

        executable("build/exe/main/debug/app").assertExists()
        executable("build/exe/main/debug/app").assertHasDebugSymbolsFor(app.sourceFileNamesWithoutHeaders)
        installation("build/install/main/debug").exec().out == app.withFeatureDisabled().expectedOutput
    }

    @ToBeFixedForConfigurationCache
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
        result.assertTasksExecuted(tasks.debug.allToLink, ':buildDebug')
        executable("build/exe/main/debug/app").assertExists()
    }

    @ToBeFixedForConfigurationCache
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
        result.assertTasksExecuted(tasks.debug.compile, ':compileDebug')
        executable("build/exe/main/debug/app").assertDoesNotExist()
        objectFiles(app.main)*.assertExists()
    }

    @ToBeFixedForConfigurationCache
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
        result.assertTasksExecuted(tasks.debug.allToInstall, ':install')
        installation("build/install/main/debug").exec().out == app.expectedOutput
    }

    @ToBeFixedForConfigurationCache
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
        result.assertTasksExecuted(tasks.debug.allToInstall, ":assemble")

        executable("build/exe/main/debug/app").assertExists()
        installation("build/install/main/debug").exec().out == app.expectedOutput
    }

    @ToBeFixedForConfigurationCache
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
        result.assertTasksExecuted(tasks.debug.allToInstall, ':assemble')

        file("build/obj/main/debug").assertIsDir()
        executable("build/exe/main/debug/app").assertExists()
        installation("build/install/main/debug").exec().out == app.expectedOutput
    }

    @ToBeFixedForConfigurationCache
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
        result.assertTasksExecuted(tasks.debug.allToInstall, ':assemble')

        file("build/obj/main/debug").assertIsDir()
        executable("build/exe/main/debug/app").assertExists()
        installation("build/install/main/debug").exec().out == app.expectedOutput
    }

    @ToBeFixedForConfigurationCache
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
        result.assertTasksExecuted(tasks.debug.allToInstall, ':assemble')

        !file("build").exists()
        file("output/obj/main/debug").assertIsDir()
        executable("output/exe/main/debug/app").assertExists()
        installation("output/install/main/debug").exec().out == app.expectedOutput
    }

    @ToBeFixedForConfigurationCache
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
        result.assertTasksExecuted(tasks.debug.allToInstall, ':assemble')

        file("build/obj/main/debug").assertIsDir()
        executable("build/exe/main/debug/test_app").assertExists()
        installation("build/install/main/debug").exec().out == app.expectedOutput
    }

    @ToBeFixedForConfigurationCache
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
        result.assertTasksExecuted(tasks.debug.allToInstall, ':assemble')

        file("build/object-files").assertIsDir()
        file("build/exe/some-app.exe").assertIsFile()
        installation("build/some-app").exec().out == app.expectedOutput
    }

    @ToBeFixedForConfigurationCache
    def "can compile and link against a library"() {
        createDirs("app", "hello")
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

        result.assertTasksExecuted(tasks(':hello').debug.allToLink, tasks(':app').debug.allToInstall, ":app:assemble")
        executable("app/build/exe/main/debug/app").assertExists()
        sharedLibrary("hello/build/lib/main/debug/hello").assertExists()
        def installation = installation("app/build/install/main/debug")
        installation.exec().out == app.expectedOutput
        installation.assertIncludesLibraries("hello")
    }

    @ToBeFixedForConfigurationCache
    def "can compile and link against a library when specifying multiple target machines"() {
        createDirs("app", "hello")
        settingsFile << "include 'app', 'hello'"
        def app = new CppAppWithLibrary()

        given:
        buildFile << """
            project(':app') {
                apply plugin: 'cpp-application'
                application {
                    targetMachines = [machines.${currentHostOperatingSystemFamilyDsl}, machines.os('host-family')]
                }
                dependencies {
                    implementation project(':hello')
                }
            }
            project(':hello') {
                apply plugin: 'cpp-library'
                library {
                    targetMachines = [machines.${currentHostOperatingSystemFamilyDsl}, machines.os('host-family')]
                }
            }
        """
        app.greeter.writeToProject(file("hello"))
        app.main.writeToProject(file("app"))

        expect:
        succeeds ":app:assemble"

        result.assertTasksExecuted(tasks(':hello').withOperatingSystemFamily(currentOsFamilyName).debug.allToLink, tasks(':app').withOperatingSystemFamily(currentOsFamilyName).debug.allToInstall, ":app:assemble")
        executable("app/build/exe/main/debug/${currentOsFamilyName.toLowerCase()}/app").assertExists()
        sharedLibrary("hello/build/lib/main/debug/${currentOsFamilyName.toLowerCase()}/hello").assertExists()
        def installation = installation("app/build/install/main/debug/${currentOsFamilyName.toLowerCase()}")
        installation.exec().out == app.expectedOutput
        installation.assertIncludesLibraries("hello")
    }

    def "fails when dependency library does not specify the same target machines"() {
        createDirs("app", "greeter")
        settingsFile << "include 'app', 'greeter'"
        def app = new CppAppWithLibrary()

        given:
        buildFile << """
            project(':app') {
                apply plugin: 'cpp-application'
                application {
                    targetMachines = [machines.${currentHostOperatingSystemFamilyDsl}]
                    dependencies {
                        implementation project(':greeter')
                    }
                }
            }
            project(':greeter') {
                apply plugin: 'cpp-library'
                library {
                    targetMachines = [machines.os('os-family')]
                }
            }
        """
        file("gradle.properties").text = "${ResolutionFailureHandler.FULL_FAILURES_MESSAGE_PROPERTY}=true"

        app.greeter.writeToProject(file("greeter"))
        app.main.writeToProject(file("app"))

        expect:
        fails ":app:assemble"

        and:
        failure.assertHasCause("Could not resolve project :greeter")
        failure.assertHasCause("No matching variant of project :greeter was found. The consumer was configured to find attribute 'org.gradle.usage' with value 'native-runtime', attribute 'org.gradle.native.debuggable' with value 'true', attribute 'org.gradle.native.optimized' with value 'false', attribute 'org.gradle.native.operatingSystem' with value '${currentOsFamilyName.toLowerCase()}', attribute 'org.gradle.native.architecture' with value '${currentArchitecture}' but:")
    }

    def "fails when dependency library does not specify the same target architecture"() {
        createDirs("app", "greeter")
        settingsFile << "include 'app', 'greeter'"
        def app = new CppAppWithLibrary()

        given:
        buildFile << """
            project(':app') {
                apply plugin: 'cpp-application'
                application {
                    targetMachines = [machines.${currentHostOperatingSystemFamilyDsl}]
                    dependencies {
                        implementation project(':greeter')
                    }
                }
            }
            project(':greeter') {
                apply plugin: 'cpp-library'
                library {
                    targetMachines = [machines.${currentHostOperatingSystemFamilyDsl}.architecture('foo')]
                }
                ${configureToolChainSupport('foo')}
            }
        """
        file("gradle.properties").text = "${ResolutionFailureHandler.FULL_FAILURES_MESSAGE_PROPERTY}=true"

        app.greeter.writeToProject(file("greeter"))
        app.main.writeToProject(file("app"))

        expect:
        fails ":app:assemble"

        and:
        failure.assertHasCause("Could not resolve project :greeter")
        failure.assertHasErrorOutput("Incompatible because this component declares attribute 'org.gradle.native.architecture' with value 'foo', attribute 'org.gradle.usage' with value 'native-link' and the consumer needed attribute 'org.gradle.native.architecture' with value '${currentArchitecture}', attribute 'org.gradle.usage' with value 'native-runtime'")
    }

    @ToBeFixedForConfigurationCache
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

            def headerDirectory = objects.directoryProperty()

            task generateHeader {
                outputs.dir(headerDirectory)
                headerDirectory.set(layout.buildDirectory.dir("headers"))
                doLast {
                    def fooH = headerDirectory.file("foo.h").get().asFile
                    fooH.parentFile.mkdirs()
                    fooH << '''
                        #define EXIT_VALUE 0
                    '''
                }
            }

            application.binaries.whenElementFinalized { binary ->
                def dependency = project.dependencies.create(files(headerDirectory))
                binary.getIncludePathConfiguration().dependencies.add(dependency)
            }
         """

        expect:
        succeeds "compileDebug"
    }

    @ToBeFixedForConfigurationCache
    def "can compile and link against a library with explicit target machine defined"() {
        createDirs("app", "hello")
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
                    targetMachines = [machines.os('${currentOsFamilyName}').architecture('${currentArchitecture}')]
                }
            }
            project(':hello') {
                apply plugin: 'cpp-library'
                library {
                    targetMachines = [machines.os('${currentOsFamilyName}').architecture('${currentArchitecture}')]
                }
            }
        """
        app.greeter.writeToProject(file("hello"))
        app.main.writeToProject(file("app"))

        expect:
        succeeds ":app:assemble"

        result.assertTasksExecuted(tasks(':hello').debug.allToLink, tasks(':app').debug.allToInstall, ":app:assemble")
        executable("app/build/exe/main/debug/app").assertExists()
        sharedLibrary("hello/build/lib/main/debug/hello").assertExists()
        def installation = installation("app/build/install/main/debug")
        installation.exec().out == app.expectedOutput
        installation.assertIncludesLibraries("hello")
    }

    def "fails compile and link against a library with different operating system family support"() {
        createDirs("app", "hello")
        settingsFile << """
            rootProject.name = 'test'
            include 'app', 'hello'
        """
        def app = new CppAppWithLibrary()

        given:
        buildFile << """
            project(':app') {
                apply plugin: 'cpp-application'
                application {
                    dependencies {
                        implementation project(':hello')
                    }
                    targetMachines = [machines.os('${currentOsFamilyName}').architecture('${currentArchitecture}')]
                }
            }
            project(':hello') {
                apply plugin: 'cpp-library'
                library {
                    targetMachines = [machines.os('some-other-family').architecture('${currentArchitecture}')]
                }
            }
        """
        file("gradle.properties").text = "${ResolutionFailureHandler.FULL_FAILURES_MESSAGE_PROPERTY}=true"

        app.greeter.writeToProject(file("hello"))
        app.main.writeToProject(file("app"))

        expect:
        fails ":app:assemble"

        failure.assertHasCause """No matching variant of project :hello was found. The consumer was configured to find attribute 'org.gradle.usage' with value 'native-runtime', attribute 'org.gradle.native.debuggable' with value 'true', attribute 'org.gradle.native.optimized' with value 'false', attribute 'org.gradle.native.operatingSystem' with value '${currentOsFamilyName}', attribute 'org.gradle.native.architecture' with value '${currentArchitecture}' but:
  - Variant 'cppApiElements' capability test:hello:unspecified:
      - Incompatible because this component declares attribute 'org.gradle.usage' with value 'cplusplus-api' and the consumer needed attribute 'org.gradle.usage' with value 'native-runtime'
      - Other compatible attributes:
          - Doesn't say anything about org.gradle.native.architecture (required '${currentArchitecture}')
          - Doesn't say anything about org.gradle.native.debuggable (required 'true')
          - Doesn't say anything about org.gradle.native.operatingSystem (required '${currentOsFamilyName}')
          - Doesn't say anything about org.gradle.native.optimized (required 'false')"""
    }

    @ToBeFixedForConfigurationCache
    def "can compile and link against a static library"() {
        createDirs("app", "hello")
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

        result.assertTasksExecuted(tasks(':hello').debug.allToCreate, tasks(':app').debug.allToInstall, ':app:assemble')
        executable("app/build/exe/main/debug/app").assertExists()
        staticLibrary("hello/build/lib/main/debug/hello").assertExists()
        def installation = installation("app/build/install/main/debug")
        installation.exec().out == app.expectedOutput
        installation.assertIncludesLibraries()
    }

    @ToBeFixedForConfigurationCache
    def "can compile and link against a library with both linkages defined"() {
        createDirs("app", "hello")
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

        result.assertTasksExecuted(tasks(':hello').withBuildType(debugShared).allToLink, tasks(':app').debug.allToInstall, ":app:assemble")
        executable("app/build/exe/main/debug/app").assertExists()
        sharedLibrary("hello/build/lib/main/debug/shared/hello").assertExists()
        def installation = installation("app/build/install/main/debug")
        installation.exec().out == app.expectedOutput
        installation.assertIncludesLibraries("hello")
    }

    @ToBeFixedForConfigurationCache
    def "can compile and link against a library with debug and release variants"() {
        createDirs("app", "hello")
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
        succeeds tasks(':app').release.assemble

        result.assertTasksExecuted(tasks(':hello').release.allToLink, tasks(':app').release.allToInstall, tasks(':app').release.extract, tasks(':app').release.assemble)
        executable("app/build/exe/main/release/app").assertExists()
        executable("app/build/exe/main/release/app").assertHasStrippedDebugSymbolsFor(app.main.sourceFileNames)
        sharedLibrary("hello/build/lib/main/release/hello").assertExists()
        sharedLibrary("hello/build/lib/main/release/hello").assertHasDebugSymbolsFor(app.greeterLib.sourceFileNamesWithoutHeaders)
        installation("app/build/install/main/release").exec().out == app.withFeatureEnabled().expectedOutput

        succeeds tasks(':app').debug.assemble

        result.assertTasksExecuted(tasks(':hello').debug.allToLink, tasks(':app').debug.allToInstall, tasks(':app').debug.assemble)

        executable("app/build/exe/main/debug/app").assertExists()
        executable("app/build/exe/main/debug/app").assertHasDebugSymbolsFor(app.main.sourceFileNames)
        sharedLibrary("hello/build/lib/main/debug/hello").assertExists()
        sharedLibrary("hello/build/lib/main/debug/hello").assertHasDebugSymbolsFor(app.greeterLib.sourceFileNamesWithoutHeaders)
        installation("app/build/install/main/debug").exec().out == app.withFeatureDisabled().expectedOutput
    }

    @ToBeFixedForConfigurationCache
    def "can compile and link against library with api and implementation dependencies"() {
        createDirs("app", "deck", "card", "shuffle")
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

        result.assertTasksExecuted([':card', ':deck', ':shuffle'].collect { tasks(it).debug.allToLink }, tasks(':app').debug.allToInstall, ":app:assemble")
        sharedLibrary("deck/build/lib/main/debug/deck").assertExists()
        sharedLibrary("card/build/lib/main/debug/card").assertExists()
        sharedLibrary("shuffle/build/lib/main/debug/shuffle").assertExists()
        executable("app/build/exe/main/debug/app").assertExists()
        def installation = installation("app/build/install/main/debug")
        installation.assertIncludesLibraries("deck", "card", "shuffle")
        installation.exec().out == app.expectedOutput
    }

    @ToBeFixedForConfigurationCache
    def "can compile and link against a static library with api and implementation dependencies"() {
        createDirs("app", "deck", "card", "shuffle")
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

        result.assertTasksExecuted([':card', ':deck', ':shuffle'].collect { tasks(it).debug.allToCreate }, tasks(':app').debug.allToInstall, ":app:assemble")
        staticLibrary("deck/build/lib/main/debug/deck").assertExists()
        staticLibrary("card/build/lib/main/debug/card").assertExists()
        staticLibrary("shuffle/build/lib/main/debug/shuffle").assertExists()
        executable("app/build/exe/main/debug/app").assertExists()
        def installation = installation("app/build/install/main/debug")
        installation.assertIncludesLibraries()
        installation.exec().out == app.expectedOutput
    }

    @ToBeFixedForConfigurationCache
    def "honors changes to library buildDir"() {
        createDirs("app", "lib1", "lib2")
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

        result.assertTasksExecuted([':lib1', ':lib2'].collect { tasks(it).debug.allToLink }, tasks(':app').debug.allToInstall, ":app:assemble")

        !file("lib2/build").exists()
        sharedLibrary("lib1/build/lib/main/debug/lib1").assertExists()
        sharedLibrary("lib2/out/lib/main/debug/lib2").assertExists()
        executable("app/build/exe/main/debug/app").assertExists()
        installation("app/build/install/main/debug").exec().out == app.expectedOutput
        sharedLibrary("app/build/install/main/debug/lib/lib1").file.assertExists()
        sharedLibrary("app/build/install/main/debug/lib/lib2").file.assertExists()
    }

    @ToBeFixedForConfigurationCache
    def "honors changes to library output locations"() {
        createDirs("app", "lib1", "lib2")
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

        result.assertTasksExecuted([':lib1', ':lib2'].collect { tasks(it).debug.allToLink }, tasks(':app').debug.allToInstall, ":app:assemble")

        file("lib2/build/shared/lib1_debug.dll").assertIsFile()
        if (toolChain.visualCpp) {
            file("lib2/build/import/lib1_import.lib").assertIsFile()
        }
        installation("app/build/install/main/debug").exec().out == app.expectedOutput
        sharedLibrary("app/build/install/main/debug/lib/lib1").file.assertExists()
        file("app/build/install/main/debug/lib/lib1_debug.dll").assertIsFile()
    }

    @ToBeFixedForConfigurationCache
    def "honors changes to library public header location"() {
        createDirs("app", "lib1", "lib2")
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

        result.assertTasksExecuted([':lib1', ':lib2'].collect { tasks(it).debug.allToLink }, tasks(':app').debug.allToInstall, ":app:assemble")

        sharedLibrary("lib1/build/lib/main/debug/lib1").assertExists()
        sharedLibrary("lib2/build/lib/main/debug/lib2").assertExists()
        executable("app/build/exe/main/debug/app").assertExists()
        installation("app/build/install/main/debug").exec().out == app.expectedOutput
        sharedLibrary("app/build/install/main/debug/lib/lib1").file.assertExists()
        sharedLibrary("app/build/install/main/debug/lib/lib2").file.assertExists()
    }

    @ToBeFixedForConfigurationCache
    def "multiple components can share the same source directory"() {
        createDirs("app", "greeter", "logger")
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
        result.assertTasksExecuted([':greeter', ":logger"].collect { tasks(it).debug.allToLink }, tasks(':app').debug.allToInstall, ":app:assemble")

        sharedLibrary("greeter/build/lib/main/debug/greeter").assertExists()
        sharedLibrary("logger/build/lib/main/debug/logger").assertExists()
        executable("app/build/exe/main/debug/app").assertExists()
        installation("app/build/install/main/debug").exec().out == app.expectedOutput
        sharedLibrary("app/build/install/main/debug/lib/greeter").file.assertExists()
        sharedLibrary("app/build/install/main/debug/lib/logger").file.assertExists()
    }

    @ToBeFixedForConfigurationCache
    def "can compile and link against libraries in included builds"() {
        createDirs("app", "lib1", "lib2")
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
        result.assertTasksExecuted([':lib1', ":lib2"].collect { tasks(it).debug.allToLink }, tasks.debug.allToInstall, ":assemble")
        sharedLibrary("lib1/build/lib/main/debug/lib1").assertExists()
        sharedLibrary("lib2/build/lib/main/debug/lib2").assertExists()
        executable("build/exe/main/debug/app").assertExists()
        installation("build/install/main/debug").exec().out == app.expectedOutput
        sharedLibrary("build/install/main/debug/lib/lib1").file.assertExists()
        sharedLibrary("build/install/main/debug/lib/lib2").file.assertExists()
    }

    @RequiresInstalledToolChain(ToolChainRequirement.GCC_COMPATIBLE)
    @ToBeFixedForConfigurationCache
    def "system headers are not evaluated when compiler warnings are enabled"() {
        settingsFile << "rootProject.name = 'app'"
        def app = new CppCompilerDetectingTestApp()

        given:
        app.writeSources(file('src/main'))

        and:
        buildFile << """
            apply plugin: 'cpp-application'

            application {
                binaries.configureEach {
                    compileTask.get().compilerArgs.add("-Wall")
                    compileTask.get().compilerArgs.add("-Werror")
                }
            }
         """

        expect:
        succeeds "assemble"
        result.assertTasksExecuted(tasks.debug.allToInstall, ':assemble')

        executable("build/exe/main/debug/app").assertExists()
        installation("build/install/main/debug").exec().out == app.expectedOutput(toolChain)
    }

    @Issue("https://github.com/gradle/gradle-native/issues/950")
    @ToBeFixedForConfigurationCache
    def "can handle candidate header directory which happens to match an existing file"() {
        def app = new CppApp()

        given:
        app.sources.writeToSourceDir(file('src/main/cpp'))
        app.greeter.headers.writeToSourceDir(file('src/main/headers'))
        app.sum.headers.writeToSourceDir(file('src/sumHeaders/foo'))

        file("src/main/cpp/main.cpp").text = file("src/main/cpp/main.cpp").text.replace("sum.h", "foo/sum.h")
        file("src/main/cpp/sum.cpp").text = file("src/main/cpp/sum.cpp").text.replace("sum.h", "foo/sum.h")

        // poison file
        file('src/main/headers/foo').createNewFile()

        buildFile << """
            apply plugin: 'cpp-application'

            application {
                privateHeaders.from 'src/main/headers', 'src/sumHeaders'
            }
        """

        expect:
        succeeds "assemble"
    }
}
