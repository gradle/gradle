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
package org.gradle.ide.visualstudio

import org.gradle.ide.visualstudio.fixtures.AbstractVisualStudioIntegrationSpec
import org.gradle.ide.visualstudio.fixtures.MSBuildExecutor
import org.gradle.integtests.fixtures.SourceFile
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.internal.os.OperatingSystem
import org.gradle.nativeplatform.fixtures.RequiresInstalledToolChain
import org.gradle.nativeplatform.fixtures.app.CppHelloWorldApp
import org.gradle.nativeplatform.fixtures.app.ExeWithDiamondDependencyHelloWorldApp
import org.gradle.nativeplatform.fixtures.app.ExeWithLibraryUsingLibraryHelloWorldApp
import org.gradle.nativeplatform.fixtures.app.MixedLanguageHelloWorldApp
import org.gradle.nativeplatform.fixtures.app.WindowsResourceHelloWorldApp
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions
import spock.lang.Issue

import static org.gradle.nativeplatform.fixtures.ToolChainRequirement.VISUALCPP
import static org.gradle.nativeplatform.fixtures.ToolChainRequirement.WINDOWS_GCC
import static org.junit.Assume.assumeFalse

class VisualStudioSoftwareModelSingleProjectIntegrationTest extends AbstractVisualStudioIntegrationSpec {
    Set<String> projectConfigurations = ['win32Debug', 'win32Release', 'x64Debug', 'x64Release'] as Set

    def app = new CppHelloWorldApp()

    def setup() {
        assumeFalse(toolChain.meets(WINDOWS_GCC))

        settingsFile << """
            rootProject.name = 'app'
        """
        buildFile << """
            apply plugin: 'cpp'
            apply plugin: 'visual-studio'

            model {
                platforms {
                    win32 {
                        architecture "i386"
                    }
                    x64 {
                        architecture "amd64"
                    }
                }
                buildTypes {
                    debug
                    release
                }
                components {
                    all {
                        targetPlatform "win32"
                        targetPlatform "x64"
                    }
                }
            }
        """
    }

    @Issue("https://github.com/gradle/gradle/issues/790")
    @ToBeFixedForConfigurationCache
    def "creating visual studio multiple time gives the same result"() {
        given:
        app.writeSources(file("src/main"))
        buildFile << """
model {
    components {
        main(NativeExecutableSpec)
    }
}
"""
        when:
        run "visualStudio"
        def filtersFileContent = filtersFile("mainExe.vcxproj.filters").file.text
        def projectFileContent = projectFile("mainExe.vcxproj").projectFile.text
        def solutionFileContent = solutionFile("app.sln").file.text

        then:
        executedAndNotSkipped getExecutableTasks("main")

        when:
        run "visualStudio"

        then:
        skipped getExecutableTasks("main")

        and:
        filtersFile("mainExe.vcxproj.filters").file.text == filtersFileContent
        projectFile("mainExe.vcxproj").projectFile.text == projectFileContent
        solutionFile("app.sln").file.text == solutionFileContent
    }

    @ToBeFixedForConfigurationCache
    def "create visual studio solution for single executable"() {
        when:
        app.writeSources(file("src/main"))
        buildFile << """
model {
    components {
        main(NativeExecutableSpec) {
            binaries.all {
                cppCompiler.define "TEST"
                cppCompiler.define "foo", "bar"
            }
        }
    }
}
"""
        and:
        run "visualStudio"

        then:
        executedAndNotSkipped ":visualStudio"
        executedAndNotSkipped getExecutableTasks("main")

        and:
        final projectFile = projectFile("mainExe.vcxproj")
        projectFile.assertHasComponentSources(app, "src/main")
        projectFile.projectConfigurations.keySet() == projectConfigurations
        projectFile.projectConfigurations.values().each {
            assert it.macros == "TEST;foo=bar"
            assert it.includePath == filePath("src/main/headers")
            assert it.buildCommand.endsWith("gradle\" :installMain${it.name.capitalize()}Executable")
            assert it.outputFile == OperatingSystem.current().getExecutableName("build/install/main/${it.outputDir}/lib/main")
        }

        and:
        final mainSolution = solutionFile("app.sln")
        mainSolution.assertHasProjects("mainExe")
        mainSolution.assertReferencesProject(projectFile, projectConfigurations)
    }

