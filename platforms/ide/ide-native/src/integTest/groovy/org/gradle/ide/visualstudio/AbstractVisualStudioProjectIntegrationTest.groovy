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
import org.gradle.ide.visualstudio.fixtures.ProjectFile
import org.gradle.ide.visualstudio.fixtures.SolutionFile
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.language.VariantContext
import org.gradle.nativeplatform.OperatingSystemFamily
import org.gradle.nativeplatform.fixtures.app.CppSourceElement
import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions

import static org.gradle.language.VariantContext.dimensions
import static org.gradle.nativeplatform.fixtures.ToolChainRequirement.WINDOWS_GCC
import static org.junit.Assume.assumeFalse

abstract class AbstractVisualStudioProjectIntegrationTest extends AbstractVisualStudioIntegrationSpec {

    def setup() {
        buildFile << """
            allprojects {
                apply plugin: 'visual-studio'
            }
        """
        settingsFile << """
            rootProject.name = "${rootProjectName}"
        """
    }

    @ToBeFixedForConfigurationCache
    def "ignores target machine not buildable from project configuration dimensions"() {
        assumeFalse(toolChain.meets(WINDOWS_GCC))

        when:
        componentUnderTest.writeToProject(testDirectory)
        makeSingleProject()
        buildFile << """
            ${componentUnderTestDsl}.targetMachines = [machines.os('os-family'), machines.${currentHostOperatingSystemFamilyDsl}.x86, machines.${currentHostOperatingSystemFamilyDsl}.x86_64]
        """

        and:
        run "visualStudio"

        then:
        result.assertTasksExecuted(":visualStudio", ":appVisualStudioSolution", projectTasks)

        and:
        def contexts = VariantContext.from(dimensions("buildType", ['debug', 'release']), dimensions("architecture", ['x86', 'x86-64']))
        def projectConfigurations = contexts*.asVariantName as Set
        projectFile.projectConfigurations.keySet() == projectConfigurations

        and:
        solutionFile.assertReferencesProject(projectFile, projectConfigurations)
    }

    @ToBeFixedForConfigurationCache
    def "create visual studio solution for component with multiple target machines"() {
        assumeFalse(toolChain.meets(WINDOWS_GCC))

        when:
        componentUnderTest.writeToProject(testDirectory)
        makeSingleProject()
        buildFile << """
            ${componentUnderTestDsl}.targetMachines = [machines.${currentHostOperatingSystemFamilyDsl}.x86, machines.${currentHostOperatingSystemFamilyDsl}.x86_64]
        """

        and:
        run "visualStudio"

        then:
        result.assertTasksExecuted(":visualStudio", ":appVisualStudioSolution", projectTasks)

        and:
        projectFile.assertHasComponentSources(componentUnderTest, "src/main")

        def contexts = VariantContext.from(dimensions("buildType", ['debug', 'release']), dimensions("architecture", ['x86', 'x86-64']))
        projectFile.projectConfigurations.size() == contexts.size()

        contexts.each {
            assert projectFile.projectConfigurations[it.asVariantName].includePath == filePath(expectedBaseIncludePaths)
            assert projectFile.projectConfigurations[it.asVariantName].buildCommand.endsWith("gradle\" :${getIdeBuildTaskName(it.asVariantName)}")
            assert projectFile.projectConfigurations[it.asVariantName].outputFile == getBuildFile(it)
        }

        and:
        solutionFile.assertHasProjects(visualStudioProjectName)
        solutionFile.assertReferencesProject(projectFile, contexts*.asVariantName as Set)
    }

    @Requires(IntegTestPreconditions.HasMsBuild)
    @ToBeFixedForConfigurationCache
    def "build generated visual studio solution with multiple target machines"() {
        assumeFalse(toolChain.meets(WINDOWS_GCC))
        useMsbuildTool()

        given:
        componentUnderTest.writeToProject(testDirectory)
        makeSingleProject()
        buildFile << """
            ${componentUnderTestDsl}.targetMachines = [machines.${currentHostOperatingSystemFamilyDsl}.x86, machines.${currentHostOperatingSystemFamilyDsl}.x86_64]
        """
        run "visualStudio"

        when:
        def resultDebug = msbuild
                .withWorkingDir(testDirectory)
                .withSolution(solutionFile)
                .withConfiguration("debugX86")
                .withProject(visualStudioProjectName)
                .succeeds()

        then:
        resultDebug.size() == 1
        resultDebug[0].assertTasksExecuted(getTasksToBuildFromIde("debugX86"))
        file(getBuildFile(VariantContext.of(buildType: 'debug', architecture: 'x86'))).assertIsFile()

        when:
        def resultRelease = msbuild
                .withWorkingDir(testDirectory)
                .withSolution(solutionFile)
                .withConfiguration("releaseX86-64")
                .withProject(visualStudioProjectName)
                .succeeds()

        then:
        resultRelease.size() == 1
        resultRelease[0].assertTasksExecuted(getTasksToBuildFromIde("releaseX86-64"))
        file(getBuildFile(VariantContext.of(buildType: 'release', architecture: 'x86-64'))).assertIsFile()
    }

