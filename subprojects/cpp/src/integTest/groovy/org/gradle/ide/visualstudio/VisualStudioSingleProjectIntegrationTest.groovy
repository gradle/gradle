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
import org.gradle.ide.visualstudio.fixtures.ProjectFile
import org.gradle.ide.visualstudio.fixtures.SolutionFile
import org.gradle.internal.os.OperatingSystem
import org.gradle.nativebinaries.language.cpp.fixtures.AbstractInstalledToolChainIntegrationSpec
import org.gradle.nativebinaries.language.cpp.fixtures.RequiresInstalledToolChain
import org.gradle.nativebinaries.language.cpp.fixtures.app.*

import static org.gradle.nativebinaries.language.cpp.fixtures.ToolChainRequirement.VisualCpp

class VisualStudioSingleProjectIntegrationTest extends AbstractInstalledToolChainIntegrationSpec {
    private final Set<String> projectConfigurations = ['win32Debug', 'win32Release', 'x64Debug', 'x64Release'] as Set

    def app = new CppHelloWorldApp()

    def setup() {
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
    }

"""
    }

    def "create visual studio solution for single executable"() {
        when:
        app.writeSources(file("src/main"))
        buildFile << """
    executables {
        main {}
    }
    binaries.all {
        cppCompiler.define "TEST"
        cppCompiler.define "foo", "bar"
    }
"""
        and:
        run "mainVisualStudio"

        then:
        executedAndNotSkipped ":mainExeVisualStudio"

        and:
        final projectFile = projectFile("visualStudio/mainExe.vcxproj")
        projectFile.sourceFiles == allFiles("src/main/cpp")
        projectFile.headerFiles == allFiles("src/main/headers")
        projectFile.projectConfigurations.keySet() == projectConfigurations
        projectFile.projectConfigurations.values().each {
            assert it.macros == "TEST;foo=bar"
            assert it.includePath == filePath("src/main/headers")
            assert it.buildCommand == "gradle -p \"..\" :install${it.name.capitalize()}MainExecutable"
            assert it.outputFile == OperatingSystem.current().getExecutableName("../build/install/mainExecutable/${it.name}/lib/main")
        }

        and:
        final mainSolution = solutionFile("visualStudio/mainExe.sln")
        mainSolution.assertHasProjects("mainExe")
        mainSolution.assertReferencesProject(projectFile, projectConfigurations)
    }

    def "create visual studio solution for single shared library"() {
        when:
        app.library.writeSources(file("src/main"))
        buildFile << """
    libraries {
        main {}
    }
"""
        and:
        run "mainVisualStudio"

        then:
        executedAndNotSkipped ":mainDllVisualStudio"

        and:
        final projectFile = projectFile("visualStudio/mainDll.vcxproj")
        projectFile.sourceFiles == allFiles("src/main/cpp")
        projectFile.headerFiles == allFiles("src/main/headers")
        projectFile.projectConfigurations.keySet() == projectConfigurations
        projectFile.projectConfigurations.values().each {
            assert it.includePath == filePath("src/main/headers")
            assert it.buildCommand == "gradle -p \"..\" :${it.name}MainSharedLibrary"
            assert it.outputFile == OperatingSystem.current().getSharedLibraryName("../build/binaries/mainSharedLibrary/${it.name}/main")
        }

        and:
        final mainSolution = solutionFile("visualStudio/mainDll.sln")
        mainSolution.assertHasProjects("mainDll")
        mainSolution.assertReferencesProject(projectFile, projectConfigurations)
    }

    def "create visual studio solution for defined static library"() {
        when:
        app.library.writeSources(file("src/main"))
        buildFile << """
    libraries {
        main {}
    }
    binaries.withType(SharedLibraryBinary) {
        buildable = false
    }
"""
        and:
        run "mainVisualStudio"

        then:
        executedAndNotSkipped ":mainLibVisualStudio"

        and:
        final projectFile = projectFile("visualStudio/mainLib.vcxproj")
        projectFile.sourceFiles == allFiles("src/main/cpp")
        projectFile.headerFiles == allFiles("src/main/headers")
        projectFile.projectConfigurations.keySet() == projectConfigurations
        projectFile.projectConfigurations['win32Debug'].includePath == filePath("src/main/headers")

        and:
        final mainSolution = solutionFile("visualStudio/mainLib.sln")
        mainSolution.assertHasProjects("mainLib")
        mainSolution.assertReferencesProject(projectFile, projectConfigurations)
    }

    def "create visual studio solution for executable that depends on static library"() {
        when:
        app.executable.writeSources(file("src/main"))
        app.library.writeSources(file("src/hello"))
        buildFile << """
    libraries {
        hello {}
    }
    executables {
        main {}
    }
    sources.main.cpp.lib libraries.hello.static
"""
        and:
        run "mainVisualStudio"

        then:
        final exeProject = projectFile("visualStudio/mainExe.vcxproj")
        exeProject.sourceFiles == allFiles("src/main/cpp")
        exeProject.headerFiles.isEmpty()
        exeProject.projectConfigurations.keySet() == projectConfigurations
        exeProject.projectConfigurations['win32Debug'].includePath == filePath("src/main/headers", "src/hello/headers")

        and:
        final libProject = projectFile("visualStudio/helloLib.vcxproj")
        libProject.sourceFiles == allFiles("src/hello/cpp")
        libProject.headerFiles == allFiles("src/hello/headers")
        libProject.projectConfigurations.keySet() == projectConfigurations
        libProject.projectConfigurations['win32Debug'].includePath == filePath("src/hello/headers")

        and:
        final mainSolution = solutionFile("visualStudio/mainExe.sln")
        mainSolution.assertHasProjects("mainExe", "helloLib")
        mainSolution.assertReferencesProject(exeProject, projectConfigurations)
        mainSolution.assertReferencesProject(libProject, projectConfigurations)
    }

    def "create visual studio solution for executable that depends on shared library"() {
        when:
        app.executable.writeSources(file("src/main"))
        app.library.writeSources(file("src/hello"))
        buildFile << """
    libraries {
        hello {}
    }
    executables {
        main {}
    }
    sources.main.cpp.lib libraries.hello
"""
        and:
        run "mainVisualStudio"

        then:
        final exeProject = projectFile("visualStudio/mainExe.vcxproj")
        exeProject.sourceFiles == allFiles("src/main/cpp")
        exeProject.headerFiles.isEmpty()
        exeProject.projectConfigurations.keySet() == projectConfigurations
        exeProject.projectConfigurations['win32Debug'].includePath == filePath("src/main/headers", "src/hello/headers")

        and:
        final dllProject = projectFile("visualStudio/helloDll.vcxproj")
        dllProject.sourceFiles == allFiles("src/hello/cpp")
        dllProject.headerFiles == allFiles("src/hello/headers")
        dllProject.projectConfigurations.keySet() == projectConfigurations
        dllProject.projectConfigurations['win32Debug'].includePath == filePath("src/hello/headers")

        and:
        final mainSolution = solutionFile("visualStudio/mainExe.sln")
        mainSolution.assertHasProjects("mainExe", "helloDll")
        mainSolution.assertReferencesProject(exeProject, projectConfigurations)
        mainSolution.assertReferencesProject(dllProject, projectConfigurations)
    }

    def "create visual studio solution for executable that depends on library that depends on another library"() {
        given:
        def testApp = new ExeWithLibraryUsingLibraryHelloWorldApp()
        testApp.writeSources(file("src/main"), file("src/hello"), file("src/greetings"))

        buildFile << """
            apply plugin: "cpp"
            libraries {
                greetings {}
                hello {}
            }
            executables {
                main {}
            }
            sources {
                hello.cpp.lib libraries.greetings.static
                main.cpp.lib libraries.hello
            }
        """
        when:
        run "mainVisualStudio"

        then:
        final exeProject = projectFile("visualStudio/mainExe.vcxproj")
        exeProject.sourceFiles == allFiles("src/main/cpp")
        exeProject.headerFiles.isEmpty()
        exeProject.projectConfigurations.keySet() == projectConfigurations
        exeProject.projectConfigurations['win32Debug'].includePath == filePath("src/main/headers", "src/hello/headers")

        and:
        final helloDllProject = projectFile("visualStudio/helloDll.vcxproj")
        helloDllProject.sourceFiles == allFiles("src/hello/cpp")
        helloDllProject.headerFiles == allFiles("src/hello/headers")
        helloDllProject.projectConfigurations.keySet() == projectConfigurations
        helloDllProject.projectConfigurations['win32Debug'].includePath == filePath("src/hello/headers", "src/greetings/headers")

        and:
        final greetingsLibProject = projectFile("visualStudio/greetingsLib.vcxproj")
        greetingsLibProject.sourceFiles == allFiles("src/greetings/cpp")
        greetingsLibProject.headerFiles == allFiles("src/greetings/headers")
        greetingsLibProject.projectConfigurations.keySet() == projectConfigurations
        greetingsLibProject.projectConfigurations['win32Debug'].includePath == filePath("src/greetings/headers")

        and:
        final mainSolution = solutionFile("visualStudio/mainExe.sln")
        mainSolution.assertHasProjects("mainExe", "helloDll", "greetingsLib")
        mainSolution.assertReferencesProject(exeProject, projectConfigurations)
        mainSolution.assertReferencesProject(helloDllProject, projectConfigurations)
        mainSolution.assertReferencesProject(greetingsLibProject, projectConfigurations)
    }

    def "create visual studio solutions for 2 executables that depend on different linkages of the same library"() {
        when:
        app.executable.writeSources(file("src/main"))
        app.library.writeSources(file("src/hello"))
        buildFile << """
    libraries {
        hello {}
    }
    executables {
        main {}
        mainStatic {}
    }
    sources.main.cpp.lib libraries.hello
    sources.mainStatic.cpp.source.srcDirs "src/main/cpp"
    sources.mainStatic.cpp.lib libraries.hello.static
"""
        and:
        run "mainVisualStudio", "mainStaticVisualStudio"

        then:
        solutionFile("visualStudio/mainExe.sln").assertHasProjects("mainExe", "helloDll")
        solutionFile("visualStudio/mainStaticExe.sln").assertHasProjects("mainStaticExe", "helloLib")

        and:
        final exeProject = projectFile("visualStudio/mainExe.vcxproj")
        final staticExeProject = projectFile("visualStudio/mainStaticExe.vcxproj")
        exeProject.sourceFiles == staticExeProject.sourceFiles
        exeProject.headerFiles == []
        staticExeProject.headerFiles == []

        and:
        final dllProject = projectFile("visualStudio/helloDll.vcxproj")
        final libProject = projectFile("visualStudio/helloLib.vcxproj")
        dllProject.sourceFiles == libProject.sourceFiles
        dllProject.headerFiles == libProject.headerFiles
    }

    def "create visual studio solutions for 2 executables that depend on different build types of the same library"() {
        when:
        app.executable.writeSources(file("src/main"))
        app.library.writeSources(file("src/hello"))
        buildFile << """
    libraries {
        hello {}
    }
    executables {
        main {}
        mainRelease {
            targetBuildTypes "release"
        }
    }
    sources.main.cpp.lib libraries.hello

