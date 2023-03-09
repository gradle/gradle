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

package org.gradle.language

import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.internal.os.OperatingSystem
import org.gradle.nativeplatform.fixtures.AbstractInstalledToolChainIntegrationSpec
import org.gradle.nativeplatform.fixtures.RequiresInstalledToolChain
import org.gradle.nativeplatform.fixtures.ToolChainRequirement
import org.gradle.nativeplatform.fixtures.app.IncrementalHelloWorldApp
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.precondition.Requires
import org.gradle.test.precondition.TestPrecondition
import org.gradle.test.preconditions.UnitTestPreconditions
import org.gradle.util.internal.GUtil
import org.junit.Assume
import spock.lang.IgnoreIf
import spock.lang.Issue

import static org.gradle.nativeplatform.fixtures.ToolChainRequirement.GCC_COMPATIBLE
import static org.gradle.nativeplatform.fixtures.ToolChainRequirement.SUPPORTS_32_AND_64
import static org.gradle.nativeplatform.fixtures.ToolChainRequirement.VISUALCPP
import static org.gradle.util.internal.TextUtil.escapeString

abstract class AbstractNativeLanguageIncrementalBuildIntegrationTest extends AbstractInstalledToolChainIntegrationSpec {

    IncrementalHelloWorldApp app
    String mainCompileTask
    String libraryCompileTask
    TestFile sourceFile
    TestFile headerFile
    TestFile commonHeaderFile
    List<TestFile> librarySourceFiles = []

    boolean languageBuildsOnMultiplePlatforms() {
        return true
    }

    abstract IncrementalHelloWorldApp getHelloWorldApp()

    String getCompilerTool() {
        "${app.sourceType}Compiler"
    }

    String getSourceType() {
        GUtil.toCamelCase(app.sourceType)
    }

    def "setup"() {
        app = getHelloWorldApp()
        mainCompileTask = ":compileMainExecutableMain${sourceType}"
        libraryCompileTask = ":compileHelloSharedLibraryHello${sourceType}"

        buildFile << app.pluginScript
        buildFile << app.extraConfiguration

        buildFile << """
        model {
            components {
                main(NativeExecutableSpec) {
                    binaries.all {
                        lib library: 'hello'
                    }
                }
                hello(NativeLibrarySpec) {
                    binaries.withType(SharedLibraryBinarySpec) {
                        ${app.compilerDefine("DLL_EXPORT")}
                    }
                }
            }
        }
        """
        settingsFile << "rootProject.name = 'test'"
        sourceFile = app.mainSource.writeToDir(file("src/main"))
        headerFile = app.libraryHeader.writeToDir(file("src/hello"))
        commonHeaderFile = app.commonHeader.writeToDir(file("src/hello"))
        app.librarySources.each {
            librarySourceFiles << it.writeToDir(file("src/hello"))
        }
    }

    @ToBeFixedForConfigurationCache
    def "does not re-execute build with no change"() {
        given:
        run "installMainExecutable"

        when:
        run "installMainExecutable"

        then:
        allSkipped()
    }

    @IgnoreIf({ TestPrecondition.notSatisfies(UnitTestPreconditions.CanInstallExecutable) })
    @ToBeFixedForConfigurationCache
    def "rebuilds executable with source file change"() {
        given:
        run "installMainExecutable"

        def install = installation("build/install/main")

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
        install.assertInstalled()
        install.exec().out == app.alternateOutput
    }

    @ToBeFixedForConfigurationCache
    def "recompiles but does not relink executable with source comment change"() {
        given:
        run "installMainExecutable"
        maybeWait()

        when:
        sourceFile.text = sourceFile.text.replaceFirst("// Simple hello world app", "// Comment is changed")
        run "mainExecutable"

        then:
        skipped libraryCompileTask
        skipped ":linkHelloSharedLibrary"
        skipped ":helloSharedLibrary"

        executedAndNotSkipped mainCompileTask

        if (nonDeterministicCompilation) {
            // Relinking may (or may not) be required after recompiling
            executed ":linkMainExecutable", ":mainExecutable"
        } else {
            skipped ":linkMainExecutable"
            skipped ":mainExecutable"
        }
    }

