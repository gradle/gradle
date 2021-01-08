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
import org.gradle.internal.os.OperatingSystem
import org.gradle.nativeplatform.fixtures.app.CppHelloWorldApp

import static org.apache.commons.io.FileUtils.copyFile

class VisualStudioIncrementalIntegrationTest extends AbstractVisualStudioIntegrationSpec {
    def app = new CppHelloWorldApp()

    def setup() {
        settingsFile << """
            rootProject.name = 'app'
        """
        buildFile << """
            apply plugin: 'visual-studio'
            apply plugin: 'cpp-application'
        """
    }

    @ToBeFixedForConfigurationCache
    def "visual studio tasks re-execute when source files are added"() {
        app.writeSources(file("src/main"))

        when:
        run "visualStudio"

        then:
        executedAndNotSkipped ":appVisualStudioSolution"
        executedAndNotSkipped getComponentTasks("app")

        when:
        copyFile(file("src/main/cpp/main.cpp"), file("src/main/cpp/foo.cpp"))
        run "visualStudio"

        then:
        skipped ":appVisualStudioSolution"
        executedAndNotSkipped getComponentTasks("app")

        and:
        final projectFile = projectFile("app.vcxproj")
        assert projectFile.sourceFiles.contains('src/main/cpp/foo.cpp')

        when:
        run "visualStudio"

        then:
        skipped ":appVisualStudioSolution"
        skipped getComponentTasks("app")
    }

    @ToBeFixedForConfigurationCache
    def "visual studio tasks re-execute when header files are added"() {
        app.writeSources(file("src/main"))

        when:
        run "visualStudio"

        then:
        executedAndNotSkipped ":appVisualStudioSolution"
        executedAndNotSkipped getComponentTasks("app")

        when:
        copyFile(file("src/main/headers/hello.h"), file("src/main/headers/foo.h"))
        run "visualStudio"

        then:
        skipped ":appVisualStudioSolution"
        executedAndNotSkipped getComponentTasks("app")

        and:
        final projectFile = projectFile("app.vcxproj")
        assert projectFile.headerFiles.contains('src/main/headers/foo.h')

        when:
        run "visualStudio"

        then:
        skipped ":appVisualStudioSolution"
        skipped getComponentTasks("app")
    }

    @ToBeFixedForConfigurationCache
    def "visual studio tasks re-execute when output file locations change"() {
        app.writeSources(file("src/main"))

        when:
        run "visualStudio"

        then:
        executedAndNotSkipped ":appVisualStudioSolution"
        executedAndNotSkipped getComponentTasks("app")

        when:
        buildFile << """
            visualStudio {
                solution {
                    solutionFile.location = file('foo.sln')
                }
            }
        """
        run "visualStudio"

        then:
        executedAndNotSkipped ":appVisualStudioSolution"
        skipped getComponentTasks("app")

        and:
        final solutionFile = solutionFile("foo.sln")
        solutionFile.assertReferencesProject(projectFile("app.vcxproj"), ["debug", "release"])

        when:
        buildFile << """
            visualStudio {
                projects.all {
                    projectFile.location = file('foo.vcxproj')
                }
            }
        """
        run "visualStudio"

        then:
        skipped getFiltersTask("app")
        executedAndNotSkipped ":appVisualStudioSolution", getProjectTask("app")

        and:
        final projectFile = projectFile("foo.vcxproj")
        projectFile.assertHasComponentSources(app, "src/main")

        when:
        buildFile << """
            visualStudio {
                projects.all {
                    filtersFile.location = file('foo.vcxproj.filters')
                }
            }
        """
        run "visualStudio"

        then:
        skipped ":appVisualStudioSolution", getProjectTask("app")
        executedAndNotSkipped getFiltersTask("app")

        when:
        run "visualStudio"

        then:
        skipped ":appVisualStudioSolution"
        skipped getComponentTasks("app")
    }

