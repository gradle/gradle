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
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.nativeplatform.fixtures.app.CppHelloWorldApp

import static org.apache.commons.io.FileUtils.copyFile
import static org.gradle.nativeplatform.fixtures.ToolChainRequirement.WINDOWS_GCC
import static org.junit.Assume.assumeFalse

class VisualStudioSoftwareModelIncrementalIntegrationTest extends AbstractVisualStudioIntegrationSpec {
    def app = new CppHelloWorldApp()

    def setup() {
        settingsFile << """
            rootProject.name = 'app'
        """
        buildFile << """
            apply plugin: 'cpp'
            apply plugin: 'visual-studio'

            model {
                components {
                    main(NativeExecutableSpec)
                }
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

    @ToBeFixedForConfigurationCache
    def "visual studio tasks re-execute when source files are added"() {
        app.writeSources(file("src/main"))

        when:
        run "visualStudio"

        then:
        executedAndNotSkipped ":appVisualStudioSolution"
        executedAndNotSkipped getComponentTasks("main")

        when:
        copyFile(file("src/main/cpp/main.cpp"), file("src/main/cpp/foo.cpp").assertDoesNotExist())
        run "visualStudio"

        then:
        skipped ":appVisualStudioSolution"
        executedAndNotSkipped getComponentTasks("main")

        and:
        final projectFile = projectFile("mainExe.vcxproj")
        assert projectFile.sourceFiles.contains('src/main/cpp/foo.cpp')

        when:
        run "visualStudio"

        then:
        skipped ":appVisualStudioSolution"
        skipped getComponentTasks("main")
    }

    @ToBeFixedForConfigurationCache
    def "visual studio tasks re-execute when header files are added"() {
        app.writeSources(file("src/main"))

        when:
        run "visualStudio"

        then:
        executedAndNotSkipped ":appVisualStudioSolution"
        executedAndNotSkipped getComponentTasks("main")

        when:
        copyFile(file("src/main/headers/hello.h"), file("src/main/headers/foo.h").assertDoesNotExist())
        run "visualStudio"

        then:
        skipped ":appVisualStudioSolution"
        executedAndNotSkipped getComponentTasks("main")

        and:
        final projectFile = projectFile("mainExe.vcxproj")
        assert projectFile.headerFiles.contains('src/main/headers/foo.h')

        when:
        run "visualStudio"

        then:
        skipped ":appVisualStudioSolution"
        skipped getComponentTasks("main")
    }

    @ToBeFixedForConfigurationCache
    def "visual studio tasks re-execute when output file locations change"() {
        assumeFalse(toolChain.meets(WINDOWS_GCC))
        app.writeSources(file("src/main"))

        when:
        run "visualStudio"

        then:
        executedAndNotSkipped ":appVisualStudioSolution"
        executedAndNotSkipped getComponentTasks("main")

        when:
        buildFile << """
            model {
                visualStudio {
                    solution {
                        solutionFile.location = file('foo.sln')
                    }
                }
            }
        """
        run "visualStudio"

        then:
        executedAndNotSkipped ":appVisualStudioSolution"
        skipped getComponentTasks("main")

        and:
        final solutionFile = solutionFile("foo.sln")
        solutionFile.assertReferencesProject(projectFile("mainExe.vcxproj"), ["win32Debug", "win32Release", "x64Debug", "x64Release"])

        when:
        buildFile << """
            model {
                visualStudio {
                    projects.all {
                        projectFile.location = file('foo.vcxproj')
                    }
                }
            }
        """
        run "visualStudio"

        then:
        skipped getFiltersTask("main")
        executedAndNotSkipped ":appVisualStudioSolution", getProjectTask("main")

        and:
        final projectFile = projectFile("foo.vcxproj")
        projectFile.assertHasComponentSources(app, "src/main")

        when:
        buildFile << """
            model {
                visualStudio {
                    projects.all {
                        filtersFile.location = file('foo.vcxproj.filters')
                    }
                }
            }
        """
        run "visualStudio"

        then:
        skipped ":appVisualStudioSolution", getProjectTask("main")
        executedAndNotSkipped getFiltersTask("main")

        when:
        run "visualStudio"

        then:
        skipped ":appVisualStudioSolution"
        skipped getComponentTasks("main")
    }

    @ToBeFixedForConfigurationCache
    def "visual studio tasks re-execute when a new variant is introduced"() {
        assumeFalse(toolChain.meets(WINDOWS_GCC))
        app.writeSources(file("src/main"))

        when:
        run "visualStudio"

        then:
        executedAndNotSkipped ":appVisualStudioSolution"
        executedAndNotSkipped getComponentTasks("main")

        when:
        buildFile << """
            model {
                flavors {
                    orange
                    banana
                }
            }
        """
        run "visualStudio"

        then:
        executedAndNotSkipped ":appVisualStudioSolution"
        executedAndNotSkipped getComponentTasks("main")

        and:
        final projectFile = projectFile("mainExe.vcxproj")
        projectFile.projectConfigurations.keySet() == ["win32DebugBanana", "win32DebugOrange",
                                                       "win32ReleaseBanana", "win32ReleaseOrange",
                                                       "x64DebugBanana", "x64DebugOrange",
                                                       "x64ReleaseBanana", "x64ReleaseOrange"] as Set

        when:
        run "visualStudio"

        then:
        skipped ":appVisualStudioSolution"
        skipped getComponentTasks("main")
    }

    @ToBeFixedForConfigurationCache
    def "visual studio tasks re-execute when compiler macros change"() {
        app.writeSources(file("src/main"))

        when:
        run "visualStudio"

        then:
        executedAndNotSkipped ":appVisualStudioSolution"
        executedAndNotSkipped getComponentTasks("main")

        when:
        buildFile << """
            model {
                components {
                    main {
                        binaries.all {
                            cppCompiler.define "FOO"
                        }
                    }
                }
            }
        """
        run "visualStudio"

        then:
        skipped ":appVisualStudioSolution"
        executedAndNotSkipped getComponentTasks("main")

        and:
        final projectFile = projectFile("mainExe.vcxproj")
        projectFile.projectConfigurations.values().each {
            assert it.macros == "FOO"
        }

        when:
        run "visualStudio"

        then:
        skipped ":appVisualStudioSolution"
        skipped getComponentTasks("main")
    }

    private static String[] getComponentTasks(String exeName) {
        return [getProjectTask(exeName), getFiltersTask(exeName)]
    }

    private static String getProjectTask(String exeName) {
        return ":${exeName}ExeVisualStudioProject"
    }

    private static String getFiltersTask(String exeName) {
        return ":${exeName}ExeVisualStudioFilters"
    }
}