    sources.mainRelease.cpp.source.srcDirs "src/main/cpp"
    sources.mainRelease.cpp.lib libraries.hello
"""
        and:
        run "mainVisualStudio", "mainReleaseVisualStudio"

        then:
        solutionFile("visualStudio/mainExe.sln").assertHasProjects("mainExe", "helloDll")
        solutionFile("visualStudio/mainReleaseExe.sln").assertHasProjects("mainReleaseExe", "helloDll")

        and:
        final helloProjectFile = projectFile("visualStudio/helloDll.vcxproj")
        helloProjectFile.projectConfigurations.keySet() == projectConfigurations
        final mainProjectFile = projectFile("visualStudio/mainExe.vcxproj")
        mainProjectFile.projectConfigurations.keySet() == projectConfigurations
        final mainReleaseProjectFile = projectFile("visualStudio/mainReleaseExe.vcxproj")
        mainReleaseProjectFile.projectConfigurations.keySet() == ['win32Release', 'x64Release'] as Set

        and:
        final mainSolution = solutionFile("visualStudio/mainExe.sln")
        mainSolution.assertReferencesProject(helloProjectFile, projectConfigurations)
        final mainReleaseSolution = solutionFile("visualStudio/mainReleaseExe.sln")
        mainReleaseSolution.assertReferencesProject(helloProjectFile, ['win32Release', 'x64Release'])
    }

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
    }
    executables {
        main {
            targetBuildTypes "debug"
            targetPlatforms "win32", "otherWin32"
        }
    }
"""
        and:
        run "mainVisualStudio"