    def "can configure project when plugin applied containing unbuildable architecture"() {
        assumeFalse(toolChain.meets(WINDOWS_GCC))

        given:
        componentUnderTest.writeToProject(testDirectory)
        makeSingleProject()
        buildFile << """
            ${componentUnderTestDsl}.targetMachines = [machines.${currentHostOperatingSystemFamilyDsl}.architecture('foo'), machines.${currentHostOperatingSystemFamilyDsl}.x86, machines.${currentHostOperatingSystemFamilyDsl}.x86_64]

            ${configureToolChainSupport('foo')}
        """

        expect:
        succeeds "help"
    }

    @ToBeFixedForConfigurationCache
    def "can create visual studio project for unbuildable component"() {
        given:
        makeSingleProject()
        buildFile << """
            ${componentUnderTestDsl}.targetMachines = [machines.os('os-family')]
        """
        componentUnderTest.writeToProject(testDirectory)

        when:
        succeeds "visualStudio"

        then:
        executedAndNotSkipped(":visualStudio", ":appVisualStudioSolution", *projectTasks)

        and:
        def projectConfigurations = ["unbuildable"] as Set
        projectFile.assertHasComponentSources(componentUnderTest, "src/main")
        projectFile.projectConfigurations.keySet() == projectConfigurations

        and:
        solutionFile.assertReferencesProject(projectFile, projectConfigurations)
    }

    @ToBeFixedForConfigurationCache
    def "warns about unbuildable components in generated visual studio project"() {
        given:
        makeSingleProject()
        buildFile << """
            ${componentUnderTestDsl}.targetMachines = [machines.os('os-family')]
        """
        componentUnderTest.writeToProject(testDirectory)

        when:
        succeeds "visualStudio"

        then:
        executedAndNotSkipped(":visualStudio", ":appVisualStudioSolution", *projectTasks)
        outputContains("'main' component in project ':' is not buildable.");
    }

    @Requires(IntegTestPreconditions.HasMsBuild)
    @ToBeFixedForConfigurationCache
    def "returns meaningful errors from visual studio when component product is unbuildable due to operating system"() {
        assumeFalse(toolChain.meets(WINDOWS_GCC))
        useMsbuildTool()

        given:
        componentUnderTest.writeToProject(testDirectory)
        makeSingleProject()
        buildFile << """
            ${componentUnderTestDsl}.targetMachines = [machines.os('os-family')]
        """
        succeeds "visualStudio"

        when:
        def resultSolution = msbuild
                .withWorkingDir(testDirectory)
                .withSolution(solutionFile)
                .succeeds()

        then:
        resultSolution.size() == 1
        resultSolution[0].assertOutputContains('The project "' + visualStudioProjectName + '" is not selected for building in solution configuration "unbuildable|Win32".')

        when:
        def resultProject = msbuild
                .withWorkingDir(testDirectory)
                .withSolution(solutionFile)
                .withProject(visualStudioProjectName)
                .run()

        then:
        resultProject.assertOutputContains('The project "' + visualStudioProjectName + '" is not selected for building in solution configuration "unbuildable|Win32".')
    }

    @Requires(IntegTestPreconditions.HasMsBuild)
    @ToBeFixedForConfigurationCache
    def "returns meaningful errors from visual studio when component product is unbuildable due to architecture"() {
        assumeFalse(toolChain.meets(WINDOWS_GCC))
        useMsbuildTool()

        given:
        componentUnderTest.writeToProject(testDirectory)
        makeSingleProject()
        buildFile << """
            ${componentUnderTestDsl}.targetMachines = [machines.${currentHostOperatingSystemFamilyDsl}.architecture('foo')]
            ${configureToolChainSupport('foo')}
        """
        succeeds "visualStudio"

        when:
        def result = msbuild
                .withWorkingDir(testDirectory)
                .withSolution(solutionFile)
                .withProject(visualStudioProjectName)
                .fails()

        then:
        result.assertHasCause("No tool chain is available to build C++")
    }