    @Requires(UnitTestPreconditions.CanInstallExecutable)
    @ToBeFixedForConfigurationCache
    def "recompiles library and relinks executable after library source file change"() {
        given:
        run "installMainExecutable"
        maybeWait()
        def install = installation("build/install/main")

        when:
        for (int i = 0; i < librarySourceFiles.size(); i++) {
            TestFile sourceFile = librarySourceFiles.get(i)
            sourceFile.text = app.alternateLibrarySources[i].content
        }

        and:
        run "installMainExecutable"

        then:
        executedAndNotSkipped libraryCompileTask
        executedAndNotSkipped ":linkHelloSharedLibrary"
        skipped mainCompileTask
        executedAndNotSkipped ":installMainExecutable"

        and:
        install.assertInstalled()
        install.exec().out == app.alternateLibraryOutput
    }

    @ToBeFixedForConfigurationCache
    def "recompiles binary when header file changes"() {
        given:
        run "installMainExecutable"
        maybeWait()

        when:
        headerFile << """
            int unused();
"""
        run "mainExecutable"

        then:
        executedAndNotSkipped libraryCompileTask
        executedAndNotSkipped mainCompileTask

        if (nonDeterministicCompilation) {
            // Relinking may (or may not) be required after recompiling
            executed ":linkHelloSharedLibrary", ":helloSharedLibrary"
            executed ":linkMainExecutable", ":mainExecutable"
        } else {
            skipped ":linkHelloSharedLibrary", ":helloSharedLibrary"
            skipped ":linkMainExecutable", ":mainExecutable"
        }
    }

    @ToBeFixedForConfigurationCache
    def "recompiles binary when system header file changes"() {
        given:
        def systemHeader = file("src/main/system/${headerFile.name}")
        systemHeader.text = headerFile.text
        assert headerFile.delete()
        buildFile << """
            tasks.withType(AbstractNativeCompileTask) {
                systemIncludes.from("src/main/system")
            }
        """

        when:
        run "installMainExecutable"

        then:
        executedAndNotSkipped libraryCompileTask
        executedAndNotSkipped mainCompileTask

        when:
        run "installMainExecutable"

        then:
        skipped libraryCompileTask
        skipped mainCompileTask

        when:
        maybeWait()
        systemHeader << """
            int unused();
        """
        run "mainExecutable"

        then:
        executedAndNotSkipped libraryCompileTask
        executedAndNotSkipped mainCompileTask

        if (nonDeterministicCompilation) {
            // Relinking may (or may not) be required after recompiling
            executed ":linkHelloSharedLibrary", ":helloSharedLibrary"
            executed ":linkMainExecutable", ":mainExecutable"
        } else {
            skipped ":linkHelloSharedLibrary", ":helloSharedLibrary"
            skipped ":linkMainExecutable", ":mainExecutable"
        }

        when:
        run "installMainExecutable"

        then:
        skipped libraryCompileTask
        skipped mainCompileTask
    }

    @ToBeFixedForConfigurationCache
    def "recompiles binary when header file changes in a way that does not affect the object files"() {
        given:
        run "installMainExecutable"
        maybeWait()

        when:
        headerFile << """
// Comment added to the end of the header file
"""
        run "mainExecutable"

        then:
        executedAndNotSkipped libraryCompileTask
        executedAndNotSkipped mainCompileTask

        if (nonDeterministicCompilation) {
            // Relinking may (or may not) be required after recompiling
            executed ":linkHelloSharedLibrary", ":helloSharedLibrary"
            executed ":linkMainExecutable", ":mainExecutable"
        } else {
            skipped ":linkHelloSharedLibrary", ":helloSharedLibrary"
            skipped ":linkMainExecutable", ":mainExecutable"
        }
    }

