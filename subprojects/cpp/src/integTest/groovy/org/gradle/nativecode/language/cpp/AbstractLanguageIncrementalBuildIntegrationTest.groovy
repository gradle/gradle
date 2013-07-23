/*
 * Copyright 2013 the original author or authors.
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




package org.gradle.nativecode.language.cpp
import org.gradle.nativecode.language.cpp.fixtures.AbstractBinariesIntegrationSpec
import org.gradle.nativecode.language.cpp.fixtures.app.IncrementalHelloWorldApp
import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition

import static org.gradle.util.TextUtil.escapeString

abstract class AbstractLanguageIncrementalBuildIntegrationTest extends AbstractBinariesIntegrationSpec {
    IncrementalHelloWorldApp app
    String mainCompileTask
    String libraryCompileTask
    TestFile sourceFile
    TestFile headerFile

    abstract IncrementalHelloWorldApp getHelloWorldApp();

    def "setup"() {
        app = getHelloWorldApp()
        mainCompileTask = ":compileMainExecutableMain${app.sourceType}"
        libraryCompileTask = ":compileHelloSharedLibraryHello${app.sourceType}"

        buildFile << """
            apply plugin: 'cpp'

            sources {
                main {}
                hello {}
            }

            executables {
                main {
                    source sources.main
                }
            }
            libraries {
                hello {
                    source sources.hello
                    binaries.withType(SharedLibraryBinary) {
                        define "DLL_EXPORT"
                    }
                }
            }
            sources.main.cpp.lib libraries.hello
        """
        settingsFile << "rootProject.name = 'test'"

        sourceFile = write("main", app.mainSource)
        headerFile = write("hello", app.libraryHeader)
        app.librarySources.each {
            write("hello", it)
        }

        run "installMainExecutable"
    }

    def "does not re-execute build with no change"() {
        when:
        run "installMainExecutable"

        then:
        nonSkippedTasks.empty
    }

    @Requires(TestPrecondition.CAN_INSTALL_EXECUTABLE)
    def "rebuilds binary with source file change"() {
        given:
        def executable = executable("build/install/mainExecutable/main")

        when:
        sourceFile.text = app.alternateMainSource.content

        and:
        run "installMainExecutable"

        then:
        skipped libraryCompileTask
        skipped ":linkHelloSharedLibrary"
        skipped ":helloSharedLibrary"
        executedAndNotSkipped mainCompileTask
        executedAndNotSkipped ":linkMainExecutable"
        executedAndNotSkipped ":mainExecutable"
        executedAndNotSkipped ":installMainExecutable"

        and:
        executable.assertExists()
        executable.exec().out == app.alternateOutput
    }

    @Requires(TestPrecondition.CAN_INSTALL_EXECUTABLE)
    def "relinks binary with library source file change"() {
        when:
        def executable = executable("build/install/mainExecutable/main")
        app.alternateLibrarySources.each { sourceFile ->
            write("hello", sourceFile)
        }

        and:
        run "installMainExecutable"

        then:
        executedAndNotSkipped libraryCompileTask
        executedAndNotSkipped ":linkHelloSharedLibrary"
        executedAndNotSkipped ":helloSharedLibrary"
        skipped mainCompileTask
        executedAndNotSkipped ":linkMainExecutable"
        executedAndNotSkipped ":mainExecutable"
        executedAndNotSkipped ":installMainExecutable"

        and:
        executable.assertExists()
        executable.exec().out == app.alternateLibraryOutput
    }

    def "recompiles binary when header file changes in a way that does not affect the object files"() {
        if (toolChain.visualCpp) {
            return // Visual C++ compiler embeds a timestamp in every object file, so relinking is always required after recompiling
        }

        when:
        headerFile << """
// Comment added to the end of the header file
"""

        run "installMainExecutable"

        then:
        executedAndNotSkipped libraryCompileTask
        skipped ":linkHelloSharedLibrary"
        skipped ":helloSharedLibrary"
        executedAndNotSkipped mainCompileTask
        skipped ":linkMainExecutable"
        skipped ":mainExecutable"
        skipped ":installMainExecutable"
    }

    @Requires(TestPrecondition.CAN_INSTALL_EXECUTABLE)
    def "rebuilds binary with compiler option change"() {
        when:
        def executable = executable("build/install/mainExecutable/main")

        and:
        buildFile << """
            libraries {
                hello {
                    binaries.all { compilerArgs '-DFRENCH' }
                }
            }
"""

        run "installMainExecutable"

        then:
        executedAndNotSkipped libraryCompileTask
        executedAndNotSkipped ":linkHelloSharedLibrary"
        executedAndNotSkipped ":helloSharedLibrary"
        skipped mainCompileTask
        executedAndNotSkipped ":linkMainExecutable"
        executedAndNotSkipped ":mainExecutable"
        executedAndNotSkipped ":installMainExecutable"

        and:
        executable.assertExists()
        executable.exec().out == app.frenchOutput
    }

    def "relinks binary when set of input libraries changes"() {
        def executable = executable("build/binaries/mainExecutable/main")
        def snapshot = executable.snapshot()

        when:
        buildFile << """
            executables {
                main {
                    binaries.all {
                        lib libraries.hello.static
                    }
                }
            }
"""

        run "installMainExecutable"

        then:
        skipped libraryCompileTask
        skipped ":linkHelloSharedLibrary"
        skipped ":helloSharedLibrary"
        skipped mainCompileTask
        executedAndNotSkipped ":linkMainExecutable"
        executedAndNotSkipped ":mainExecutable"
        executedAndNotSkipped ":installMainExecutable"

        and:
        executable.assertHasChangedSince(snapshot)
    }

    def "relinks binary but does not recompile when linker option changed"() {
        when:
        def executable = executable("build/binaries/mainExecutable/main")
        def snapshot = executable.snapshot()

        and:
        def linkerArgs = toolChain.isVisualCpp() ? "'/DEBUG'" : "'-S'"
        linkerArgs = escapeString(linkerArgs)
        buildFile << """
            executables {
                main {
                    binaries.all { linkerArgs ${escapeString(linkerArgs)} }
                }
            }
"""

        run "installMainExecutable"

        then:
        skipped libraryCompileTask
        skipped ":linkHelloSharedLibrary"
        skipped ":helloSharedLibrary"
        skipped mainCompileTask
        executedAndNotSkipped ":linkMainExecutable"
        executedAndNotSkipped ":mainExecutable"

        and:
        executable.assertExists()
        executable.assertHasChangedSince(snapshot)
    }

    def "recompiles source but does not relink binary with source comment change"() {
        if (toolChain.visualCpp) {
            return // Visual C++ compiler embeds a timestamp in every object file, so relinking is always required after recompiling
        }
        when:
        sourceFile.text = sourceFile.text.replaceFirst("// Simple hello world app", "// Comment is changed")
        run "installMainExecutable"

        then:
        skipped libraryCompileTask
        skipped ":linkHelloSharedLibrary"
        skipped ":helloSharedLibrary"
        executedAndNotSkipped mainCompileTask
        skipped ":linkMainExecutable"
        skipped ":mainExecutable"
        skipped ":installMainExecutable"
    }

    def "cleans up stale object files when source file renamed"() {
        def oldObjFile = objectFile("build/objectFiles/mainExecutable/main${app.sourceType}/main")
        def newObjFile = objectFile("build/objectFiles/mainExecutable/main${app.sourceType}/changed_main")
        assert oldObjFile.file
        assert !newObjFile.file

        when:
        sourceFile.renameTo("${sourceFile.parentFile.absolutePath}/changed_${sourceFile.name}")
        run "mainExecutable"

        then:
        skipped libraryCompileTask
        skipped ":linkHelloSharedLibrary"
        skipped ":helloSharedLibrary"
        executedAndNotSkipped mainCompileTask
        executedAndNotSkipped ":linkMainExecutable"
        executedAndNotSkipped ":mainExecutable"

        and:
        !oldObjFile.file
        newObjFile.file
    }

    def "cleans up stale debug files when changing from debug to non-debug"() {
        if (!toolChain.visualCpp) {
            return
        }

        given:
        buildFile << """
            binaries.all { compilerArgs '/Zi'; linkerArgs '/DEBUG'; }
        """
        run "mainExecutable"

        def executable = executable("build/binaries/mainExecutable/main")
        executable.assertDebugFileExists()

        when:
        buildFile << """
            binaries.all { compilerArgs.clear(); linkerArgs.clear(); }
        """
        run "mainExecutable"

        then:
        executedAndNotSkipped libraryCompileTask
        executedAndNotSkipped ":helloSharedLibrary"
        executedAndNotSkipped mainCompileTask
        executedAndNotSkipped ":linkMainExecutable"
        executedAndNotSkipped ":mainExecutable"

        and:
        executable.assertDebugFileDoesNotExist()
    }

    TestFile write(def path, def sourceFile) {
        final file = file("src/$path/${sourceFile.path}/${sourceFile.name}")
        if (file.exists()) {
            file.text = ""
        }
        file << sourceFile.content
    }

}