    @Requires(IntegTestPreconditions.HasMsBuild)
    @ToBeFixedForConfigurationCache
    def "can build executable from visual studio"() {
        useMsbuildTool()
        def debugBinary = executable("build/exe/main/win32/debug/main")

        given:
        app.writeSources(file("src/main"))
        buildFile << """
            model {
                components {
                    main(NativeExecutableSpec)
                }
            }
        """

        and:
        succeeds "visualStudio"

        when:
        debugBinary.assertDoesNotExist()
        def resultDebug = msbuild
            .withSolution(solutionFile("app.sln"))
            .withConfiguration('win32Debug')
            .succeeds()

        then:
        resultDebug.size() == 1
        debugBinary.assertExists()
        installation('build/install/main/win32/debug').assertInstalled()
    }

    @Requires(IntegTestPreconditions.HasMsBuild)
    @ToBeFixedForConfigurationCache
    def "can build library from visual studio"() {
        useMsbuildTool()
        def debugBinaryLib = staticLibrary("build/libs/main/static/win32/debug/main")
        def debugBinaryDll = sharedLibrary("build/libs/main/shared/win32/debug/main")

        given:
        app.library.writeSources(file("src/main"))
        buildFile << """
model {
    components {
        main(NativeLibrarySpec)
    }
}
"""
        and:
        succeeds "visualStudio"

        when:
        debugBinaryLib.assertDoesNotExist()
        debugBinaryDll.assertDoesNotExist()
        def resultDebug = msbuild
            .withSolution(solutionFile("app.sln"))
            .withConfiguration('win32Debug')
            .succeeds()

        then:
        resultDebug.size() == 2
        debugBinaryLib.assertExists()
        debugBinaryDll.assertExists()
    }

    @Requires(IntegTestPreconditions.HasMsBuild)
    @ToBeFixedForConfigurationCache
    def "can detect build failure from visual studio"() {
        useMsbuildTool()

        given:
        app.writeSources(file("src/main"))
        file('src/main/cpp/broken.cpp') << 'Broken!'
        buildFile << """
            model {
                components {
                    main(NativeExecutableSpec)
                }
            }
        """

        and:
        succeeds "visualStudio"

        when:
        def resultDebug = msbuild
            .withSolution(solutionFile("app.sln"))
            .withConfiguration('win32Debug')
            .fails()

        then:
        resultDebug.assertHasErrorOutput("broken.cpp(1): error C2143: syntax error: missing ';' before '!'")
    }

    @Requires(IntegTestPreconditions.HasMsBuild)
    @ToBeFixedForConfigurationCache
    def "can clean from visual studio"() {
        useMsbuildTool()
        def debugBinary = executable('build/exe/main/win32/debug/main')

        given:
        app.writeSources(file("src/main"))
        buildFile << """
            model {
                components {
                    main(NativeExecutableSpec)
                }
            }
        """

        and:
        succeeds "visualStudio"

        when:
        debugBinary.assertDoesNotExist()
        msbuild
            .withSolution(solutionFile("app.sln"))
            .withConfiguration('win32Debug')
            .succeeds()

        then:
        debugBinary.exec().out == app.englishOutput

        when:
        msbuild
            .withSolution(solutionFile("app.sln"))
            .withConfiguration('win32Debug')
            .succeeds(MSBuildExecutor.MSBuildAction.CLEAN)

        then:
        file("build").assertDoesNotExist()
    }

