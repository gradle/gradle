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

import groovy.test.NotYetImplemented
import org.gradle.ide.visualstudio.fixtures.AbstractVisualStudioIntegrationSpec
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.internal.os.OperatingSystem
import org.gradle.nativeplatform.fixtures.app.CppHelloWorldApp
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions


class VisualStudioSingleProjectIntegrationTest extends AbstractVisualStudioIntegrationSpec {
    def app = new CppHelloWorldApp()

    def setup() {
        buildFile << """
            apply plugin: 'visual-studio'
        """
    }

    @ToBeFixedForConfigurationCache
    def "create visual studio solution for project without C++ component"() {
        when:
        settingsFile << """
            rootProject.name = 'app'
        """

        and:
        run "visualStudio"

        then:
        result.assertTasksExecuted(":visualStudio", ":appVisualStudioSolution")

        and:
        final mainSolution = solutionFile("app.sln")
        mainSolution.assertHasProjects()
    }

    @ToBeFixedForConfigurationCache
    def "create empty solution when component does not target current OS"() {
        when:
        settingsFile << """
            rootProject.name = 'app'
        """

        buildFile << """
            apply plugin: 'cpp-application'

            application {
                targetMachines = [machines.os('os-family')]
            }
        """

        and:
        run "visualStudio"

        then:
        executedAndNotSkipped(":visualStudio", ":appVisualStudioSolution", *getProjectTasks("app"))

        and:
        final mainSolution = solutionFile("app.sln")
        mainSolution.assertHasProjects("app")
    }

    @ToBeFixedForConfigurationCache
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
        result.assertTasksExecuted(":visualStudio", ":appVisualStudioSolution", getProjectTasks("app"))

        and:
        final projectFile = projectFile("app.vcxproj")
        projectFile.assertHasComponentSources(app, "src/main")
        projectFile.projectConfigurations.keySet() == projectConfigurations
        projectFile.projectConfigurations.values().each {
            assert it.macros == "TEST;foo=bar"
            assert it.includePath == filePath("src/main/headers")
            assert it.buildCommand.endsWith("gradle\" :install${it.name.capitalize()}")
            assert it.outputFile == OperatingSystem.current().getExecutableName("build/install/main/${it.name.toLowerCase(Locale.ROOT)}/lib/app")
        }

