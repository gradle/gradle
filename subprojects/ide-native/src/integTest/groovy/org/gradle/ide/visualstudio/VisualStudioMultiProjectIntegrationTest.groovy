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
import org.gradle.nativeplatform.fixtures.app.CppAppWithLibrary
import org.gradle.nativeplatform.fixtures.app.CppHelloWorldApp
import org.gradle.nativeplatform.fixtures.app.ExeWithLibraryUsingLibraryHelloWorldApp
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions


class VisualStudioMultiProjectIntegrationTest extends AbstractVisualStudioIntegrationSpec {
    def app = new CppHelloWorldApp()

    def setup() {
        settingsFile << """
            rootProject.name = 'app'
        """
        buildFile << """
            allprojects {
                apply plugin: 'visual-studio'
            }
        """
    }

    @ToBeFixedForConfigurationCache
    def "create visual studio solution for build without any C++ components"() {
        when:
        settingsFile << """
            rootProject.name = 'app'
            include 'one', 'two', 'three'
        """

        and:
        run ":visualStudio"

        then:
        result.assertTasksExecuted(":visualStudio", ":appVisualStudioSolution")

        and:
        final mainSolution = solutionFile("app.sln")
        mainSolution.assertHasProjects()
    }

    @ToBeFixedForConfigurationCache
    def "includes a visual studio project for every project with a C++ component"() {
        when:
        settingsFile << """
            rootProject.name = 'app'
            include 'one', 'two', 'three'
        """
        buildFile << """
            apply plugin: 'cpp-application'

            project(':one') {
                apply plugin: 'cpp-application'
            }
            project(':two') {
                apply plugin: 'cpp-library'
            }
        """

        and:
        run ":visualStudio"

        then:
        result.assertTasksExecuted(":appVisualStudioSolution",
            ":appVisualStudioFilters", ":appVisualStudioProject",
            ":one:oneVisualStudioFilters", ":one:oneVisualStudioProject",
            ":two:twoDllVisualStudioFilters", ":two:twoDllVisualStudioProject",
            ":visualStudio")

        and:
        def appProject = projectFile("app.vcxproj")
        def oneProject = projectFile("one/one.vcxproj")
        def twoProject = projectFile("two/twoDll.vcxproj")

        final mainSolution = solutionFile("app.sln")
        mainSolution.assertHasProjects("app", "one", "twoDll")
        mainSolution.assertReferencesProject(appProject, projectConfigurations)
        mainSolution.assertReferencesProject(oneProject, projectConfigurations)
        mainSolution.assertReferencesProject(twoProject, projectConfigurations)
    }

    @ToBeFixedForConfigurationCache
    def "create visual studio solution for executable that depends on a library in another project"() {
        when:
        app.executable.writeSources(file("exe/src/main"))
        app.library.writeSources(file("lib/src/main"))

        settingsFile << """
            include ':exe', ':lib'
        """
        file("exe", "build.gradle") << """
            apply plugin: 'cpp-application'

            dependencies {
                implementation project(':lib')
            }
        """
        file("lib", "build.gradle") << """
            apply plugin: 'cpp-library'
        """

        and:
        run ":visualStudio"

        then:
        result.assertTasksExecuted(":appVisualStudioSolution",
            ":exe:exeVisualStudioFilters", ":exe:exeVisualStudioProject",
            ":lib:libDllVisualStudioFilters", ":lib:libDllVisualStudioProject",
            ":visualStudio")

        and:
        final exeProject = projectFile("exe/exe.vcxproj")
        exeProject.assertHasComponentSources(app.executable, "src/main")
        exeProject.projectConfigurations.keySet() == projectConfigurations
        exeProject.projectConfigurations.values().each {
            assert it.includePath == filePath("src/main/headers", "../lib/src/main/public")
            assert it.buildCommand.endsWith("gradle\" -p \"..\" :exe:install${it.name.capitalize()}")
        }

        and:
        final dllProject = projectFile("lib/libDll.vcxproj")
        dllProject.assertHasComponentSources(app.library, "src/main")
        dllProject.projectConfigurations.keySet() == projectConfigurations
        dllProject.projectConfigurations.values().each {
            assert it.includePath == filePath("src/main/public", "src/main/headers")
            assert it.buildCommand.endsWith("gradle\" -p \"..\" :lib:link${it.name.capitalize()}")
        }

        and:
        final mainSolution = solutionFile("app.sln")
        mainSolution.assertHasProjects("exe", "libDll")
        mainSolution.assertReferencesProject(exeProject, projectConfigurations)
        mainSolution.assertReferencesProject(dllProject, projectConfigurations)
    }