    @ToBeFixedForConfigurationCache
    def "create visual studio solution for library"() {
        when:
        app.library.writeSources(file("src/main"))
        buildFile << """
model {
    components {
        main(NativeLibrarySpec)
    }
}
"""
        and:
        run "visualStudio"

        then:
        executedAndNotSkipped ":visualStudio"
        executedAndNotSkipped getLibraryTasks("main")

        and:
        def dllProjectFile = projectFile("mainDll.vcxproj")
        dllProjectFile.assertHasComponentSources(app.library, "src/main")
        dllProjectFile.projectConfigurations.keySet() == projectConfigurations
        dllProjectFile.projectConfigurations.values().each {
            assert it.includePath == filePath("src/main/headers")
            assert it.buildCommand.endsWith("gradle\" :main${it.name.capitalize()}SharedLibrary")
            assert it.outputFile == OperatingSystem.current().getSharedLibraryName("build/libs/main/shared/${it.outputDir}/main")
        }

        and:
        def libProjectFile = projectFile("mainLib.vcxproj")
        libProjectFile.assertHasComponentSources(app.library, "src/main")
        libProjectFile.projectConfigurations.keySet() == projectConfigurations
        libProjectFile.projectConfigurations.values().each {
            assert it.includePath == filePath("src/main/headers")
            assert it.buildCommand.endsWith("gradle\" :main${it.name.capitalize()}StaticLibrary")
            assert it.outputFile == OperatingSystem.current().getStaticLibraryName("build/libs/main/static/${it.outputDir}/main")
        }

        and:
        def mainSolution = solutionFile("app.sln")
        mainSolution.assertHasProjects("mainDll", "mainLib")
        mainSolution.assertReferencesProject(dllProjectFile, projectConfigurations)
    }

    @ToBeFixedForConfigurationCache
    def "create visual studio solution for build with an executable and library"() {
        when:
        app.executable.writeSources(file("src/main"))
        app.library.writeSources(file("src/hello"))
        buildFile << """
model {
    components {
        hello(NativeLibrarySpec)
        main(NativeExecutableSpec) {
            sources {
                cpp.lib library: 'hello', linkage: 'static'
            }
        }
    }
}
"""
        and:
        run "visualStudio"

        then:
        final exeProject = projectFile("mainExe.vcxproj")
        exeProject.assertHasComponentSources(app.executable, "src/main")
        exeProject.projectConfigurations.keySet() == projectConfigurations
        exeProject.projectConfigurations['win32Debug'].includePath == filePath("src/main/headers", "src/hello/headers")

        and:
        final libProject = projectFile("helloLib.vcxproj")
        libProject.assertHasComponentSources(app.library, "src/hello")
        libProject.projectConfigurations.keySet() == projectConfigurations
        libProject.projectConfigurations['win32Debug'].includePath == filePath("src/hello/headers")

        and:
        final dllProject = projectFile("helloDll.vcxproj")
        dllProject.assertHasComponentSources(app.library, "src/hello")
        dllProject.projectConfigurations.keySet() == projectConfigurations
        dllProject.projectConfigurations['win32Debug'].includePath == filePath("src/hello/headers")

        and:
        final mainSolution = solutionFile("app.sln")
        mainSolution.assertHasProjects("mainExe", "helloDll", "helloLib")
        mainSolution.assertReferencesProject(exeProject, projectConfigurations)
        mainSolution.assertReferencesProject(libProject, projectConfigurations)
    }

    @ToBeFixedForConfigurationCache
    def "create visual studio project for executable that targets multiple platforms with the same architecture"() {
        when:
        app.writeSources(file("src/main"))
        buildFile << """
model {
    platforms {
        otherWin32 {
            architecture "i386"
        }
    }
    components {
        main(NativeExecutableSpec) {
            targetPlatform "otherWin32"
        }
    }
}
"""
        and:
        run "visualStudio"

        then:
        final mainProjectFile = projectFile("mainExe.vcxproj")
        mainProjectFile.projectConfigurations.keySet() == ['win32Debug', 'otherWin32Debug', 'win32Release', 'otherWin32Release', 'x64Debug', 'x64Release'] as Set
    }

