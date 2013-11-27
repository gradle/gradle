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
import org.gradle.nativebinaries.language.cpp.fixtures.AbstractInstalledToolChainIntegrationSpec
import org.gradle.nativebinaries.language.cpp.fixtures.app.CppHelloWorldApp
import org.gradle.nativebinaries.language.cpp.fixtures.app.ExeWithLibraryUsingLibraryHelloWorldApp

class VisualStudioSingleProjectIntegrationTest extends AbstractInstalledToolChainIntegrationSpec {

    def app = new CppHelloWorldApp()

    def setup() {
        buildFile << """
    apply plugin: 'cpp'
    apply plugin: 'visual-studio'

    model {
        platforms {
            create("win32") {
                architecture "i386"
            }
        }
        buildTypes {
            create("debug")
            create("release")
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
        final projectFile = projectFile("visualStudio/mainExe.vcxproj")
        projectFile.sourceFiles == allFiles("src/main/cpp")
        projectFile.headerFiles == allFiles("src/main/headers")
        projectFile.projectConfigurations.keySet() == ['debug|Win32'] as Set
        with (projectFile.projectConfigurations['debug|Win32']) {
            macros == "TEST;foo=bar"
            includePath == filePath("src/main/headers")
        }

        and:
        final mainSolution = solutionFile("visualStudio/main.sln")
        mainSolution.projects.keySet() == ["mainExe"] as Set
        with (mainSolution.projects['mainExe']) {
            file == 'mainExe.vcxproj'
            uuid == projectFile.projectGuid
            configurations == ['debug|Win32']
        }
    }

    def "create visual studio solution for single library"() {
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
        final projectFile = projectFile("visualStudio/mainDll.vcxproj")
        projectFile.sourceFiles == allFiles("src/main/cpp")
        projectFile.headerFiles == allFiles("src/main/headers")
        projectFile.projectConfigurations.keySet() == ['debug|Win32'] as Set
        projectFile.projectConfigurations['debug|Win32'].includePath == filePath("src/main/headers")

        and:
        final mainSolution = solutionFile("visualStudio/main.sln")
        mainSolution.projects.keySet() == ["mainDll"] as Set
        with (mainSolution.projects['mainDll']) {
            file == 'mainDll.vcxproj'
            uuid == projectFile.projectGuid
            configurations == ['debug|Win32']
        }
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
        exeProject.projectConfigurations.keySet() == ['debug|Win32'] as Set
        exeProject.projectConfigurations['debug|Win32'].includePath == filePath("src/main/headers", "src/hello/headers")

        and:
        final libProject = projectFile("visualStudio/helloLib.vcxproj")
        libProject.sourceFiles == allFiles("src/hello/cpp")
        libProject.headerFiles == allFiles("src/hello/headers")
        libProject.projectConfigurations.keySet() == ['debug|Win32'] as Set
        libProject.projectConfigurations['debug|Win32'].includePath == filePath("src/hello/headers")

        and:
        final mainSolution = solutionFile("visualStudio/main.sln")
        mainSolution.projects.keySet() == ["mainExe", "helloLib"] as Set
        with (mainSolution.projects['mainExe']) {
            file == 'mainExe.vcxproj'
            uuid == exeProject.projectGuid
            configurations == ['debug|Win32']
        }
        with (mainSolution.projects['helloLib']) {
            file == 'helloLib.vcxproj'
            uuid == libProject.projectGuid
            configurations == ['debug|Win32']
        }
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
        exeProject.projectConfigurations.keySet() == ['debug|Win32'] as Set
        exeProject.projectConfigurations['debug|Win32'].includePath == filePath("src/main/headers", "src/hello/headers")

        and:
        final dllProject = projectFile("visualStudio/helloDll.vcxproj")
        dllProject.sourceFiles == allFiles("src/hello/cpp")
        dllProject.headerFiles == allFiles("src/hello/headers")
        dllProject.projectConfigurations.keySet() == ['debug|Win32'] as Set
        dllProject.projectConfigurations['debug|Win32'].includePath == filePath("src/hello/headers")

        and:
        final mainSolution = solutionFile("visualStudio/main.sln")
        mainSolution.projects.keySet() == ["mainExe", "helloDll"] as Set
        with (mainSolution.projects['mainExe']) {
            file == 'mainExe.vcxproj'
            uuid == exeProject.projectGuid
            configurations == ['debug|Win32']
        }
        with (mainSolution.projects['helloDll']) {
            file == 'helloDll.vcxproj'
            uuid == dllProject.projectGuid
            configurations == ['debug|Win32']
        }
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
                hello.cpp.lib libraries.greetings
                main.cpp.lib libraries.hello
            }
        """
        when:
        run "mainVisualStudio"

        then:
        solutionFile("visualStudio/main.sln").assertHasProjects("mainExe", "helloDll", "greetingsDll")

        then:
        final exeProject = projectFile("visualStudio/mainExe.vcxproj")
        exeProject.sourceFiles == allFiles("src/main/cpp")
        exeProject.headerFiles.isEmpty()
        exeProject.projectConfigurations.keySet() == ['debug|Win32'] as Set
        exeProject.projectConfigurations['debug|Win32'].includePath == filePath("src/main/headers", "src/hello/headers")

        and:
        final helloDllProject = projectFile("visualStudio/helloDll.vcxproj")
        helloDllProject.sourceFiles == allFiles("src/hello/cpp")
        helloDllProject.headerFiles == allFiles("src/hello/headers")
        helloDllProject.projectConfigurations.keySet() == ['debug|Win32'] as Set
        helloDllProject.projectConfigurations['debug|Win32'].includePath == filePath("src/hello/headers", "src/greetings/headers")

        and:
        final greetingsDllProject = projectFile("visualStudio/greetingsDll.vcxproj")
        greetingsDllProject.sourceFiles == allFiles("src/greetings/cpp")
        greetingsDllProject.headerFiles == allFiles("src/greetings/headers")
        greetingsDllProject.projectConfigurations.keySet() == ['debug|Win32'] as Set
        greetingsDllProject.projectConfigurations['debug|Win32'].includePath == file("src/greetings/headers").absolutePath

        and:
        final mainSolution = solutionFile("visualStudio/main.sln")
        mainSolution.projects.keySet() == ["mainExe", "helloDll", "greetingsDll"] as Set
        with (mainSolution.projects['mainExe']) {
            file == 'mainExe.vcxproj'
            uuid == exeProject.projectGuid
            configurations == ['debug|Win32']
        }
        with (mainSolution.projects['helloDll']) {
            file == 'helloDll.vcxproj'
            uuid == helloDllProject.projectGuid
            configurations == ['debug|Win32']
        }
        with (mainSolution.projects['greetingsDll']) {
            file == 'greetingsDll.vcxproj'
            uuid == greetingsDllProject.projectGuid
            configurations == ['debug|Win32']
        }
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
        solutionFile("visualStudio/main.sln").assertHasProjects("mainExe", "helloDll")
        solutionFile("visualStudio/mainStatic.sln").assertHasProjects("mainStaticExe", "helloLib")

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

    private SolutionFile solutionFile(String path) {
        return new SolutionFile(file(path))
    }

    private ProjectFile projectFile(String path) {
        return new ProjectFile(file(path))
    }

    private List<String> allFiles(String path) {
        return file(path).listFiles()*.absolutePath as List
    }

    private String filePath(String... paths) {
        return paths.collect {
            file(it).absolutePath
        } .join(';')
    }
}