    @ToBeFixedForConfigurationCache
    def "visual studio solution does not reference the components of a project if it does not have visual studio plugin applied"() {
        when:
        app.executable.writeSources(file("exe/src/main"))
        app.library.writeSources(file("lib/src/main"))
        app.library.writeSources(file("other/src/main"))

        settingsFile << """
            include ':exe', ':lib', ':other'
        """
        buildFile.text = """
            allprojects {
                if (name != 'other') {
                    apply plugin: 'visual-studio'
                }
            }
        """
        file("exe", "build.gradle") << """
            apply plugin: 'cpp-application'

            dependencies {
                implementation project(':lib')
            }
        """
        file("lib", "build.gradle") << """
            apply plugin: 'cpp-library'
        """
        file("other", "build.gradle") << """
            apply plugin: 'cpp-library'
        """

        and:
        run ":visualStudio"

        then:
        result.assertTasksExecuted(":appVisualStudioSolution",
            ":exe:exeVisualStudioFilters", ":exe:exeVisualStudioProject",
            ":lib:libDllVisualStudioFilters", ":lib:libDllVisualStudioProject",
            ":visualStudio")

        and:
        final exeProject = projectFile("exe/exe.vcxproj")
        exeProject.assertHasComponentSources(app.executable, "src/main")
        exeProject.projectConfigurations.keySet() == projectConfigurations
        exeProject.projectConfigurations.values().each {
            assert it.includePath == filePath("src/main/headers", "../lib/src/main/public")
            assert it.buildCommand.endsWith("gradle\" -p \"..\" :exe:install${it.name.capitalize()}")
        }

        and:
        final dllProject = projectFile("lib/libDll.vcxproj")
        dllProject.assertHasComponentSources(app.library, "src/main")
        dllProject.projectConfigurations.keySet() == projectConfigurations
        dllProject.projectConfigurations.values().each {
            assert it.includePath == filePath("src/main/public", "src/main/headers")
            assert it.buildCommand.endsWith("gradle\" -p \"..\" :lib:link${it.name.capitalize()}")
        }

        and:
        final mainSolution = solutionFile("app.sln")
        mainSolution.assertHasProjects("exe", "libDll")
        mainSolution.assertReferencesProject(exeProject, projectConfigurations)
        mainSolution.assertReferencesProject(dllProject, projectConfigurations)

        and:
        file("other").listFiles().every { !(it.name.endsWith(".vcxproj") || it.name.endsWith(".vcxproj.filters")) }
    }