    @ToBeFixedForConfigurationCache
    def "visual studio tasks re-execute when compiler macros change"() {
        app.writeSources(file("src/main"))

        when:
        run "visualStudio"

        then:
        executedAndNotSkipped ":appVisualStudioSolution"
        executedAndNotSkipped getComponentTasks("app")

        when:
        buildFile << """
            application {
                binaries.configureEach { binary ->
                    binary.compileTask.get().macros["FOO"] = null
                }
            }
        """
        run "visualStudio"

        then:
        skipped ":appVisualStudioSolution"
        executedAndNotSkipped getComponentTasks("app")

        and:
        final projectFile = projectFile("app.vcxproj")
        projectFile.projectConfigurations.values().each {
            assert it.macros == "FOO"
        }

        when:
        run "visualStudio"

        then:
        skipped ":appVisualStudioSolution"
        skipped getComponentTasks("app")
    }

    @ToBeFixedForConfigurationCache
    def "visual studio tasks re-execute when binary output location changes"() {
        app.writeSources(file("src/main"))

        when:
        run "visualStudio"

        then:
        executedAndNotSkipped ":appVisualStudioSolution"
        executedAndNotSkipped getComponentTasks("app")

        when:
        buildFile << """
            application {
                binaries.configureEach { binary ->
                    binary.installTask.get().installDirectory = project.layout.buildDirectory.dir("foo/\${binary.name}")
                }
            }
        """
        run "visualStudio"

        then:
        skipped ":appVisualStudioSolution"
        executedAndNotSkipped getComponentTasks("app")

        and:
        final projectFile = projectFile("app.vcxproj")
        projectFile.projectConfigurations.values().each {
            assert it.outputFile == OperatingSystem.current().getExecutableName("build/foo/main${it.name.capitalize()}/lib/app")
        }

        when:
        run "visualStudio"

        then:
        skipped ":appVisualStudioSolution"
        skipped getComponentTasks("app")
    }

    @ToBeFixedForConfigurationCache
    def "visual studio tasks re-execute when new component is added"() {
        app.writeSources(file("src/main"))

        when:
        run "visualStudio"

        then:
        executedAndNotSkipped ":appVisualStudioSolution"
        executedAndNotSkipped getComponentTasks("app")

        when:
        app.writeSources(file("foo/src/main"))
        settingsFile << """
            include ':foo'
        """
        buildFile << """
            project(':foo') {
                apply plugin: 'cpp-application'
                apply plugin: 'visual-studio'
            }
        """
        run "visualStudio"

        then:
        executedAndNotSkipped ":appVisualStudioSolution"
        executedAndNotSkipped getComponentTasks("foo:foo")
        skipped getComponentTasks("app")

        when:
        run "visualStudio"

        then:
        skipped ":appVisualStudioSolution"
        skipped getComponentTasks("foo:foo")
        skipped getComponentTasks("app")
    }

    @ToBeFixedForConfigurationCache
    def "visual studio tasks re-execute when metadata files are removed"() {
        app.writeSources(file("src/main"))

        when:
        run "visualStudio"

        then:
        executedAndNotSkipped ":appVisualStudioSolution"
        executedAndNotSkipped getComponentTasks("app")

        when:
        assert file("app.sln").delete()
        run "visualStudio"

        then:
        executedAndNotSkipped ":appVisualStudioSolution"
        skipped getComponentTasks("app")

        when:
        assert file("app.vcxproj").delete()
        run "visualStudio"

        then:
        skipped ":appVisualStudioSolution", getFiltersTask("app")
        executedAndNotSkipped getProjectTask("app")

        when:
        assert file("app.vcxproj.filters").delete()
        run "visualStudio"

        then:
        skipped ":appVisualStudioSolution", getProjectTask("app")
        executedAndNotSkipped getFiltersTask("app")

        when:
        run "visualStudio"

        then:
        skipped ":appVisualStudioSolution"
        skipped getComponentTasks("app")
    }