    @ToBeFixedForConfigurationCache
    def "create visual studio solution for executable that has diamond dependency"() {
        def testApp = new ExeWithDiamondDependencyHelloWorldApp()
        testApp.writeSources(file("src/main"), file("src/hello"), file("src/greetings"))

        buildFile << """
model {
    components {
        main(NativeExecutableSpec) {
            sources {
                cpp.lib library: "hello"
                cpp.lib library: "greetings", linkage: "static"
            }
        }
        hello(NativeLibrarySpec) {
            sources {
                cpp.lib library: "greetings", linkage: "static"
            }
        }
        greetings(NativeLibrarySpec)
    }
}
"""

        when:
        succeeds "visualStudio"

        then:
        final exeProject = projectFile("mainExe.vcxproj")
        final helloDllProject = projectFile("helloDll.vcxproj")
        final helloLibProject = projectFile("helloDll.vcxproj")
        final greetDllProject = projectFile("greetingsLib.vcxproj")
        final greetLibProject = projectFile("greetingsLib.vcxproj")
        final mainSolution = solutionFile("app.sln")

        and:
        mainSolution.assertHasProjects("mainExe", "helloDll", "helloLib", "greetingsDll", "greetingsLib")
        mainSolution.assertReferencesProject(exeProject, projectConfigurations)
        mainSolution.assertReferencesProject(helloDllProject, projectConfigurations)
        mainSolution.assertReferencesProject(helloLibProject, projectConfigurations)
        mainSolution.assertReferencesProject(greetDllProject, projectConfigurations)
        mainSolution.assertReferencesProject(greetLibProject, projectConfigurations)

        and:
        exeProject.assertHasComponentSources(testApp.executable, "src/main")
        exeProject.projectConfigurations.keySet() == projectConfigurations
        exeProject.projectConfigurations['win32Debug'].includePath == filePath("src/main/headers", "src/hello/headers", "src/greetings/headers")
    }

    @ToBeFixedForConfigurationCache
    def "generate visual studio solution for executable with mixed sources"() {
        given:
        def testApp = new MixedLanguageHelloWorldApp(toolChain)
        testApp.writeSources(file("src/main"))

        and:
        buildFile << """
apply plugin: 'c'
apply plugin: 'assembler'
model {
    components {
        main(NativeExecutableSpec)
    }
}
"""

        when:
        run "visualStudio"

        then:
        final projectFile = projectFile("mainExe.vcxproj")
        projectFile.assertHasComponentSources(testApp, "src/main")
        projectFile.projectConfigurations.keySet() == projectConfigurations
        with (projectFile.projectConfigurations['win32Debug']) {
            includePath == filePath("src/main/headers")
        }

        and:
        solutionFile("app.sln").assertHasProjects("mainExe")
    }

    @RequiresInstalledToolChain(VISUALCPP)
    @ToBeFixedForConfigurationCache
    def "generate visual studio solution for executable with windows resource files"() {
        given:
        def resourceApp = new WindowsResourceHelloWorldApp()
        resourceApp.writeSources(file("src/main"))

        and:
        buildFile << """
apply plugin: 'windows-resources'
model {
    components {
        main(NativeExecutableSpec)
    }
    binaries {
        all {
            rcCompiler.define "TEST"
            rcCompiler.define "foo", "bar"
        }
    }
}
"""

        when:
        run "visualStudio"

        then:
        final projectFile = projectFile("mainExe.vcxproj")
        final List<SourceFile> resources = resourceApp.resourceSources
        final List<SourceFile> sources = resourceApp.sourceFiles - resources
        assert projectFile.headerFiles == resourceApp.headerFiles*.withPath("src/main").sort()
        assert projectFile.sourceFiles == ['build.gradle'] + sources*.withPath("src/main").sort()
        assert projectFile.resourceFiles == resources*.withPath("src/main").sort()

        projectFile.projectConfigurations.keySet() == projectConfigurations
        with (projectFile.projectConfigurations['win32Debug']) {
            macros == "TEST;foo=bar"
            includePath == filePath("src/main/headers")
        }

        and:
        solutionFile("app.sln").assertHasProjects("mainExe")
    }

    @ToBeFixedForConfigurationCache
    def "builds solution for component with no source"() {
        given:
        buildFile << """
model {
    components {
        main(NativeExecutableSpec)
    }
}
"""

        when:
        run "visualStudio"

        then:
        final projectFile = projectFile("mainExe.vcxproj")
        projectFile.sourceFiles == ['build.gradle']
        projectFile.headerFiles == []
        projectFile.projectConfigurations.keySet() == projectConfigurations
        with (projectFile.projectConfigurations['win32Debug']) {
            includePath == filePath("src/main/headers")
        }

        and:
        solutionFile("app.sln").assertHasProjects("mainExe")
    }

