/*
 * Copyright 2018 the original author or authors.
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
import org.gradle.internal.os.OperatingSystem
import org.gradle.nativeplatform.fixtures.app.CppHelloWorldApp


class VisualStudioSingleProjectIntegrationTest extends AbstractVisualStudioIntegrationSpec {
    private final Set<String> projectConfigurations = ['debug', 'release'] as Set

    def app = new CppHelloWorldApp()

    def setup() {
        buildFile << """
            apply plugin: 'visual-studio'
        """
    }

    def "create visual studio solution for single executable"() {
        when:
        app.writeSources(file("src/main"))
        settingsFile << """
            rootProject.name = 'app'
        """
        buildFile << """
            apply plugin: 'cpp-application'
            
            application {
                binaries.configureEach { binary ->
                    binary.compileTask.get().macros["TEST"] = null
                    binary.compileTask.get().macros["foo"] = "bar"
                }
            }
        """

        and:
        run "visualStudio"

        then:
        executedAndNotSkipped ":visualStudio", ":appVisualStudioSolution"
        executedAndNotSkipped getProjectTasks("app")

        and:
        final projectFile = projectFile("app.vcxproj")
        projectFile.assertHasComponentSources(app, "src/main")
        projectFile.projectConfigurations.keySet() == projectConfigurations
        projectFile.projectConfigurations.values().each {
            assert it.macros == "TEST;foo=bar"
            assert it.includePath == filePath("src/main/headers")
            assert it.buildCommand.endsWith("gradle\" :install${it.name.capitalize()}")
            assert it.outputFile == OperatingSystem.current().getExecutableName("build/install/main/${it.name.toLowerCase()}/lib/app")
        }

        and:
        final mainSolution = solutionFile("app.sln")
        mainSolution.assertHasProjects("app")
        mainSolution.assertReferencesProject(projectFile, projectConfigurations)
    }

    def "create visual studio solution for single shared library"() {
        when:
        app.library.writeSources(file("src/main"))
        settingsFile << """
            rootProject.name = 'lib'
        """
        buildFile << """
            apply plugin: 'cpp-library'
            
            library {
                binaries.configureEach { binary ->
                    binary.compileTask.get().macros["TEST"] = null
                    binary.compileTask.get().macros["foo"] = "bar"
                }
            }
        """

        and:
        run "visualStudio"

        then:
        executedAndNotSkipped ":visualStudio", ":libVisualStudioSolution"
        executedAndNotSkipped getProjectTasks("libDll")

        and:
        final projectFile = projectFile("libDll.vcxproj")
        projectFile.assertHasComponentSources(app.library, "src/main")
        projectFile.projectConfigurations.keySet() == projectConfigurations
        projectFile.projectConfigurations.values().each {
            assert it.macros == "TEST;foo=bar"
            assert it.includePath == filePath("src/main/public", "src/main/headers")
            assert it.buildCommand.endsWith("gradle\" :link${it.name.capitalize()}")
            assert it.outputFile == OperatingSystem.current().getSharedLibraryName("build/lib/main/${it.name.toLowerCase()}/${stripped(it.name)}lib")
        }

        and:
        final mainSolution = solutionFile("lib.sln")
        mainSolution.assertHasProjects("libDll")
        mainSolution.assertReferencesProject(projectFile, projectConfigurations)
    }

    def "create visual studio solution for single static library"() {
        when:
        app.library.writeSources(file("src/main"))
        settingsFile << """
            rootProject.name = 'lib'
        """
        buildFile << """
            apply plugin: 'cpp-library'
            
            library {
                linkage = [Linkage.STATIC]
                binaries.configureEach { binary ->
                    binary.compileTask.get().macros["TEST"] = null
                    binary.compileTask.get().macros["foo"] = "bar"
                }
            }
        """

        and:
        run "visualStudio"

        then:
        executedAndNotSkipped ":visualStudio", ":libVisualStudioSolution"
        executedAndNotSkipped getProjectTasks("libLib")

        and:
        final projectFile = projectFile("libLib.vcxproj")
        projectFile.assertHasComponentSources(app.library, "src/main")
        projectFile.projectConfigurations.keySet() == projectConfigurations
        projectFile.projectConfigurations.values().each {
            assert it.macros == "TEST;foo=bar"
            assert it.includePath == filePath("src/main/public", "src/main/headers")
            assert it.buildCommand.endsWith("gradle\" :create${it.name.capitalize()}")
            assert it.outputFile == OperatingSystem.current().getStaticLibraryName("build/lib/main/${it.name.toLowerCase()}/lib")
        }

        and:
        final mainSolution = solutionFile("lib.sln")
        mainSolution.assertHasProjects("libLib")
        mainSolution.assertReferencesProject(projectFile, projectConfigurations)
    }

    def "create visual studio solution for single library with both static and shared linkages"() {
        when:
        app.library.writeSources(file("src/main"))
        settingsFile << """
            rootProject.name = 'lib'
        """
        buildFile << """
            apply plugin: 'cpp-library'
            
            library {
                linkage = [Linkage.STATIC, Linkage.SHARED]
                binaries.configureEach { binary ->
                    binary.compileTask.get().macros["TEST"] = null
                    binary.compileTask.get().macros["foo"] = "bar"
                }
            }
        """

        and:
        run "visualStudio"

        then:
        executedAndNotSkipped ":visualStudio", ":libVisualStudioSolution"
        executedAndNotSkipped getProjectTasks("libLib")
        executedAndNotSkipped getProjectTasks("libDll")

        and:
        final libProjectFile = projectFile("libLib.vcxproj")
        libProjectFile.assertHasComponentSources(app.library, "src/main")
        libProjectFile.projectConfigurations.keySet() == projectConfigurations
        libProjectFile.projectConfigurations.values().each {
            assert it.macros == "TEST;foo=bar"
            assert it.includePath == filePath("src/main/public", "src/main/headers")
            assert it.buildCommand.endsWith("gradle\" :create${it.name.capitalize()}Static")
            assert it.outputFile == OperatingSystem.current().getStaticLibraryName("build/lib/main/${it.name.toLowerCase()}/static/lib")
        }

        and:
        final dllProjectFile = projectFile("libDll.vcxproj")
        dllProjectFile.assertHasComponentSources(app.library, "src/main")
        dllProjectFile.projectConfigurations.keySet() == projectConfigurations
        dllProjectFile.projectConfigurations.values().each {
            assert it.macros == "TEST;foo=bar"
            assert it.includePath == filePath("src/main/public", "src/main/headers")
            assert it.buildCommand.endsWith("gradle\" :link${it.name.capitalize()}Shared")
            assert it.outputFile == OperatingSystem.current().getSharedLibraryName("build/lib/main/${it.name.toLowerCase()}/shared/${stripped(it.name)}lib")
        }

        and:
        final mainSolution = solutionFile("lib.sln")
        mainSolution.assertHasProjects("libLib", "libDll")
        mainSolution.assertReferencesProject(dllProjectFile, projectConfigurations)
    }

    private String[] getProjectTasks(String exeName) {
        return [":${exeName}VisualStudioProject", ":${exeName}VisualStudioFilters"]
    }

    private String stripped(String configurationName) {
        if (toolChain.visualCpp) {
            return ""
        } else {
            return configurationName == "release" ? "stripped/" : ""
        }
    }
}