    @ToBeFixedForConfigurationCache
    def "visual studio tasks re-execute when new project xml actions are added"() {
        app.writeSources(file("src/main"))

        when:
        run "visualStudio"

        then:
        executedAndNotSkipped ":appVisualStudioSolution"
        executedAndNotSkipped getComponentTasks("app")

        when:
        buildFile << """
            visualStudio {
                projects.all {
                    projectFile.withXml { }
                }
            }
        """
        run "visualStudio"

        then:
        skipped ":appVisualStudioSolution"
        executedAndNotSkipped getComponentTasks("app")

        when:
        buildFile.text = buildFile.text.replace "projectFile.withXml { }", """
                    projectFile.withXml { xml ->
                        Node globals = xml.asNode().PropertyGroup.find({it.'@Label' == 'Globals'}) as Node
                        globals.appendNode("ExtraInfo", "Some extra info")
                        globals.appendNode("ProjectName", project.name)
                    }
        """
        run "visualStudio"

        then:
        skipped ":appVisualStudioSolution"
        executedAndNotSkipped getComponentTasks("app")

        when:
        run "visualStudio"

        then:
        skipped ":appVisualStudioSolution"
        skipped getComponentTasks("app")
    }

    @ToBeFixedForConfigurationCache
    def "visual studio tasks re-execute when new filter file xml actions are added"() {
        app.writeSources(file("src/main"))

        when:
        run "visualStudio"

        then:
        executedAndNotSkipped ":appVisualStudioSolution"
        executedAndNotSkipped getComponentTasks("app")

        when:
        buildFile << """
            visualStudio {
                projects.all {
                    filtersFile.withXml { }
                }
            }
        """
        run "visualStudio"

        then:
        skipped ":appVisualStudioSolution"
        executedAndNotSkipped getComponentTasks("app")

        when:
        buildFile.text = buildFile.text.replace "filtersFile.withXml { }", """
                    filtersFile.withXml { xml ->
                        xml.asNode().appendNode("ExtraContent", "Filter - \${project.name}")
                    }
        """
        run "visualStudio"

        then:
        skipped ":appVisualStudioSolution"
        executedAndNotSkipped getComponentTasks("app")

        when:
        run "visualStudio"

        then:
        skipped ":appVisualStudioSolution"
        skipped getComponentTasks("app")
    }

    @ToBeFixedForConfigurationCache
    def "visual studio tasks re-execute when new solution content actions are added"() {
        app.writeSources(file("src/main"))

        when:
        run "visualStudio"

        then:
        executedAndNotSkipped ":appVisualStudioSolution"
        executedAndNotSkipped getComponentTasks("app")

        when:
        buildFile << """
            visualStudio {
                solution {
                    solutionFile.withContent { }
                }
            }
        """
        run "visualStudio"

        then:
        executedAndNotSkipped ":appVisualStudioSolution"
        skipped getComponentTasks("app")

        when:
        buildFile.text = buildFile.text.replace "solutionFile.withContent { }", '''
                    solutionFile.withContent { content ->
                        String projectList = projects.collect({it.name}).join(',')

                        content.text = content.text.replace("EndGlobal", """
                            GlobalSection(MyGlobalSection)
                            Project-list: ${projectList}
                            EndGlobalSection
                            EndGlobal
                        """)
                    }
        '''
        run "visualStudio"
        println buildFile.text

        then:
        executedAndNotSkipped ":appVisualStudioSolution"
        skipped getComponentTasks("app")

        when:
        run "visualStudio"

        then:
        skipped ":appVisualStudioSolution"
        skipped getComponentTasks("app")
    }

    private static String[] getComponentTasks(String exeName) {
        return [getProjectTask(exeName), getFiltersTask(exeName)]
    }

    private static String getFiltersTask(String exeName) {
        ":${exeName}VisualStudioFilters"
    }

    private static String getProjectTask(String exeName) {
        return ":${exeName}VisualStudioProject"
    }

}