    @ToBeFixedForConfigurationCache
    def "visual studio project includes headers co-located with sources"() {
        when:
        // Write headers so they sit with sources
        app.files.each {
            it.writeToFile(file("src/main/cpp/${it.name}"))
        }
        buildFile << """
model {
    components {
        main(NativeExecutableSpec) {
            sources {
                cpp.source.include "**/*.cpp"
            }
        }
    }
}
"""
        and:
        run "mainVisualStudio"

        then:
        executedAndNotSkipped getExecutableTasks("main")

        and:
        final projectFile = projectFile("mainExe.vcxproj")
        assert projectFile.sourceFiles == ['build.gradle'] + app.sourceFiles.collect({"src/main/cpp/${it.name}"}).sort()
        assert projectFile.headerFiles == app.headerFiles.collect({"src/main/cpp/${it.name}"}).sort()
    }

    @ToBeFixedForConfigurationCache
    def "visual studio solution with header-only library"() {
        given:
        def app = new CppHelloWorldApp()
        app.executable.writeSources(file("src/main"))

        app.library.headerFiles*.writeToDir(file("src/helloApi"))
        app.library.sourceFiles*.writeToDir(file("src/hello"))

        and:
        buildFile << """
model {
    components {
        main(NativeExecutableSpec) {
            sources {
                cpp.lib library: 'helloApi', linkage: 'api' // TODO:DAZ This should not be needed
                cpp.lib library: 'hello'
            }
        }
        helloApi(NativeLibrarySpec)
        hello(NativeLibrarySpec) {
            sources {
                cpp.lib library: 'helloApi', linkage: 'api'
            }
        }
    }
}
"""

        when:
        succeeds "visualStudio"

        then:
        final mainSolution = solutionFile("app.sln")
        mainSolution.assertHasProjects("mainExe", "helloDll", "helloLib", "helloApiDll", "helloApiLib")

        and:
        final mainExeProject = projectFile("mainExe.vcxproj")
        with (mainExeProject.projectConfigurations['win32Debug']) {
            includePath == filePath("src/main/headers", "src/helloApi/headers", "src/hello/headers")
        }

        and:
        final helloDllProject = projectFile("helloDll.vcxproj")
        with (helloDllProject.projectConfigurations['win32Debug']) {
            includePath == filePath("src/hello/headers", "src/helloApi/headers")
        }

        and:
        final helloLibProject = projectFile("helloLib.vcxproj")
        with (helloLibProject.projectConfigurations['win32Debug']) {
            includePath == filePath("src/hello/headers", "src/helloApi/headers")
        }

        and:
        final helloApiDllProject = projectFile("helloApiDll.vcxproj")
        with (helloApiDllProject.projectConfigurations['win32Debug']) {
            includePath == filePath("src/helloApi/headers")
        }

        and:
        final helloApiLibProject = projectFile("helloApiLib.vcxproj")
        with (helloApiLibProject.projectConfigurations['win32Debug']) {
            includePath == filePath("src/helloApi/headers")
        }
    }

    @ToBeFixedForConfigurationCache
    def "create visual studio solution for executable with variant conditional sources"() {
        when:
        app.writeSources(file("src/win32"))
        app.alternate.writeSources(file("src/x64"))
        buildFile << """
model {
    components {
        main(NativeExecutableSpec) {
            binaries.all { binary ->
                def platformName = binary.targetPlatform.name
                sources {
                    platformSources(CppSourceSet) {
                        source.srcDir "src/\$platformName/cpp"
                        exportedHeaders.srcDir "src/\$platformName/headers"
                    }
                }
            }
        }
    }
}

"""
        and:
        run "visualStudio"

        then:
        final projectFile = projectFile("mainExe.vcxproj")
        projectFile.assertHasComponentSources(app, "src/win32", app.alternate, "src/x64")
        projectFile.projectConfigurations.keySet() == projectConfigurations

        and:
        final mainSolution = solutionFile("app.sln")
        mainSolution.assertHasProjects("mainExe")
        mainSolution.assertReferencesProject(projectFile, projectConfigurations)
    }

    @ToBeFixedForConfigurationCache
    def "visual studio solution with pre-built library"() {
        given:
        app.writeSources(file("src/main"))
        buildFile << """
model {
    repositories {
        libs(PrebuiltLibraries) {
            test {
                headers.srcDir "libs/test/include"
            }
        }
    }
    components {
        main(NativeExecutableSpec) {
            sources {
                cpp.lib library: 'test', linkage: 'api'
            }
        }
    }
}
"""

        when:
        run "visualStudio"

        then:
        executedAndNotSkipped getExecutableTasks("main")
        and:

        then:
        final mainSolution = solutionFile("app.sln")
        mainSolution.assertHasProjects("mainExe")

        and:
        final mainExeProject = projectFile("mainExe.vcxproj")
        with (mainExeProject.projectConfigurations['win32Debug']) {
            includePath == filePath("src/main/headers", "libs/test/include")
        }
    }

