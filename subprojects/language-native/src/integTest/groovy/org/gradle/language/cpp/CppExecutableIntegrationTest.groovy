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

import org.gradle.nativeplatform.fixtures.AbstractInstalledToolChainIntegrationSpec
import org.gradle.nativeplatform.fixtures.app.CppApp
import org.gradle.nativeplatform.fixtures.app.CppCompilerDetectingTestApp
import org.gradle.nativeplatform.fixtures.app.CppHelloWorldApp
import org.gradle.nativeplatform.fixtures.app.ExeWithLibraryUsingLibraryHelloWorldApp
import org.junit.Assume

import static org.gradle.util.Matchers.containsText

class CppExecutableIntegrationTest extends AbstractInstalledToolChainIntegrationSpec {
    def setup() {
        // TODO - currently the customizations to the tool chains are ignored by the plugins, so skip these tests until this is fixed
        Assume.assumeTrue(toolChain.id != "mingw" && toolChain.id != "gcccygwin")
    }

    def "build fails when compilation fails"() {
        given:
        buildFile << """
            apply plugin: 'cpp-executable'
         """

        and:
        file("src/main/cpp/broken.cpp") << """
        #include <iostream>

        'broken
"""

        expect:
        fails "assemble"
        failure.assertHasDescription("Execution failed for task ':compileCpp'.");
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
            apply plugin: 'cpp-executable'
         """

        expect:
        succeeds "assemble"
        result.assertTasksExecuted(":compileCpp", ":linkMain", ":installMain", ":assemble")

        executable("build/exe/app").assertExists()
        installation("build/install/app").exec().out == app.expectedOutput(AbstractInstalledToolChainIntegrationSpec.toolChain)
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
            apply plugin: 'cpp-executable'
         """

        expect:
        succeeds "assemble"
        result.assertTasksExecuted(":compileCpp", ":linkMain", ":installMain", ":assemble")

        executable("build/exe/app").assertExists()
        installation("build/install/app").exec().out == app.expectedOutput
    }