        then:
        final mainProjectFile = projectFile("visualStudio/mainExe.vcxproj")
        mainProjectFile.projectConfigurations.keySet() == ['win32Debug', 'otherWin32Debug'] as Set
    }

    def "create visual studio solution for executable that has diamond dependency"() {
        def testApp = new ExeWithDiamondDependencyHelloWorldApp()
        testApp.writeSources(file("src/main"), file("src/hello"), file("src/greetings"))

        buildFile << """
            executables {
                main {}
            }
            libraries {
                hello {}
                greetings {}
            }
            sources.main.cpp.lib libraries.hello.shared
            sources.main.cpp.lib libraries.greetings.static
            sources.hello.cpp.lib libraries.greetings.static
        """

        when:
        succeeds "mainVisualStudio"

        then:
        final exeProject = projectFile("visualStudio/mainExe.vcxproj")
        final helloProject = projectFile("visualStudio/helloDll.vcxproj")
        final greetProject = projectFile("visualStudio/greetingsLib.vcxproj")
        final mainSolution = solutionFile("visualStudio/mainExe.sln")

        and:
        mainSolution.assertHasProjects("mainExe", "helloDll", "greetingsLib")
        mainSolution.assertReferencesProject(exeProject, projectConfigurations)
        mainSolution.assertReferencesProject(helloProject, projectConfigurations)
        mainSolution.assertReferencesProject(greetProject, projectConfigurations)

        and:
        exeProject.sourceFiles == allFiles("src/main/cpp")
        exeProject.headerFiles.isEmpty()
        exeProject.projectConfigurations.keySet() == projectConfigurations
        exeProject.projectConfigurations['win32Debug'].includePath == filePath("src/main/headers", "src/hello/headers", "src/greetings/headers")
    }

    def "create visual studio solution for executable that depends on both static and shared linkage of library"() {
        given:
        def app = new ExeWithDiamondDependencyHelloWorldApp()
        app.writeSources(file("src/main"), file("src/hello"), file("src/greetings"))

        and:
        buildFile << """
            executables {
                main {}
            }
            libraries {
                hello {}
                greetings {}
            }
            sources.main.cpp.lib libraries.hello.shared
            sources.main.cpp.lib libraries.greetings.shared
            sources.hello.cpp.lib libraries.greetings.static
        """

        when:
        succeeds "mainVisualStudio"

        then:
        final exeProject = projectFile("visualStudio/mainExe.vcxproj")
        final helloProject = projectFile("visualStudio/helloDll.vcxproj")
        final greetDllProject = projectFile("visualStudio/greetingsDll.vcxproj")
        final greetLibProject = projectFile("visualStudio/greetingsLib.vcxproj")
        final mainSolution = solutionFile("visualStudio/mainExe.sln")

        and:
        mainSolution.assertHasProjects("mainExe", "helloDll", "greetingsLib", "greetingsDll")
        mainSolution.assertReferencesProject(exeProject, projectConfigurations)
        mainSolution.assertReferencesProject(helloProject, projectConfigurations)
        mainSolution.assertReferencesProject(greetDllProject, projectConfigurations)
        mainSolution.assertReferencesProject(greetLibProject, projectConfigurations)

        and:
        exeProject.sourceFiles == allFiles("src/main/cpp")
        exeProject.projectConfigurations.keySet() == projectConfigurations
        exeProject.projectConfigurations['win32Debug'].includePath == filePath("src/main/headers", "src/hello/headers", "src/greetings/headers")

        and:
        helloProject.sourceFiles == allFiles("src/hello/cpp")
        helloProject.projectConfigurations.keySet() == projectConfigurations
        helloProject.projectConfigurations['win32Debug'].includePath == filePath("src/hello/headers", "src/greetings/headers")
    }

    def "generate visual studio solution for executable with mixed sources"() {
        given:
        def testApp = new MixedLanguageHelloWorldApp(toolChain)
        testApp.writeSources(file("src/main"))

        and:
        buildFile << """
            apply plugin: 'c'
            apply plugin: 'assembler'
            executables {
                main {}
            }
        """

        when:
        run "mainVisualStudio"

        then:
        final projectFile = projectFile("visualStudio/mainExe.vcxproj")
        projectFile.sourceFiles == allFiles("src/main/asm", "src/main/c", "src/main/cpp")
        projectFile.headerFiles == allFiles("src/main/headers")
        projectFile.projectConfigurations.keySet() == projectConfigurations
        with (projectFile.projectConfigurations['win32Debug']) {
            includePath == filePath("src/main/headers")
        }

        and:
        solutionFile("visualStudio/mainExe.sln").assertHasProjects("mainExe")
    }

    @RequiresInstalledToolChain(VisualCpp)
    def "generate visual studio solution for executable with windows resource files"() {
        given:
        def resourceApp = new WindowsResourceHelloWorldApp()
        resourceApp.writeSources(file("src/main"))

        and:
        buildFile << """
            apply plugin: 'windows-resources'
            executables {
                main {}
            }
            binaries.all {
                rcCompiler.define "TEST"
                rcCompiler.define "foo", "bar"
            }
        """

        when:
        run "mainVisualStudio"

        then:
        final projectFile = projectFile("visualStudio/mainExe.vcxproj")
        projectFile.sourceFiles == allFiles("src/main/cpp")
        projectFile.resourceFiles == allFiles("src/main/rc")
        projectFile.headerFiles == allFiles("src/main/headers")
        projectFile.projectConfigurations.keySet() == projectConfigurations
        with (projectFile.projectConfigurations['win32Debug']) {
            macros == "TEST;foo=bar"
            includePath == filePath("src/main/headers")
        }

        and:
        solutionFile("visualStudio/mainExe.sln").assertHasProjects("mainExe")
    }

    def "builds solution for component with no source"() {
        given:
        buildFile << """
            executables {
                main {}
            }
        """

        when:
        run "mainVisualStudio"

        then:
        final projectFile = projectFile("visualStudio/mainExe.vcxproj")
        projectFile.sourceFiles == []
        projectFile.headerFiles == []
        projectFile.projectConfigurations.keySet() == projectConfigurations
        with (projectFile.projectConfigurations['win32Debug']) {
            includePath == filePath("src/main/headers")
        }

        and:
        solutionFile("visualStudio/mainExe.sln").assertHasProjects("mainExe")
    }

    def "visual studio solution with header-only library"() {
        given:
        def app = new CppHelloWorldApp()
        app.executable.writeSources(file("src/main"))

        app.library.headerFiles*.writeToDir(file("src/helloApi"))
        app.library.sourceFiles*.writeToDir(file("src/hello"))

        and:
        buildFile << """
            executables {
                main {}
            }
            libraries {
                helloApi {}
                hello {}
            }
            sources.main.cpp.lib library: 'helloApi', linkage: 'api' // TODO:DAZ This should not be needed
            sources.main.cpp.lib library: 'hello'
            sources.hello.cpp.lib library: 'helloApi', linkage: 'api'
        """

        when:
        succeeds "mainVisualStudio"

        then:
        final mainSolution = solutionFile("visualStudio/mainExe.sln")
        mainSolution.assertHasProjects("mainExe", "helloDll")

        and:
        final mainExeProject = projectFile("visualStudio/mainExe.vcxproj")
        with (mainExeProject.projectConfigurations['win32Debug']) {
            includePath == filePath("src/main/headers", "src/helloApi/headers", "src/hello/headers")
        }

        and:
        final helloDllProject = projectFile("visualStudio/helloDll.vcxproj")
        with (helloDllProject.projectConfigurations['win32Debug']) {
            includePath == filePath("src/hello/headers", "src/helloApi/headers")
        }
    }

    def "create visual studio solution for executable with variant conditional sources"() {
        when:
        app.writeSources(file("src/win32"))
        app.alternate.writeSources(file("src/x64"))
        buildFile << """
    sources {
        win32 {}
        x64 {}
    }
    executables {
        main {}
    }
    binaries.all {
        source sources[it.targetPlatform.name]
    }
"""
        and:
        run "mainVisualStudio"

        then:
        final projectFile = projectFile("visualStudio/mainExe.vcxproj")
        projectFile.sourceFiles == allFiles("src/win32/cpp", "src/x64/cpp")
        projectFile.headerFiles == allFiles("src/win32/headers", "src/x64/headers")
        projectFile.projectConfigurations.keySet() == projectConfigurations

        and:
        final mainSolution = solutionFile("visualStudio/mainExe.sln")
        mainSolution.assertHasProjects("mainExe")
        mainSolution.assertReferencesProject(projectFile, projectConfigurations)
    }

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
    }
    executables {
        main {}
    }
    sources.main.cpp.lib library: 'test', linkage: 'api'