    @ToBeFixedForConfigurationCache
    def "visual studio solution for executable that depends on library using precompiled header"() {
        when:
        app = new CppHelloWorldApp()
        app.executable.writeSources(file("src/main"))
        app.library.writeSources(file("src/hello"))
        buildFile << """
            model {
                components {
                    hello(NativeLibrarySpec) {
                        sources {
                            cpp.preCompiledHeader "pch.h"
                        }
                    }
                    main(NativeExecutableSpec) {
                        sources {
                            cpp.lib library: 'hello'
                        }
                    }
                }
            }
        """
        and:
        run "visualStudio"

        then:
        final exeProject = projectFile("mainExe.vcxproj")
        exeProject.assertHasComponentSources(app.executable, "src/main")
        exeProject.projectConfigurations.keySet() == projectConfigurations
        exeProject.projectConfigurations['win32Debug'].includePath == filePath("src/main/headers", "src/hello/headers")

        and:
        final dllProject = projectFile("helloDll.vcxproj")
        dllProject.assertHasComponentSources(app.library, "src/hello")
        dllProject.projectConfigurations.keySet() == projectConfigurations
        dllProject.projectConfigurations['win32Debug'].includePath == filePath("src/hello/headers")

        and:
        final libProject = projectFile("helloLib.vcxproj")
        libProject.assertHasComponentSources(app.library, "src/hello")
        libProject.projectConfigurations.keySet() == projectConfigurations
        libProject.projectConfigurations['win32Debug'].includePath == filePath("src/hello/headers")

        and:
        final mainSolution = solutionFile("app.sln")
        mainSolution.assertHasProjects("mainExe", "helloDll", "helloLib")
        mainSolution.assertReferencesProject(exeProject, projectConfigurations)
        mainSolution.assertReferencesProject(dllProject, projectConfigurations)
    }

    @ToBeFixedForConfigurationCache
    def "visual studio solution for component graph with library dependency cycle"() {
        given:
        def app = new ExeWithLibraryUsingLibraryHelloWorldApp()
        app.executable.writeSources(file("src/main"))
        app.library.writeSources(file("src/hello"))
        app.greetingsHeader.writeToDir(file("src/hello"))
        app.greetingsSources*.writeToDir(file("src/greetings"))

        and:
        buildFile << """
model {
    components {
        main(NativeExecutableSpec) {
            sources {
                cpp.lib library: 'hello'
            }
        }
        hello(NativeLibrarySpec) {
            sources {
                cpp.lib library: 'greetings', linkage: 'static'
            }
        }
        greetings(NativeLibrarySpec) {
            sources {
                cpp.lib library: 'hello', linkage: 'api'
            }
        }
    }
}
"""

        when:
        succeeds "visualStudio"

        then:
        final mainSolution = solutionFile("app.sln")
        mainSolution.assertHasProjects("mainExe", "helloDll", "helloLib", "greetingsLib", "greetingsDll")

        and:
        final mainExeProject = projectFile("mainExe.vcxproj")
        with (mainExeProject.projectConfigurations['win32Debug']) {
            includePath == filePath("src/main/headers", "src/hello/headers")
        }

        and:
        final helloDllProject = projectFile("helloDll.vcxproj")
        with (helloDllProject.projectConfigurations['win32Debug']) {
            includePath == filePath( "src/hello/headers", "src/greetings/headers")
        }

        and:
        final helloLibProject = projectFile("helloLib.vcxproj")
        with (helloLibProject.projectConfigurations['win32Debug']) {
            includePath == filePath( "src/hello/headers", "src/greetings/headers")
        }

        and:
        final greetingsDllProject = projectFile("greetingsDll.vcxproj")
        with (greetingsDllProject.projectConfigurations['win32Debug']) {
            includePath == filePath("src/greetings/headers", "src/hello/headers")
        }

        and:
        final greetingsLibProject = projectFile("greetingsLib.vcxproj")
        with (greetingsLibProject.projectConfigurations['win32Debug']) {
            includePath == filePath("src/greetings/headers", "src/hello/headers")
        }
    }

