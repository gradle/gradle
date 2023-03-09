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

import org.apache.commons.io.FilenameUtils
import org.gradle.api.Project
import org.gradle.ide.visualstudio.fixtures.AbstractVisualStudioIntegrationSpec
import org.gradle.ide.visualstudio.fixtures.MSBuildExecutor
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.nativeplatform.fixtures.app.CppHelloWorldApp
import org.gradle.nativeplatform.fixtures.app.ExeWithLibraryUsingLibraryHelloWorldApp
import org.gradle.plugins.ide.internal.IdePlugin
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions
import spock.lang.IgnoreIf

class VisualStudioSoftwareModelMultiProjectIntegrationTest extends AbstractVisualStudioIntegrationSpec {
    Set<String> projectConfigurations = ['debug', 'release'] as Set

    def app = new CppHelloWorldApp()

    def setup() {
        settingsFile << """
            rootProject.name = 'app'
        """
        buildFile << """
            allprojects {
                apply plugin: 'visual-studio'
            }
            subprojects {
                apply plugin: 'cpp'

                model {
                    buildTypes {
                        debug
                        release
                    }
                }
            }
        """
    }

    @ToBeFixedForConfigurationCache
    def "create visual studio solution for executable that depends on a library in another project"() {
        when:
        app.executable.writeSources(file("exe/src/main"))
        app.library.writeSources(file("lib/src/hello"))

        settingsFile << """
            include ':exe', ':lib'
        """
        file("exe", "build.gradle") << """
            model {
                components {
                    main(NativeExecutableSpec) {
                        sources {
                            cpp.lib project: ':lib', library: 'hello', linkage: 'static'
                        }
                    }
                }
            }
        """
        file("lib", "build.gradle") << """
            model {
                components {
                    hello(NativeLibrarySpec)
                }
            }
        """
        and:
        run ":visualStudio"

        then:
        final exeProject = projectFile("exe/exe_mainExe.vcxproj")
        exeProject.assertHasComponentSources(app.executable, "src/main")
        exeProject.projectConfigurations.keySet() == projectConfigurations
        exeProject.projectConfigurations.values().each {
            assert it.includePath == filePath("src/main/headers", "../lib/src/hello/headers")
            assert it.buildCommand.endsWith("gradle\" -p \"..\" :exe:installMain${it.name.capitalize()}Executable")
        }

        and:
        final dllProject = projectFile("lib/lib_helloDll.vcxproj")
        dllProject.assertHasComponentSources(app.library, "src/hello")
        dllProject.projectConfigurations.keySet() == projectConfigurations
        dllProject.projectConfigurations.values().each {
            assert it.includePath == filePath("src/hello/headers")
            assert it.buildCommand.endsWith("gradle\" -p \"..\" :lib:hello${it.name.capitalize()}SharedLibrary")
        }

        and:
        final libProject = projectFile("lib/lib_helloLib.vcxproj")
        libProject.assertHasComponentSources(app.library, "src/hello")
        libProject.projectConfigurations.keySet() == projectConfigurations
        libProject.projectConfigurations.values().each {
            assert it.includePath == filePath("src/hello/headers")
            assert it.buildCommand.endsWith("gradle\" -p \"..\" :lib:hello${it.name.capitalize()}StaticLibrary")
        }

        and:
        final mainSolution = solutionFile("app.sln")
        mainSolution.assertHasProjects("exe_mainExe", "lib_helloDll", "lib_helloLib")
        mainSolution.assertReferencesProject(exeProject, projectConfigurations)
        mainSolution.assertReferencesProject(dllProject, projectConfigurations)
        mainSolution.assertReferencesProject(libProject, projectConfigurations)
    }