        and:
        final mainSolution = solutionFile("app.sln")
        mainSolution.assertHasProjects("app")
        mainSolution.assertReferencesProject(projectFile, projectConfigurations)
    }

    @ToBeFixedForConfigurationCache
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
        result.assertTasksExecuted(":visualStudio", ":libVisualStudioSolution", getProjectTasks("libDll"))

        and:
        final projectFile = projectFile("libDll.vcxproj")
        projectFile.assertHasComponentSources(app.library, "src/main")
        projectFile.projectConfigurations.keySet() == projectConfigurations
        projectFile.projectConfigurations.values().each {
            assert it.macros == "TEST;foo=bar"
            assert it.includePath == filePath("src/main/public", "src/main/headers")
            assert it.buildCommand.endsWith("gradle\" :link${it.name.capitalize()}")
            assert it.outputFile == OperatingSystem.current().getSharedLibraryName("build/lib/main/${it.name.toLowerCase(Locale.ROOT)}/${stripped(it.name)}lib")
        }

        and:
        final mainSolution = solutionFile("lib.sln")
        mainSolution.assertHasProjects("libDll")
        mainSolution.assertReferencesProject(projectFile, projectConfigurations)
    }

    @ToBeFixedForConfigurationCache
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
        result.assertTasksExecuted(":visualStudio", ":libVisualStudioSolution", getProjectTasks("libLib"))

        and:
        final projectFile = projectFile("libLib.vcxproj")
        projectFile.assertHasComponentSources(app.library, "src/main")
        projectFile.projectConfigurations.keySet() == projectConfigurations
        projectFile.projectConfigurations.values().each {
            assert it.macros == "TEST;foo=bar"
            assert it.includePath == filePath("src/main/public", "src/main/headers")
            assert it.buildCommand.endsWith("gradle\" :create${it.name.capitalize()}")
            assert it.outputFile == OperatingSystem.current().getStaticLibraryName("build/lib/main/${it.name.toLowerCase(Locale.ROOT)}/lib")
        }

        and:
        final mainSolution = solutionFile("lib.sln")
        mainSolution.assertHasProjects("libLib")
        mainSolution.assertReferencesProject(projectFile, projectConfigurations)
    }

    @ToBeFixedForConfigurationCache
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
        result.assertTasksExecuted(":visualStudio", ":libVisualStudioSolution", getProjectTasks("libLib"), getProjectTasks("libDll"))

        and:
        final libProjectFile = projectFile("libLib.vcxproj")
        libProjectFile.assertHasComponentSources(app.library, "src/main")
        libProjectFile.projectConfigurations.keySet() == projectConfigurations
        libProjectFile.projectConfigurations.values().each {
            assert it.macros == "TEST;foo=bar"
            assert it.includePath == filePath("src/main/public", "src/main/headers")
            assert it.buildCommand.endsWith("gradle\" :create${it.name.capitalize()}Static")
            assert it.outputFile == OperatingSystem.current().getStaticLibraryName("build/lib/main/${it.name.toLowerCase(Locale.ROOT)}/static/lib")
        }

        and:
        final dllProjectFile = projectFile("libDll.vcxproj")
        dllProjectFile.assertHasComponentSources(app.library, "src/main")
        dllProjectFile.projectConfigurations.keySet() == projectConfigurations
        dllProjectFile.projectConfigurations.values().each {
            assert it.macros == "TEST;foo=bar"
            assert it.includePath == filePath("src/main/public", "src/main/headers")
            assert it.buildCommand.endsWith("gradle\" :link${it.name.capitalize()}Shared")
            assert it.outputFile == OperatingSystem.current().getSharedLibraryName("build/lib/main/${it.name.toLowerCase(Locale.ROOT)}/shared/${stripped(it.name)}lib")
        }

        and:
        final mainSolution = solutionFile("lib.sln")
        mainSolution.assertHasProjects("libLib", "libDll")
        mainSolution.assertReferencesProject(dllProjectFile, projectConfigurations)
    }

    @Requires(IntegTestPreconditions.HasMsBuild)
    def "can build executable from visual studio"() {
        useMsbuildTool()
        def debugBinary = executable("build/install/main/debug/lib/app")

        given:
        app.writeSources(file("src/main"))
        settingsFile << """
            rootProject.name = 'app'
        """
        buildFile << """
            apply plugin: 'cpp-application'
        """

        and:
        succeeds "visualStudio"

        when:
        debugBinary.assertDoesNotExist()
        def resultDebug = msbuild
            .withSolution(solutionFile("app.sln"))
            .withConfiguration('Debug')
            .succeeds()

        then:
        resultDebug.size() == 1
        debugBinary.assertExists()
        installation('build/install/main/debug').assertInstalled()
    }

    @Requires(IntegTestPreconditions.HasMsBuild)
    def "can build library from visual studio"() {
        useMsbuildTool()
        def debugBinaryLib = staticLibrary("build/lib/main/debug/static/lib")
        def debugBinaryDll = sharedLibrary("build/lib/main/debug/shared/lib")

        given:
        app.library.writeSources(file("src/main"))
        settingsFile << """
            rootProject.name = 'lib'
        """
        buildFile << """
            apply plugin: 'cpp-library'

            library {
                linkage = [Linkage.STATIC, Linkage.SHARED]
            }
        """

        and:
        succeeds "visualStudio"

        when:
        debugBinaryLib.assertDoesNotExist()
        debugBinaryDll.assertDoesNotExist()
        def resultDebug = msbuild
            .withSolution(solutionFile("lib.sln"))
            .withConfiguration('Debug')
            .succeeds()

        then:
        resultDebug.size() == 2
        debugBinaryLib.assertExists()
        debugBinaryDll.assertExists()
    }

    @ToBeFixedForConfigurationCache
    def "builds solution for component with no source"() {
        given:
        settingsFile << """
            rootProject.name = 'app'
        """
        buildFile << """
            apply plugin: 'cpp-application'
        """

        when:
        run "visualStudio"

        then:
        result.assertTasksExecuted(":visualStudio", ":appVisualStudioSolution", getProjectTasks("app"))

        and:
        final projectFile = projectFile("app.vcxproj")
        projectFile.sourceFiles == ['build.gradle']
        projectFile.headerFiles == []
        projectFile.projectConfigurations.keySet() == projectConfigurations
        with (projectFile.projectConfigurations['debug']) {
            includePath == filePath("src/main/headers")
        }

        and:
        solutionFile("app.sln").assertHasProjects("app")
    }

    @NotYetImplemented
    def "visual studio project includes headers co-located with sources"() {
        when:
        // Write headers so they sit with sources
        app.files.each {
            it.writeToFile(file("src/main/cpp/${it.name}"))
        }
        settingsFile << """
            rootProject.name = 'app'
        """
        buildFile << """
            apply plugin: 'cpp-application'
        """

        and:
        run "visualStudio"

        then:
        executedAndNotSkipped getProjectTasks("app")

        and:
        final projectFile = projectFile("app.vcxproj")
        assert projectFile.sourceFiles == ['build.gradle'] + app.sourceFiles.collect({"src/main/cpp/${it.name}"}).sort()
        assert projectFile.headerFiles == app.headerFiles.collect({"src/main/cpp/${it.name}"}).sort()
    }

    private String[] getProjectTasks(String exeName) {
        return [":${exeName}VisualStudioProject", ":${exeName}VisualStudioFilters"]
    }

    private String stripped(String configurationName) {
        if (toolChain.visualCpp) {
            return ""
        } else {
            return configurationName.startsWith("release") ? "stripped/" : ""
        }
    }
}