    @ToBeFixedForConfigurationCache
    def "create visual studio solution for executable that transitively depends on multiple projects"() {
        given:
        def app = new ExeWithLibraryUsingLibraryHelloWorldApp()
        app.writeSources(file("exe/src/main"), file("lib/src/main"), file("greet/src/main"))

        and:
        settingsFile << """
            include ':exe', ':lib', ':greet'
        """
        buildFile << """
            project(":exe") {
                apply plugin: "cpp-application"

                dependencies {
                    implementation project(':lib')
                }
            }
            project(":lib") {
                apply plugin: "cpp-library"

                dependencies {
                    implementation project(':greet')
                }
            }
            project(":greet") {
                apply plugin: "cpp-library"

                library {
                    linkage = [Linkage.STATIC]
                }
            }
        """

        when:
        succeeds ":visualStudio"

        then:
        result.assertTasksExecuted(":appVisualStudioSolution",
            ":exe:exeVisualStudioFilters", ":exe:exeVisualStudioProject",
            ":greet:greetLibVisualStudioFilters", ":greet:greetLibVisualStudioProject",
            ":lib:libDllVisualStudioFilters", ":lib:libDllVisualStudioProject",
            ":visualStudio")

        and:
        final exeProject = projectFile("exe/exe.vcxproj")
        final helloDllProject = projectFile("lib/libDll.vcxproj")
        final greetLibProject = projectFile("greet/greetLib.vcxproj")
        final mainSolution = solutionFile("app.sln")

        and:
        mainSolution.assertHasProjects("exe", "libDll", "greetLib")
        mainSolution.assertReferencesProject(exeProject, projectConfigurations)
        mainSolution.assertReferencesProject(helloDllProject, projectConfigurations)
        mainSolution.assertReferencesProject(greetLibProject, projectConfigurations)

        and:
        exeProject.projectConfigurations['debug'].includePath == filePath("src/main/headers", "../lib/src/main/public")
        helloDllProject.projectConfigurations['debug'].includePath == filePath("src/main/public", "src/main/headers", "../greet/src/main/public")
        greetLibProject.projectConfigurations['debug'].includePath == filePath("src/main/public", "src/main/headers")
    }

    @ToBeFixedForConfigurationCache
    def "create visual studio solution for executable with a transitive api dependency"() {
        given:
        def app = new ExeWithLibraryUsingLibraryHelloWorldApp()
        app.writeSources(file("exe/src/main"), file("lib/src/main"), file("greet/src/main"))

        and:
        settingsFile << """
            include ':exe', ':lib', ':greet'
        """
        buildFile << """
            project(":exe") {
                apply plugin: "cpp-application"

                dependencies {
                    implementation project(':lib')
                }
            }
            project(":lib") {
                apply plugin: "cpp-library"

                dependencies {
                    api project(':greet')
                }
            }
            project(":greet") {
                apply plugin: "cpp-library"

                library {
                    linkage = [Linkage.STATIC]
                }
            }
        """

        when:
        succeeds ":visualStudio"

        then:
        result.assertTasksExecuted(":appVisualStudioSolution",
            ":exe:exeVisualStudioFilters", ":exe:exeVisualStudioProject",
            ":greet:greetLibVisualStudioFilters", ":greet:greetLibVisualStudioProject",
            ":lib:libDllVisualStudioFilters", ":lib:libDllVisualStudioProject",
            ":visualStudio")

        and:
        final exeProject = projectFile("exe/exe.vcxproj")
        final helloDllProject = projectFile("lib/libDll.vcxproj")
        final greetLibProject = projectFile("greet/greetLib.vcxproj")
        final mainSolution = solutionFile("app.sln")

        and:
        mainSolution.assertHasProjects("exe", "libDll", "greetLib")
        mainSolution.assertReferencesProject(exeProject, projectConfigurations)
        mainSolution.assertReferencesProject(helloDllProject, projectConfigurations)
        mainSolution.assertReferencesProject(greetLibProject, projectConfigurations)

        and:
        exeProject.projectConfigurations['debug'].includePath == filePath("src/main/headers", "../lib/src/main/public", "../greet/src/main/public")
        helloDllProject.projectConfigurations['debug'].includePath == filePath("src/main/public", "src/main/headers", "../greet/src/main/public")
        greetLibProject.projectConfigurations['debug'].includePath == filePath("src/main/public", "src/main/headers")
    }

    @Requires(UnitTestPreconditions.HasMsBuild)
    def "can build executable that depends on static library in another project from visual studio"() {
        useMsbuildTool()
        def app = new CppAppWithLibrary()

        given:
        app.greeter.writeToProject(file("lib"))
        app.main.writeToProject(file("exe"))

        settingsFile << """
            include ':exe', ':lib'
        """
        file("exe", "build.gradle") << """
            apply plugin: 'cpp-application'

            dependencies {
                implementation project(':lib')
            }
        """
        file("lib", "build.gradle") << """
            apply plugin: 'cpp-library'

            library {
                linkage = [Linkage.STATIC]
            }
        """
        succeeds ":visualStudio"

        when:
        def resultDebug = msbuild
            .withSolution(solutionFile('app.sln'))
            .withConfiguration('debug')
            .withProject("exe")
            .succeeds()

        then:
        resultDebug.size() == 1
        resultDebug[0].assertTasksExecuted(':exe:compileDebugCpp', ':exe:linkDebug', ':exe:installDebug', ':lib:compileDebugCpp', ':lib:createDebug')
        installation('exe/build/install/main/debug').assertInstalled()
    }