    @ToBeFixedForConfigurationCache
    def "visual studio solution does not reference the components of a project if it does not have visual studio plugin applied"() {
        when:
        app.executable.writeSources(file("exe/src/main"))
        app.library.writeSources(file("lib/src/hello"))
        app.library.writeSources(file("other/src/greeting"))

        settingsFile << """
            include ':exe', ':lib', ':other'
        """
        buildFile.text = """
            allprojects {
                if (name != 'other') {
                    apply plugin: 'visual-studio'
                }
            }
            subprojects {
                apply plugin: 'cpp'

                model {
                    platforms {
                        win32 {
                            architecture "i386"
                        }
                    }
                    buildTypes {
                        debug
                        release
                    }
                }
            }
        """

        file("exe", "build.gradle") << """
            model {
                components {
                    main(NativeExecutableSpec) {
                        sources {
                            cpp.lib project: ':lib', library: 'hello', linkage: 'static'
                        }
                    }
                }
            }
        """
        file("lib", "build.gradle") << """
            model {
                components {
                    hello(NativeLibrarySpec)
                }
            }
        """
        file("other", "build.gradle") << """
            apply plugin: 'cpp'

            model {
                components {
                    greeting(NativeLibrarySpec)
                }
            }
        """
        and:
        run ":visualStudio"

        then:
        final exeProject = projectFile("exe/exe_mainExe.vcxproj")
        exeProject.assertHasComponentSources(app.executable, "src/main")
        exeProject.projectConfigurations.keySet() == projectConfigurations
        exeProject.projectConfigurations.values().each {
            assert it.includePath == filePath("src/main/headers", "../lib/src/hello/headers")
            assert it.buildCommand.endsWith("gradle\" -p \"..\" :exe:installMain${it.name.capitalize()}Executable")
        }

        and:
        final dllProject = projectFile("lib/lib_helloDll.vcxproj")
        dllProject.assertHasComponentSources(app.library, "src/hello")
        dllProject.projectConfigurations.keySet() == projectConfigurations
        dllProject.projectConfigurations.values().each {
            assert it.includePath == filePath("src/hello/headers")
            assert it.buildCommand.endsWith("gradle\" -p \"..\" :lib:hello${it.name.capitalize()}SharedLibrary")
        }

        and:
        final libProject = projectFile("lib/lib_helloLib.vcxproj")
        libProject.assertHasComponentSources(app.library, "src/hello")
        libProject.projectConfigurations.keySet() == projectConfigurations
        libProject.projectConfigurations.values().each {
            assert it.includePath == filePath("src/hello/headers")
            assert it.buildCommand.endsWith("gradle\" -p \"..\" :lib:hello${it.name.capitalize()}StaticLibrary")
        }

        and:
        final mainSolution = solutionFile("app.sln")
        mainSolution.assertHasProjects("exe_mainExe", "lib_helloDll", "lib_helloLib")
        mainSolution.assertReferencesProject(exeProject, projectConfigurations)
        mainSolution.assertReferencesProject(dllProject, projectConfigurations)
        mainSolution.assertReferencesProject(libProject, projectConfigurations)

        and:
        file("other").listFiles().every { !(it.name.endsWith(".vcxproj") || it.name.endsWith(".vcxproj.filters")) }
    }

    @ToBeFixedForConfigurationCache
    def "create visual studio solution for executable that transitively depends on multiple projects"() {
        given:
        def app = new ExeWithLibraryUsingLibraryHelloWorldApp()
        app.writeSources(file("exe/src/main"), file("lib/src/hello"), file("greet/src/greetings"))

        and:
        settingsFile << """
            include ':exe', ':lib', ':greet'
        """
        buildFile << """
            project(":exe") {
                apply plugin: "cpp"
                model {
                    components {
                        main(NativeExecutableSpec) {
                            sources {
                                cpp.lib project: ':lib', library: 'hello'
                            }
                        }
                    }
                }
            }
            project(":lib") {
                apply plugin: "cpp"
                model {
                    components {
                        hello(NativeLibrarySpec) {
                            sources {
                                cpp.lib project: ':greet', library: 'greetings', linkage: 'static'
                            }
                        }
                    }
                }
            }
            project(":greet") {
                apply plugin: "cpp"
                model {
                    components {
                        greetings(NativeLibrarySpec)
                    }
                }
            }
        """

        when:
        succeeds ":visualStudio"

        then:
        final exeProject = projectFile("exe/exe_mainExe.vcxproj")
        final helloDllProject = projectFile("lib/lib_helloDll.vcxproj")
        final helloLibProject = projectFile("lib/lib_helloLib.vcxproj")
        final greetDllProject = projectFile("greet/greet_greetingsDll.vcxproj")
        final greetLibProject = projectFile("greet/greet_greetingsLib.vcxproj")
        final mainSolution = solutionFile("app.sln")

        and:
        mainSolution.assertHasProjects("exe_mainExe", "lib_helloDll", "lib_helloLib", "greet_greetingsDll", "greet_greetingsLib")
        mainSolution.assertReferencesProject(exeProject, projectConfigurations)
        mainSolution.assertReferencesProject(helloDllProject, projectConfigurations)
        mainSolution.assertReferencesProject(helloLibProject, projectConfigurations)
        mainSolution.assertReferencesProject(greetDllProject, projectConfigurations)
        mainSolution.assertReferencesProject(greetLibProject, projectConfigurations)

        and:
        exeProject.projectConfigurations['debug'].includePath == filePath("src/main/headers", "../lib/src/hello/headers")
        helloDllProject.projectConfigurations['debug'].includePath == filePath("src/hello/headers", "../greet/src/greetings/headers")
        helloLibProject.projectConfigurations['debug'].includePath == filePath("src/hello/headers", "../greet/src/greetings/headers")
        greetDllProject.projectConfigurations['debug'].includePath == filePath("src/greetings/headers")
        greetLibProject.projectConfigurations['debug'].includePath == filePath("src/greetings/headers")
    }