"""

        when:
        run "mainVisualStudio"

        then:
        executedAndNotSkipped ":mainExeVisualStudio"
        and:

        then:
        final mainSolution = solutionFile("visualStudio/mainExe.sln")
        mainSolution.assertHasProjects("mainExe")

        and:
        final mainExeProject = projectFile("visualStudio/mainExe.vcxproj")
        with (mainExeProject.projectConfigurations['win32Debug']) {
            includePath == filePath("src/main/headers", "libs/test/include")
        }
    }

    def "visual studio solution for component graph with library dependency cycle"() {
        given:
        def app = new ExeWithLibraryUsingLibraryHelloWorldApp()
        app.executable.writeSources(file("src/main"))
        app.library.writeSources(file("src/hello"))
        app.greetingsHeader.writeToDir(file("src/hello"))
        app.greetingsSources*.writeToDir(file("src/greetings"))

        and:
        buildFile << """
            executables {
                main {}
            }
            libraries {
                hello {}
                greetings {}
            }
            sources.main.cpp.lib library: 'hello'
            sources.hello.cpp.lib library: 'greetings', linkage: 'static'
            sources.greetings.cpp.lib library: 'hello', linkage: 'api'
        """

        when:
        succeeds "mainVisualStudio"

        then:
        final mainSolution = solutionFile("visualStudio/mainExe.sln")
        mainSolution.assertHasProjects("mainExe", "helloDll", "greetingsLib")

        and:
        final mainExeProject = projectFile("visualStudio/mainExe.vcxproj")
        with (mainExeProject.projectConfigurations['win32Debug']) {
            includePath == filePath("src/main/headers", "src/hello/headers")
        }

        and:
        final helloDllProject = projectFile("visualStudio/helloDll.vcxproj")
        with (helloDllProject.projectConfigurations['win32Debug']) {
            includePath == filePath( "src/hello/headers", "src/greetings/headers")
        }

        and:
        final greetingsLibProject = projectFile("visualStudio/greetingsLib.vcxproj")
        with (greetingsLibProject.projectConfigurations['win32Debug']) {
            includePath == filePath("src/greetings/headers", "src/hello/headers")
        }
    }

    def "create visual studio solution where referenced projects have different configurations"() {
        when:
        app.executable.writeSources(file("src/main"))
        app.library.writeSources(file("src/hello"))
        buildFile << """
    libraries {
        hello {}
    }
    executables {
        main {
            targetPlatforms "win32"
            targetBuildTypes "release"
        }
    }
    sources.main.cpp.lib libraries.hello