    @ToBeFixedForConfigurationCache
    def "create visual studio solution where referenced projects have different configurations"() {
        when:
        app.executable.writeSources(file("src/main"))
        app.library.writeSources(file("src/hello"))
        buildFile << """
model {
    components {
        hello(NativeLibrarySpec)
        main(NativeExecutableSpec) {
            targetBuildTypes "release"
            sources {
                cpp.lib library: 'hello'
            }
        }
    }
}
"""
        and:
        run "visualStudio"

        then:
        final exeProject = projectFile("mainExe.vcxproj")
        exeProject.assertHasComponentSources(app.executable, "src/main")
        exeProject.projectConfigurations.keySet() == ['win32', 'x64'] as Set
        exeProject.projectConfigurations['win32'].includePath == filePath("src/main/headers", "src/hello/headers")

        and:
        final dllProject = projectFile("helloDll.vcxproj")
        dllProject.assertHasComponentSources(app.library, "src/hello")
        dllProject.projectConfigurations.keySet() == projectConfigurations
        dllProject.projectConfigurations['win32Debug'].includePath == filePath("src/hello/headers")

        and:
        final libProject = projectFile("helloLib.vcxproj")
        libProject.assertHasComponentSources(app.library, "src/hello")
        libProject.projectConfigurations.keySet() == projectConfigurations
        libProject.projectConfigurations['win32Debug'].includePath == filePath("src/hello/headers")

        and:
        final mainSolution = solutionFile("app.sln")
        mainSolution.assertHasProjects("mainExe", "helloDll", "helloLib")
        mainSolution.assertReferencesProject(exeProject, ['win32':'win32', 'x64':'x64', 'win32Debug':'x64', 'win32Release':'x64', 'x64Debug':'x64', 'x64Release':'x64'])
        mainSolution.assertReferencesProject(dllProject, ['win32Debug':'win32Debug', 'win32Release':'win32Release', 'x64Debug':'x64Debug', 'x64Release':'x64Release', 'win32':'x64Release', 'x64':'x64Release'])
        mainSolution.assertReferencesProject(libProject, ['win32Debug':'win32Debug', 'win32Release':'win32Release', 'x64Debug':'x64Debug', 'x64Release':'x64Release', 'win32':'x64Release', 'x64':'x64Release'])
    }

    @ToBeFixedForConfigurationCache
    def "only create visual studio projects for buildable binaries"() {
        when:
        app.library.writeSources(file("src/both"))
        app.library.writeSources(file("src/staticOnly"))
        settingsFile << """
            rootProject.name = 'app'
        """
        buildFile << """
            model {
                components {
                    both(NativeLibrarySpec) {
                        binaries.all {
                            if (buildType == buildTypes.debug) {
                                buildable = false
                            }
                        }
                    }
                    staticOnly(NativeLibrarySpec) {
                        binaries.withType(SharedLibraryBinarySpec) {
                            buildable = false
                        }
                    }
                    none(NativeLibrarySpec) {
                        binaries.all {
                            buildable = false
                        }
                    }
                }
            }
        """
        and:
        run "visualStudio"

        then:
        executedAndNotSkipped getLibraryTasks("both")
        executedAndNotSkipped getStaticLibraryTasks("staticOnly")
        notExecuted getSharedLibraryTasks("staticOnly")
        notExecuted getLibraryTasks("none")

        and:
        final bothLibProjectFile = projectFile("bothLib.vcxproj")
        bothLibProjectFile.assertHasComponentSources(app.library, "src/both")
        bothLibProjectFile.projectConfigurations.keySet() == ['win32Release', 'x64Release'] as Set

        and:
        final bothDllProjectFile = projectFile("bothDll.vcxproj")
        bothDllProjectFile.assertHasComponentSources(app.library, "src/both")
        bothDllProjectFile.projectConfigurations.keySet() == ['win32Release', 'x64Release'] as Set

        and:
        final staticOnlyLibProjectFile = projectFile("staticOnlyLib.vcxproj")
        staticOnlyLibProjectFile.assertHasComponentSources(app.library, "src/staticOnly")
        staticOnlyLibProjectFile.projectConfigurations.keySet() == projectConfigurations

        and:
        !file("staticOnlyDll.vcxproj").exists()

        and:
        !file("noneDll.vcxproj").exists()
        !file("noneLib.vcxproj").exists()

        and:
        file("app.sln").assertExists()
        solutionFile("app.sln").assertHasProjects("bothDll", "bothLib", "staticOnlyLib")
    }