    @Requires(UnitTestPreconditions.HasMsBuild)
    @ToBeFixedForConfigurationCache
    def "can build executable that depends on static library in another project from visual studio"() {
        useMsbuildTool()

        given:
        app.executable.writeSources(file("exe/src/main"))
        app.library.writeSources(file("lib/src/hello"))

        settingsFile << """
            include ':exe', ':lib'
        """
        file("exe", "build.gradle") << """
            model {
                components {
                    main(NativeExecutableSpec) {
                        sources {
                            cpp.lib project: ':lib', library: 'hello', linkage: 'static'
                        }
                    }
                }
            }
        """
        file("lib", "build.gradle") << """
            model {
                components {
                    hello(NativeLibrarySpec)
                }
            }
        """
        succeeds ":visualStudio"

        when:
        def resultDebug = msbuild
            .withSolution(solutionFile('app.sln'))
            .withConfiguration('debug')
            .withProject("exe_mainExe")
            .succeeds()

        then:
        resultDebug.size() == 1
        resultDebug[0].assertTasksExecuted(':exe:compileMainDebugExecutableMainCpp', ':exe:linkMainDebugExecutable', ':exe:mainDebugExecutable', ':exe:installMainDebugExecutable', ':lib:compileHelloDebugStaticLibraryHelloCpp', ':lib:createHelloDebugStaticLibrary', ':lib:helloDebugStaticLibrary')
        installation('exe/build/install/main/debug').assertInstalled()
    }

    @Requires(UnitTestPreconditions.HasMsBuild)
    @ToBeFixedForConfigurationCache
    def "can clean from visual studio with dependencies"() {
        useMsbuildTool()
        def debugBinary = executable('exe/build/exe/main/debug/main')

        given:
        app.executable.writeSources(file("exe/src/main"))
        app.library.writeSources(file("lib/src/hello"))
        settingsFile << """
            include ':exe', ':lib'
        """
        file("exe", "build.gradle") << """
            model {
                components {
                    main(NativeExecutableSpec) {
                        sources {
                            cpp.lib project: ':lib', library: 'hello', linkage: 'static'
                        }
                    }
                }
            }
        """
        file("lib", "build.gradle") << """
            model {
                components {
                    hello(NativeLibrarySpec)
                }
            }
        """
        succeeds ":visualStudio"

        when:
        debugBinary.assertDoesNotExist()
        msbuild
            .withSolution(solutionFile("app.sln"))
            .withConfiguration('debug')
            .succeeds()

        then:
        debugBinary.exec().out == app.englishOutput

        when:
        msbuild
            .withSolution(solutionFile("app.sln"))
            .withConfiguration('debug')
            .succeeds(MSBuildExecutor.MSBuildAction.CLEAN)

        then:
        file("exe/build").assertDoesNotExist()
        file("lib/build").assertDoesNotExist()
    }