    @Requires(UnitTestPreconditions.CanInstallExecutable)
    @ToBeFixedForConfigurationCache
    def "rebuilds binary with compiler option change"() {
        given:
        run "installMainExecutable"

        def install = installation("build/install/main")

        when:
        buildFile << """
        model {
            components {
                hello {
                    binaries.all {
                        ${helloWorldApp.compilerArgs("-DFRENCH")}
                    }
                }
            }
        }
"""

        maybeWait()
        run "installMainExecutable"

        then:
        executedAndNotSkipped libraryCompileTask
        executedAndNotSkipped ":linkHelloSharedLibrary"
        skipped mainCompileTask
        executedAndNotSkipped ":installMainExecutable"

        and:
        install.assertInstalled()
        install.exec().out == app.frenchOutput
    }

    @Requires(UnitTestPreconditions.CanInstallExecutable)
    @RequiresInstalledToolChain(SUPPORTS_32_AND_64)
    @ToBeFixedForConfigurationCache
    def "rebuilds binary with target platform change"() {
        Assume.assumeTrue(languageBuildsOnMultiplePlatforms())
        given:
        buildFile << """
    model {
        platforms {
            platform_x86 {
                architecture 'x86'
            }
            platform_x64 {
                architecture 'x86-64'
            }
        }
        components {
            main {
                targetPlatform 'platform_x86'
            }
            hello {
                targetPlatform 'platform_x86'
            }
        }
    }
"""
        run "mainExecutable"

        when:
        buildFile.text = buildFile.text.replace("'platform_x86'", " 'platform_x64'")
        sleep(500)
        run "mainExecutable"

        then:
        executedAndNotSkipped libraryCompileTask, mainCompileTask
        executedAndNotSkipped ":linkHelloSharedLibrary"
        executedAndNotSkipped ":helloSharedLibrary", ":mainExecutable"
    }

    @ToBeFixedForConfigurationCache
    def "relinks binary when set of input libraries changes"() {
        given:
        run "mainExecutable", "helloStaticLibrary"

        def executable = executable("build/exe/main/main")
        def snapshot = executable.snapshot()

        when:
        buildFile.text = buildFile.text.replace("lib library: 'hello'", "lib library: 'hello', linkage: 'static'")
        run "mainExecutable"

        then:
        skipped ":helloStaticLibrary"
        skipped mainCompileTask
        executedAndNotSkipped ":linkMainExecutable"
        executedAndNotSkipped ":mainExecutable"

        and:
        executable.assertHasChangedSince(snapshot)
    }

    @ToBeFixedForConfigurationCache
    def "relinks binary but does not recompile when linker option changed"() {
        given:
        run "mainExecutable"

        when:
        def executable = executable("build/exe/main/main")
        def snapshot = executable.snapshot()

        and:
        def linkerArgs = toolChain.isVisualCpp() ? "'/DEBUG'" : OperatingSystem.current().isMacOsX() ? "'-Xlinker', '-no_pie'" : "'-Xlinker', '-q'"
        linkerArgs = escapeString(linkerArgs)
        buildFile << """
        model {
            components {
                main {
                    binaries.all {
                        linker.args ${escapeString(linkerArgs)}
                    }
                }
            }
        }
"""

        run "mainExecutable"

        then:
        skipped libraryCompileTask
        skipped ":linkHelloSharedLibrary"
        skipped ":helloSharedLibrary"
        skipped mainCompileTask
        executedAndNotSkipped ":linkMainExecutable"
        executedAndNotSkipped ":mainExecutable"

        and:
        executable.assertExists()

        // Identical binaries produced on mingw and gcc cygwin
        if (!(toolChain.id in ["mingw", "gcccygwin"])) {
            executable.assertHasChangedSince(snapshot)
        }
    }