    @Requires(UnitTestPreconditions.HasMsBuild)
    def "skip unbuildable static library project when building solution from visual studio"() {
        useMsbuildTool()
        def app = new CppAppWithLibrary()

        given:
        app.greeter.writeToProject(file("lib"))
        app.main.writeToProject(file("exe"))

        settingsFile << """
            include ':exe', ':lib'
        """
        file("exe", "build.gradle") << """
            apply plugin: 'cpp-application'

            dependencies {
                implementation project(':lib')
            }
        """
        file("lib", "build.gradle") << """
            apply plugin: 'cpp-library'

            library {
                linkage = [Linkage.STATIC]
                targetMachines = [machines.os('os-family')]
            }
        """
        succeeds ":visualStudio"

        when:
        def resultUnbuildableSolution = msbuild
                .withSolution(solutionFile('app.sln'))
                .withConfiguration('unbuildable')
                .succeeds()

        then:
        resultUnbuildableSolution.size() == 1
        resultUnbuildableSolution[0].assertTasksExecuted()
        resultUnbuildableSolution[0].assertOutputContains('The project "exe" is not selected for building in solution configuration "unbuildable|Win32".')
        resultUnbuildableSolution[0].assertOutputContains('The project "libLib" is not selected for building in solution configuration "unbuildable|Win32".')
        installation('exe/build/install/main/debug').assertNotInstalled()

        when:
        def resultDebug = msbuild
                .withSolution(solutionFile('app.sln'))
                .withConfiguration('debug')
                .fails()

        then:
        resultDebug.assertTasksExecuted()
        resultDebug.assertHasCause("Could not resolve all task dependencies for configuration ':exe:nativeRuntimeDebug'.")
        resultDebug.assertHasCause("Could not resolve project :lib.")
        installation('exe/build/install/main/debug').assertNotInstalled()
    }

    @NotYetImplemented
    def "create visual studio solution where multiple projects have same name"() {
        given:
        def app = new ExeWithLibraryUsingLibraryHelloWorldApp()
        app.writeSources(file("exe/src/main"), file("some/lib/src/main"), file("other/lib/src/main"))

        and:
        settingsFile << """
            include ':exe', ':some:lib', ':other:lib'
        """
        buildFile << """
            project(":exe") {
                apply plugin: "cpp-application"

                dependencies {
                    implementation project(':some:lib')
                }
            }
            project(":some:lib") {
                apply plugin: "cpp-library"

                dependencies {
                    implementation project(':other:lib')
                }
            }
            project(":other:lib") {
                apply plugin: "cpp-library"
            }
        """

        when:
        succeeds ":visualStudio"

        then:
        final exeProject = projectFile("exe/exe.vcxproj")
        final someLibProject = projectFile("some/lib/libDll.vcxproj")
        final otherLibProject = projectFile("other/lib/libDll.vcxproj")
        final mainSolution = solutionFile("app.sln")

        and:
        mainSolution.assertHasProjects("exe", "libDll", "libDll")
        mainSolution.assertReferencesProject(exeProject, projectConfigurations)
        mainSolution.assertReferencesProject(someLibProject, projectConfigurations)
        mainSolution.assertReferencesProject(otherLibProject, projectConfigurations)

        and:
        exeProject.projectConfigurations['debug'].includePath == filePath("src/main/headers", "../some/lib/src/main/public")
        someLibProject.projectConfigurations['debug'].includePath == filePath("src/main/public", "src/main/headers", "../other/lib/src/main/public")
        otherLibProject.projectConfigurations['debug'].includePath == filePath("src/main/public", "src/main/headers")
    }
}