    @ToBeFixedForConfigurationCache
    def "create visual studio solution where multiple components have same name"() {
        given:
        def app = new ExeWithLibraryUsingLibraryHelloWorldApp()
        app.writeSources(file("exe/src/main"), file("lib/src/main"), file("greet/src/main"))

        and:
        settingsFile << """
            include ':exe', ':lib', ':greet'
        """
        buildFile << """
            project(":exe") {
                apply plugin: "cpp"
                model {
                    components {
                        main(NativeExecutableSpec) {
                            sources {
                                cpp.lib project: ':lib', library: 'main'
                            }
                        }
                    }
                }
            }
            project(":lib") {
                apply plugin: "cpp"
                model {
                    components {
                        main(NativeLibrarySpec) {
                            sources {
                                cpp.lib project: ':greet', library: 'main', linkage: 'static'
                            }
                        }
                    }
                }
            }
            project(":greet") {
                apply plugin: "cpp"
                model {
                    components {
                        main(NativeLibrarySpec)
                    }
                }
            }
        """

        when:
        succeeds ":visualStudio"

        then:
        final exeProject = projectFile("exe/exe_mainExe.vcxproj")
        final helloDllProject = projectFile("lib/lib_mainDll.vcxproj")
        final helloLibProject = projectFile("lib/lib_mainLib.vcxproj")
        final greetDllProject = projectFile("greet/greet_mainDll.vcxproj")
        final greetLibProject = projectFile("greet/greet_mainLib.vcxproj")
        final mainSolution = solutionFile("app.sln")

        and:
        mainSolution.assertHasProjects("exe_mainExe", "lib_mainDll", "lib_mainLib", "greet_mainDll", "greet_mainLib")
        mainSolution.assertReferencesProject(exeProject, projectConfigurations)
        mainSolution.assertReferencesProject(helloDllProject, projectConfigurations)
        mainSolution.assertReferencesProject(helloLibProject, projectConfigurations)
        mainSolution.assertReferencesProject(greetDllProject, projectConfigurations)
        mainSolution.assertReferencesProject(greetLibProject, projectConfigurations)

        and:
        exeProject.projectConfigurations['debug'].includePath == filePath("src/main/headers", "../lib/src/main/headers")
        helloDllProject.projectConfigurations['debug'].includePath == filePath("src/main/headers", "../greet/src/main/headers")
        helloLibProject.projectConfigurations['debug'].includePath == filePath("src/main/headers", "../greet/src/main/headers")
        greetDllProject.projectConfigurations['debug'].includePath == filePath("src/main/headers")
        greetLibProject.projectConfigurations['debug'].includePath == filePath("src/main/headers")
    }

    @ToBeFixedForConfigurationCache
    def "create visual studio solution for executable with project dependency cycle"() {
        given:
        def app = new ExeWithLibraryUsingLibraryHelloWorldApp()
        app.writeSources(file("exe/src/main"), file("lib/src/hello"), file("exe/src/greetings"))

        and:
        settingsFile << """
            include ':exe', ':lib'
        """
        buildFile << """
            project(":exe") {
                apply plugin: "cpp"
                model {
                    components {
                        main(NativeExecutableSpec) {
                            sources {
                                cpp.lib project: ':lib', library: 'hello'
                            }
                        }
                        greetings(NativeLibrarySpec)
                    }
                }
            }
            project(":lib") {
                apply plugin: "cpp"
                model {
                    components {
                        hello(NativeLibrarySpec) {
                            sources {
                                cpp.lib project: ':exe', library: 'greetings', linkage: 'static'
                            }
                        }
                    }
                }
            }
        """

        when:
        succeeds ":visualStudio"

        then:
        final exeProject = projectFile("exe/exe_mainExe.vcxproj")
        final helloDllProject = projectFile("lib/lib_helloDll.vcxproj")
        final helloLibProject = projectFile("lib/lib_helloLib.vcxproj")
        final greetDllProject = projectFile("exe/exe_greetingsDll.vcxproj")
        final greetLibProject = projectFile("exe/exe_greetingsLib.vcxproj")
        final mainSolution = solutionFile("app.sln")

        and:
        mainSolution.assertHasProjects("exe_mainExe", "lib_helloDll", "lib_helloLib", "exe_greetingsDll", "exe_greetingsLib")
        mainSolution.assertReferencesProject(exeProject, projectConfigurations)
        mainSolution.assertReferencesProject(helloDllProject, projectConfigurations)
        mainSolution.assertReferencesProject(helloLibProject, projectConfigurations)
        mainSolution.assertReferencesProject(greetDllProject, projectConfigurations)
        mainSolution.assertReferencesProject(greetLibProject, projectConfigurations)

        and:
        exeProject.projectConfigurations['debug'].includePath == filePath("src/main/headers", "../lib/src/hello/headers")
        helloDllProject.projectConfigurations['debug'].includePath == filePath("src/hello/headers", "../exe/src/greetings/headers")
        helloLibProject.projectConfigurations['debug'].includePath == filePath("src/hello/headers", "../exe/src/greetings/headers")
        greetDllProject.projectConfigurations['debug'].includePath == filePath("src/greetings/headers")
        greetLibProject.projectConfigurations['debug'].includePath == filePath("src/greetings/headers")
    }