    @ToBeFixedForConfigurationCache
    def "can detect the language standard for Visual Studio IntelliSense [#expectedLanguageStandard] #uniqueIndex"() {
        assumeFalse(toolChain.meets(WINDOWS_GCC))

        given:
        componentUnderTest.writeToProject(testDirectory)
        makeSingleProject()
        buildFile << """
            ${componentUnderTestDsl}.binaries.configureEach {
                compileTask.get().compilerArgs.add('${compilerFlag}')
            }
        """

        when:
        succeeds "visualStudio"

        then:
        result.assertTasksExecuted(":visualStudio", ":appVisualStudioSolution", projectTasks)

        and:
        projectFile.projectConfigurations.size() == 2
        projectFile.projectConfigurations.values().each {
            it.languageStandard == expectedLanguageStandard
        }

        where:
        // UniqueIndex: // uniqueIndex: https://github.com/gradle/gradle/issues/8787
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
        assumeFalse(toolChain.meets(WINDOWS_GCC))

        given:
        componentUnderTest.writeToProject(testDirectory)
        makeSingleProject()
        buildFile << """
            ${componentUnderTestDsl}.binaries.configureEach {
                if (optimized) {
                    compileTask.get().compilerArgs.add('/std:c++17')
                } else {
                    compileTask.get().compilerArgs.add('/std:c++14')
                }
            }
        """

        when:
        succeeds "visualStudio"

        then:
        result.assertTasksExecuted(":visualStudio", ":appVisualStudioSolution", projectTasks)

        and:
        projectFile.projectConfigurations.size() == 2
        projectFile.projectConfigurations['debug'].languageStandard == 'stdcpp14'
        projectFile.projectConfigurations['release'].languageStandard == 'stdcpp17'
    }

    @ToBeFixedForConfigurationCache
    def "does not configure language standard when compiler flag is absent for Visual Studio Intellisense"() {
        assumeFalse(toolChain.meets(WINDOWS_GCC))

        given:
        componentUnderTest.writeToProject(testDirectory)
        makeSingleProject()

        when:
        succeeds "visualStudio"

        then:
        result.assertTasksExecuted(":visualStudio", ":appVisualStudioSolution", projectTasks)

        and:
        projectFile.projectConfigurations.size() == 2
        projectFile.projectConfigurations['debug'].languageStandard == null
        projectFile.projectConfigurations['release'].languageStandard == null
    }

    String getRootProjectName() {
        return "app"
    }

    SolutionFile getSolutionFile() {
        return solutionFile("${rootProjectName}.sln")
    }

    ProjectFile getProjectFile() {
        return projectFile("${visualStudioProjectName}.vcxproj")
    }

    abstract String getBuildFile(VariantContext variantContext)

    abstract void makeSingleProject()

    abstract String getVisualStudioProjectName()

    abstract String getComponentUnderTestDsl()

    abstract CppSourceElement getComponentUnderTest()

    abstract String getIdeBuildTaskName(String variant)

    abstract List<String> getTasksToBuildFromIde(String variant)

    String[] getProjectTasks() {
        return [":${visualStudioProjectName}VisualStudioProject", ":${visualStudioProjectName}VisualStudioFilters"]
    }

    List<String> getExpectedBaseIncludePaths() {
        return ["src/main/headers"]
    }

    protected String stripped(String configurationName) {
        if (toolChain.visualCpp) {
            return ""
        } else {
            return configurationName.startsWith("release") ? "stripped/" : ""
        }
    }

    protected String getCurrentHostOperatingSystemFamilyDsl() {
        String osFamily = DefaultNativePlatform.getCurrentOperatingSystem().toFamilyName()
        if (osFamily == OperatingSystemFamily.MACOS) {
            return "macOS"
        } else {
            return osFamily
        }
    }

    protected String configureToolChainSupport(String architecture) {
        return """
            model {
                toolChains {
                    toolChainFor${architecture.capitalize()}Architecture(Gcc) {
                        path "/not/found"
                        target("host:${architecture}")
                    }
                }
            }
        """
    }
}