    @ToBeFixedForConfigurationCache
    def "cleans up stale object files when executable source file renamed"() {
        given:
        run "installMainExecutable"

        def oldObjFile = objectFileFor(sourceFile)
        def newObjFile = objectFileFor(sourceFile.getParentFile().file("changed_${sourceFile.name}"))
        assert oldObjFile.file
        assert !newObjFile.file

        final source = sourceFile

        when:
        rename(source)
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

    @ToBeFixedForConfigurationCache
    def "cleans up stale object files when library source file renamed"() {
        when:
        run "helloStaticLibrary"

        then:
        String objectFilesPath = "build/objs/hello/static/hello${sourceType}"
        def oldObjFile = objectFileFor(librarySourceFiles[0], objectFilesPath)
        def newObjFile = objectFileFor(librarySourceFiles[0].getParentFile().file("changed_${librarySourceFiles[0].name}"), objectFilesPath)
        assert oldObjFile.file
        assert !newObjFile.file

        try {
            assert staticLibrary("build/libs/hello/static/hello").listObjectFiles().contains(oldObjFile.name)
        } catch (UnsupportedOperationException ignored) {
            // Toolchain doesn't support this.
        }

        when:
        librarySourceFiles.each { rename(it) }
        run "helloStaticLibrary"

        then:
        executedAndNotSkipped libraryCompileTask.replace("Shared", "Static")
        executedAndNotSkipped ":createHelloStaticLibrary"
        executedAndNotSkipped ":helloStaticLibrary"

        and:
        !oldObjFile.file
        newObjFile.file

        and:
        try {
            assert staticLibrary("build/libs/hello/static/hello").listObjectFiles().contains(newObjFile.name)
            assert !staticLibrary("build/libs/hello/static/hello").listObjectFiles().contains(oldObjFile.name)
        } catch (UnsupportedOperationException ignored) {
            // Toolchain doesn't support this.
        }
    }

    @RequiresInstalledToolChain(GCC_COMPATIBLE)
    @ToBeFixedForConfigurationCache
    def "recompiles binary when imported header file changes"() {
        sourceFile.text = sourceFile.text.replaceFirst('#include "hello.h"', "#import \"hello.h\"")
        if (buildingCorCppWithGcc()) {
            buildFile << """
                model {
                    //support for #import on c/cpp is deprecated in gcc
                    binaries {
                        all { ${compilerTool}.args '-Wno-deprecated'; }
                    }
                }
            """
        }

        given:
        run "installMainExecutable"


        when:
        headerFile << """
            int unused();
"""
        run "mainExecutable"

        then:
        executedAndNotSkipped libraryCompileTask
        executedAndNotSkipped mainCompileTask

        if (objectiveCWithAslr) {
            executed ":linkHelloSharedLibrary", ":helloSharedLibrary"
            executed ":linkMainExecutable", ":mainExecutable"
        } else {
            skipped ":linkHelloSharedLibrary", ":helloSharedLibrary"
            skipped ":linkMainExecutable", ":mainExecutable"
        }
    }

    @RequiresInstalledToolChain(VISUALCPP)
    @ToBeFixedForConfigurationCache
    def "cleans up stale debug files when changing from debug to non-debug"() {

        given:
        buildFile << """
            model {
                binaries {
                    all { ${compilerTool}.args ${toolChain.meets(ToolChainRequirement.VISUALCPP_2013_OR_NEWER) ? "'/Zi', '/FS'" : "'/Zi'"}; linker.args '/DEBUG'; }
                }
            }
        """
        run "mainExecutable"

        def executable = executable("build/exe/main/main")
        executable.assertDebugFileExists()

        when:
        buildFile << """
            model {
                binaries {
                    all { ${compilerTool}.args.clear(); linker.args.clear(); }
                }
            }
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

    @Issue("GRADLE-3248")
    @ToBeFixedForConfigurationCache
    def "incremental compilation isn't considered up-to-date when compilation fails"() {
        expect:
        succeeds mainCompileTask

        when:
        app.brokenFile.writeToDir(file("src/main"))

        then:
        fails mainCompileTask

        when:
        // nothing changes

        expect:
        // build should still fail
        fails mainCompileTask
    }

    def buildingCorCppWithGcc() {
        return toolChain.meets(ToolChainRequirement.GCC) && (sourceType == "C" || sourceType == "Cpp")
    }

    static boolean rename(TestFile sourceFile) {
        final newFile = new File(sourceFile.getParentFile(), "changed_${sourceFile.name}")
        newFile << sourceFile.text
        sourceFile.delete()
    }
}