    @ToBeFixedForConfigurationCache
    def "can detect the language standard for Visual Studio IntelliSense [#expectedLanguageStandard] #uniqueIndex"() {
        given:
        app.writeSources(file("src/main"))
        buildFile << """
model {
    components {
        main(NativeExecutableSpec) {
            binaries.all {
                cppCompiler.args "${compilerFlag}"
            }
        }
    }
}
"""
        when:
        succeeds "visualStudio"

        then:
        executedAndNotSkipped ":visualStudio"
        executedAndNotSkipped getExecutableTasks("main")

        and:
        final projectFile = projectFile("mainExe.vcxproj")
        projectFile.projectConfigurations.keySet() == projectConfigurations
        projectFile.projectConfigurations.values().each {
            assert it.languageStandard == expectedLanguageStandard
        }

        where:
        // uniqueIndex: https://github.com/gradle/gradle/issues/8787
        compilerFlag     | expectedLanguageStandard | uniqueIndex
        '/std:c++14'     | 'stdcpp14'               | 1
        '-std:c++14'     | 'stdcpp14'               | 2
        '/std:c++17'     | 'stdcpp17'               | 3
        '-std:c++17'     | 'stdcpp17'               | 4
        '/std:c++latest' | 'stdcpplatest'           | 5
        '-std:c++latest' | 'stdcpplatest'           | 6
    }

    @ToBeFixedForConfigurationCache
    def "can detect different language standard per component for Visual Studio IntelliSense"() {
        given:
        app.writeSources(file("src/main"))
        buildFile << """
model {
    components {
        main(NativeExecutableSpec) {
            binaries.all {
                if (it.buildType.name == 'debug') {
                    cppCompiler.args "/std:c++14"
                } else {
                    cppCompiler.args "/std:c++17"
                }
            }
        }
    }
}
"""
        when:
        succeeds "visualStudio"

        then:
        executedAndNotSkipped ":visualStudio"
        executedAndNotSkipped getExecutableTasks("main")

        and:
        final projectFile = projectFile("mainExe.vcxproj")
        projectFile.projectConfigurations.keySet() == projectConfigurations
        projectFile.projectConfigurations.values().each {
            if (it.name.endsWith("Debug")) {
                assert it.languageStandard == "stdcpp14"
            } else {
                assert it.languageStandard == "stdcpp17"
            }
        }
    }

    @ToBeFixedForConfigurationCache
    def "does not configure language standard when compiler flag is absent for Visual Studio IntelliSense"() {
        given:
        app.writeSources(file("src/main"))
        buildFile << """
model {
    components {
        main(NativeExecutableSpec)
    }
}
"""
        when:
        succeeds "visualStudio"

        then:
        executedAndNotSkipped ":visualStudio"
        executedAndNotSkipped getExecutableTasks("main")

        and:
        final projectFile = projectFile("mainExe.vcxproj")
        projectFile.projectConfigurations.keySet() == projectConfigurations
        projectFile.projectConfigurations.values().each {
            assert it.languageStandard == null
        }
    }

    private String[] getLibraryTasks(String libraryName) {
        return getStaticLibraryTasks(libraryName) + getSharedLibraryTasks(libraryName)
    }

    private String[] getStaticLibraryTasks(String libraryName) {
        return [":${libraryName}LibVisualStudioProject", ":${libraryName}LibVisualStudioFilters"]
    }

    private String[] getSharedLibraryTasks(String libraryName) {
        return [":${libraryName}DllVisualStudioProject", ":${libraryName}DllVisualStudioFilters"]
    }

    private String[] getExecutableTasks(String exeName) {
        return [":${exeName}ExeVisualStudioProject", ":${exeName}ExeVisualStudioFilters"]
    }
}
