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

class VisualStudioMultiProjectIntegrationTest extends AbstractInstalledToolChainIntegrationSpec {
    private final Set<String> projectConfigurations = ['debug|Win32', 'release|Win32'] as Set

    def app = new CppHelloWorldApp()

    def setup() {
        settingsFile.text = "include ':exe', ':lib'"
        buildFile << """
    subprojects {
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
    }

"""
    }

    def "create visual studio solution for executable that depends on static library in another project"() {
        when:
        app.executable.writeSources(file("exe/src/main"))
        app.library.writeSources(file("lib/src/hello"))
        buildFile << """
    project(':exe') {
        executables {
            main {}
        }
        sources.main.cpp.lib project: ':lib', library: 'hello', linkage: 'static'
    }
    project(':lib') {
        libraries {
            hello {}
        }
    }
"""
        and:
        run ":exe:mainVisualStudio"

        then:
        final exeProject = projectFile("exe/visualStudio/mainExe.vcxproj")
        exeProject.sourceFiles == allFiles("exe/src/main/cpp")
        exeProject.headerFiles.isEmpty()
        exeProject.projectConfigurations.keySet() == projectConfigurations
        exeProject.projectConfigurations['debug|Win32'].includePath == filePath("exe/src/main/headers", "lib/src/hello/headers")

        and:
        final libProject = projectFile("lib/visualStudio/helloLib.vcxproj")
        libProject.sourceFiles == allFiles("lib/src/hello/cpp")
        libProject.headerFiles == allFiles("lib/src/hello/headers")
        libProject.projectConfigurations.keySet() == projectConfigurations
        libProject.projectConfigurations['debug|Win32'].includePath == filePath("lib/src/hello/headers")

        and:
        final mainSolution = solutionFile("exe/visualStudio/mainExe.sln")
        mainSolution.projects.keySet() == ["mainExe", "helloLib"] as Set
        with (mainSolution.projects['mainExe']) {
            file == filePath('exe/visualStudio/mainExe.vcxproj')
            uuid == exeProject.projectGuid
            configurations == ['debug|Win32']
        }
        with (mainSolution.projects['helloLib']) {
            file == filePath('lib/visualStudio/helloLib.vcxproj')
            uuid == libProject.projectGuid
            configurations == ['debug|Win32']
        }
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
