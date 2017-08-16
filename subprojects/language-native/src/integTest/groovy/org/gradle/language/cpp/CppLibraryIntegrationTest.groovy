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
import org.gradle.nativeplatform.fixtures.app.CppAppWithLibraries
import org.gradle.nativeplatform.fixtures.app.CppLib
import org.junit.Assume

import static org.gradle.util.Matchers.containsText

class CppLibraryIntegrationTest extends AbstractInstalledToolChainIntegrationSpec {
    def setup() {
        // TODO - currently the customizations to the tool chains are ignored by the plugins, so skip these tests until this is fixed
        Assume.assumeTrue(toolChain.id != "mingw" && toolChain.id != "gcccygwin")
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
        failure.assertHasDescription("Execution failed for task ':compileCpp'.");
        failure.assertHasCause("A build operation failed.")
        failure.assertThatCause(containsText("C++ compiler failed while compiling broken.cpp"))
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
        result.assertTasksExecuted(":compileCpp", ":linkMain", ":assemble")
        sharedLibrary("build/lib/hello").assertExists()
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
        result.assertTasksExecuted(":compileCpp", ":linkMain", ":assemble")

        sharedLibrary("build/lib/hello").assertExists()
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
        result.assertTasksExecuted(":compileCpp", ":linkMain", ":assemble")

        sharedLibrary("build/lib/hello").assertExists()
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
        result.assertTasksExecuted(":compileCpp", ":linkMain", ":assemble")

        !file("build").exists()
        file("output/main/objs").assertIsDir()
        sharedLibrary("output/lib/hello").assertExists()
    }

    def "honors changes to task output locations"() {
        given:
        settingsFile << "rootProject.name = 'hello'"
        def lib = new CppLib()
        lib.writeToProject(testDirectory)

        and:
        buildFile << """
            apply plugin: 'cpp-library'
            compileCpp.objectFileDirectory.set(layout.buildDirectory.dir("object-files"))
            linkMain.binaryFile.set(layout.buildDirectory.file("some-lib/main.bin"))
         """

        expect:
        succeeds "assemble"
        result.assertTasksExecuted(":compileCpp", ":linkMain", ":assemble")

        file("build/object-files").assertIsDir()
        file("build/some-lib/main.bin").assertIsFile()
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
        result.assertTasksExecuted(":compileCpp", ":linkMain", ":assemble")
        sharedLibrary("build/lib/hello").assertExists()
    }

    def "can compile and link against another library"() {
        settingsFile << "include 'lib1', 'lib2'"
        def app = new CppAppWithLibraries()

        given:
        buildFile << """
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
        app.greeterLib.writeToProject(file("lib1"))
        app.loggerLib.writeToProject(file("lib2"))

        expect:
        succeeds ":lib1:assemble"
        result.assertTasksExecuted(":lib2:compileCpp", ":lib2:linkMain", ":lib1:compileCpp", ":lib1:linkMain", ":lib1:assemble")
        sharedLibrary("lib1/build/lib/lib1").assertExists()
        sharedLibrary("lib2/build/lib/lib2").assertExists()
    }

    def "can change default base name and successfully link against library"() {
        settingsFile << "include 'lib1', 'lib2'"
        def app = new CppAppWithLibraries()

        given:
        buildFile << """
            project(':lib1') {
                apply plugin: 'cpp-library'
                library {
                    baseName.set('hello')
                }
                dependencies {
                    implementation project(':lib2')
                }
            }
            project(':lib2') {
                apply plugin: 'cpp-library'
                library {
                    baseName.set('log')
                }
            }
"""
        app.greeterLib.writeToProject(file("lib1"))
        app.loggerLib.writeToProject(file("lib2"))

        expect:
        succeeds ":lib1:assemble"
        result.assertTasksExecuted(":lib2:compileCpp", ":lib2:linkMain", ":lib1:compileCpp", ":lib1:linkMain", ":lib1:assemble")
        sharedLibrary("lib1/build/lib/hello").assertExists()
        sharedLibrary("lib2/build/lib/log").assertExists()
    }

}