    def "build logic can change source layout convention"() {
        settingsFile << "rootProject.name = 'app'"
        def app = new CppApp()

        given:
        app.writeToSourceDir(file("srcs"))
        file("src/main/cpp/broken.cpp") << "ignore me!"

        and:
        buildFile << """
            apply plugin: 'cpp-executable'
            executable {
                source.from 'srcs'
            }
         """

        expect:
        succeeds "assemble"
        result.assertTasksExecuted(":compileCpp", ":linkMain", ":installMain", ":assemble")

        file("build/main/objs").assertIsDir()
        executable("build/exe/app").assertExists()
        installation("build/install/app").exec().out == app.expectedOutput
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
            apply plugin: 'cpp-executable'
            executable {
                source {
                    from('srcs/main.cpp')
                    from('srcs/one.cpp')
                    from('srcs/two.cpp')
                }
            }
         """

        expect:
        succeeds "assemble"
        result.assertTasksExecuted(":compileCpp", ":linkMain", ":installMain", ":assemble")

        file("build/main/objs").assertIsDir()
        executable("build/exe/app").assertExists()
        installation("build/install/app").exec().out == app.expectedOutput
    }

    def "honors changes to buildDir"() {
        settingsFile << "rootProject.name = 'app'"
        def app = new CppApp()

        given:
        app.writeToProject(testDirectory)

        and:
        buildFile << """
            apply plugin: 'cpp-executable'
            buildDir = 'output'
         """

        expect:
        succeeds "assemble"
        result.assertTasksExecuted(":compileCpp", ":linkMain", ":installMain", ":assemble")

        !file("build").exists()
        file("output/main/objs").assertIsDir()
        executable("output/exe/app").assertExists()
        installation("output/install/app").exec().out == app.expectedOutput
    }

    def "honors changes to task output locations"() {
        settingsFile << "rootProject.name = 'app'"
        def app = new CppApp()

        given:
        app.writeToProject(testDirectory)

        and:
        buildFile << """
            apply plugin: 'cpp-executable'
            compileCpp.objectFileDirectory.set(layout.buildDirectory.dir("object-files"))
            linkMain.binaryFile.set(layout.buildDirectory.file("exe/some-app.exe"))
            installMain.installDirectory.set(layout.buildDirectory.dir("some-app"))
         """

        expect:
        succeeds "assemble"
        result.assertTasksExecuted(":compileCpp", ":linkMain", ":installMain", ":assemble")

        file("build/exe/some-app.exe").assertExists()
        installation("build/some-app").exec().out == app.expectedOutput
    }

    def "can compile and link against a library"() {
        settingsFile << "include 'app', 'hello'"
        def app = new CppHelloWorldApp()

        given:
        buildFile << """
            project(':app') {
                apply plugin: 'cpp-executable'
                dependencies {
                    implementation project(':hello')
                }
            }
            project(':hello') {
                apply plugin: 'cpp-library'
            }
"""
        app.library.headerFiles.each { it.writeToFile(file("hello/src/main/public/$it.name")) }
        app.library.sourceFiles.each { it.writeToFile(file("hello/src/main/cpp/$it.name")) }
        app.executable.sourceFiles.each { it.writeToDir(file('app/src/main')) }

        expect:
        succeeds ":app:assemble"
        result.assertTasksExecuted(":hello:compileCpp", ":hello:linkMain", ":app:compileCpp", ":app:linkMain", ":app:installMain", ":app:assemble")
        executable("app/build/exe/app").assertExists()
        sharedLibrary("hello/build/lib/hello").assertExists()
        installation("app/build/install/app").exec().out == app.englishOutput
        sharedLibrary("app/build/install/app/lib/hello").file.assertExists()
    }

    def "can compile and link against library with dependencies"() {
        settingsFile << "include 'app', 'lib1', 'lib2'"
        def app = new ExeWithLibraryUsingLibraryHelloWorldApp()

        given:
        buildFile << """
            project(':app') {
                apply plugin: 'cpp-executable'
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
            }
"""
        app.library.headerFiles.each { it.writeToFile(file("lib1/src/main/public/$it.name")) }
        app.library.sourceFiles.each { it.writeToFile(file("lib1/src/main/cpp/$it.name")) }
        app.greetingsLibrary.headerFiles.each { it.writeToFile(file("lib2/src/main/public/$it.name")) }
        app.greetingsLibrary.sourceFiles.each { it.writeToFile(file("lib2/src/main/cpp/$it.name")) }
        app.executable.sourceFiles.each { it.writeToDir(file('app/src/main')) }

        expect:
        succeeds ":app:assemble"
        result.assertTasksExecuted(":lib1:compileCpp", ":lib1:linkMain", ":lib2:compileCpp", ":lib2:linkMain", ":app:compileCpp", ":app:linkMain", ":app:installMain", ":app:assemble")
        sharedLibrary("lib1/build/lib/lib1").assertExists()
        sharedLibrary("lib2/build/lib/lib2").assertExists()
        executable("app/build/exe/app").assertExists()
        installation("app/build/install/app").exec().out == app.englishOutput
        sharedLibrary("app/build/install/app/lib/lib1").file.assertExists()
        sharedLibrary("app/build/install/app/lib/lib2").file.assertExists()
    }

    def "honors changes to library buildDir"() {
        settingsFile << "include 'app', 'lib1', 'lib2'"
        def app = new ExeWithLibraryUsingLibraryHelloWorldApp()

        given:
        buildFile << """
            project(':app') {
                apply plugin: 'cpp-executable'
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
        app.library.headerFiles.each { it.writeToFile(file("lib1/src/main/public/$it.name")) }
        app.library.sourceFiles.each { it.writeToFile(file("lib1/src/main/cpp/$it.name")) }
        app.greetingsLibrary.headerFiles.each { it.writeToFile(file("lib2/src/main/public/$it.name")) }
        app.greetingsLibrary.sourceFiles.each { it.writeToFile(file("lib2/src/main/cpp/$it.name")) }
        app.executable.sourceFiles.each { it.writeToDir(file('app/src/main')) }

        expect:
        succeeds ":app:assemble"
        result.assertTasksExecuted(":lib1:compileCpp", ":lib1:linkMain", ":lib2:compileCpp", ":lib2:linkMain", ":app:compileCpp", ":app:linkMain", ":app:installMain", ":app:assemble")

        !file("lib2/build").exists()
        sharedLibrary("lib1/build/lib/lib1").assertExists()
        sharedLibrary("lib2/out/lib/lib2").assertExists()
        executable("app/build/exe/app").assertExists()
        installation("app/build/install/app").exec().out == app.englishOutput
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

        def app = new ExeWithLibraryUsingLibraryHelloWorldApp()

        given:
        buildFile << """
            apply plugin: 'cpp-executable'
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

        app.library.headerFiles.each { it.writeToFile(file("lib1/src/main/public/$it.name")) }
        app.library.sourceFiles.each { it.writeToFile(file("lib1/src/main/cpp/$it.name")) }
        app.greetingsLibrary.headerFiles.each { it.writeToFile(file("lib2/src/main/public/$it.name")) }
        app.greetingsLibrary.sourceFiles.each { it.writeToFile(file("lib2/src/main/cpp/$it.name")) }
        app.executable.sourceFiles.each { it.writeToDir(file('src/main')) }

        expect:
        succeeds ":assemble"
        result.assertTasksExecuted(":lib1:compileCpp", ":lib1:linkMain",  ":lib2:compileCpp", ":lib2:linkMain", ":compileCpp", ":linkMain", ":installMain", ":assemble")
        sharedLibrary("lib1/build/lib/lib1").assertExists()
        sharedLibrary("lib2/build/lib/lib2").assertExists()
        executable("build/exe/app").assertExists()
        installation("build/install/app").exec().out == app.englishOutput
        sharedLibrary("build/install/app/lib/lib1").file.assertExists()
        sharedLibrary("build/install/app/lib/lib2").file.assertExists()
    }
}