"""
        and:
        run "mainVisualStudio"

        then:
        final exeProject = projectFile("visualStudio/mainExe.vcxproj")
        exeProject.sourceFiles == allFiles("src/main/cpp")
        exeProject.headerFiles.isEmpty()
        exeProject.projectConfigurations.keySet() == ['release'] as Set
        exeProject.projectConfigurations['release'].includePath == filePath("src/main/headers", "src/hello/headers")

        and:
        final dllProject = projectFile("visualStudio/helloDll.vcxproj")
        dllProject.sourceFiles == allFiles("src/hello/cpp")
        dllProject.headerFiles == allFiles("src/hello/headers")
        dllProject.projectConfigurations.keySet() == projectConfigurations
        dllProject.projectConfigurations['win32Debug'].includePath == filePath("src/hello/headers")

        and:
        final mainSolution = solutionFile("visualStudio/mainExe.sln")
        mainSolution.assertHasProjects("mainExe", "helloDll")
        mainSolution.assertReferencesProject(exeProject, ['release'])
        mainSolution.assertReferencesProject(dllProject, [release: 'win32Release'])
    }

    private SolutionFile solutionFile(String path) {
        return new SolutionFile(file(path))
    }

    private ProjectFile projectFile(String path) {
        return new ProjectFile(file(path))
    }

    private List<String> allFiles(String... paths) {
        List allFiles = []
        paths.each { path ->
            file(path).listFiles().each { file ->
                allFiles.add "../${path}/${file.name}"
            }
        }
        return allFiles
    }

    private String filePath(String... paths) {
        return paths.collect {
            "../${it}"
        } .join(';')
    }
}