    /** @see IdePlugin#toGradleCommand(Project) */
    @IgnoreIf({GradleContextualExecuter.daemon || GradleContextualExecuter.noDaemon})
    @ToBeFixedForConfigurationCache
    def "detects gradle wrapper and uses in vs project"() {
        when:
        hostGradleWrapperFile << "dummy wrapper"

        settingsFile << """
            include ':exe'
        """
        buildFile << """
            project(':exe') {
                model {
                    components {
                        main(NativeExecutableSpec)
                    }
                }
            }
        """
        and:
        run ":visualStudio"

        then:
        final exeProject = projectFile("exe/exe_mainExe.vcxproj")
        exeProject.projectConfigurations.values().each {
            assert it.buildCommand == "\"../${hostGradleWrapperFile.name}\" -p \"..\" :exe:installMain${it.name.capitalize()}Executable"
        }
    }

    /** @see IdePlugin#toGradleCommand(Project) */
    @IgnoreIf({!(GradleContextualExecuter.daemon || GradleContextualExecuter.noDaemon)})
    @ToBeFixedForConfigurationCache
    def "detects executing gradle distribution and uses in vs project"() {
        when:
        hostGradleWrapperFile << "dummy wrapper"

        settingsFile << """
            include ':exe'
        """
        buildFile << """
            project(':exe') {
                model {
                    components {
                        main(NativeExecutableSpec)
                    }
                }
            }
        """
        and:
        run ":visualStudio"

        then:
        final exeProject = projectFile("exe/exe_mainExe.vcxproj")
        exeProject.projectConfigurations.values().each {
            assert it.buildCommand == "\"${FilenameUtils.separatorsToUnix(executer.distribution.gradleHomeDir.file('bin/gradle').absolutePath)}\" -p \"..\" :exe:installMain${it.name.capitalize()}Executable"
        }
    }

    @ToBeFixedForConfigurationCache
    def "cleanVisualStudio removes all generated visual studio files"() {
        when:
        settingsFile << """
            include ':exe', ':lib'
        """
        buildFile << """
            project(':exe') {
                model {
                    components {
                        main(NativeExecutableSpec) {
                            sources {
                                cpp.lib project: ':lib', library: 'main', linkage: 'static'
                            }
                        }
                    }
                }
            }
            project(':lib') {
                model {
                    components {
                        main(NativeLibrarySpec)
                    }
                }
            }
        """
        and:
        run "visualStudio"

        then:
        def generatedFiles = [
                file("app.sln"),
                file("exe/exe_mainExe.vcxproj"),
                file("exe/exe_mainExe.vcxproj.filters"),
                file("lib/lib_mainDll.vcxproj"),
                file("lib/lib_mainDll.vcxproj.filters"),
                file("lib/lib_mainDll.vcxproj"),
                file("lib/lib_mainDll.vcxproj.filters")
        ]
        generatedFiles*.assertExists()

        when:
        run "cleanVisualStudio"

        then:
        generatedFiles*.assertDoesNotExist()
    }

    @ToBeFixedForConfigurationCache
    @IgnoreIf({ GradleContextualExecuter.isParallel() })
    def "can create Visual Studio solution for multiproject depending on the same prebuilt binary from another project in parallel"() {
        given:
        settingsFile.text = """
            rootProject.name = 'root'
            include ':projectA', ':projectB', ':library'
        """
        buildFile << """
            allprojects {
                apply plugin: 'visual-studio'
            }
        """
        file("projectA/build.gradle") << """
            apply plugin: 'cpp'
            model {
                components {
                    main(NativeExecutableSpec) {
                        sources {
                            cpp.lib project: ':library', library: 'hello', linkage: 'api'
                        }
                    }
                }
            }
        """

        file("projectB/build.gradle") << """
            apply plugin: 'cpp'
            model {
                components {
                    main(NativeExecutableSpec) {
                        sources {
                            cpp.lib project: ':library', library: 'hello', linkage: 'api'
                        }
                    }
                }
            }
        """

        file("library/build.gradle") << """
            apply plugin: 'cpp'
            model {
                repositories {
                    libs(PrebuiltLibraries) {
                        hello {
                            headers.srcDir "libs/src/hello/headers"
                        }
                    }
                }
            }
        """

        when:
        succeeds '--parallel', "visualStudio"

        then:
        executedAndNotSkipped(':rootVisualStudioSolution', ':visualStudio', ':projectB:projectB_mainExeVisualStudioFilters', ':projectB:projectB_mainExeVisualStudioProject', ':projectB:visualStudio', ':projectA:projectA_mainExeVisualStudioFilters', ':projectA:projectA_mainExeVisualStudioProject', ':projectA:visualStudio')
    }
}
