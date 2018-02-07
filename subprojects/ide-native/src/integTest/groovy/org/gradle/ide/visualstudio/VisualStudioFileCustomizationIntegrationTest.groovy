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

import org.gradle.ide.visualstudio.fixtures.FiltersFile
import org.gradle.ide.visualstudio.fixtures.ProjectFile
import org.gradle.ide.visualstudio.fixtures.SolutionFile
import org.gradle.internal.os.OperatingSystem
import org.gradle.nativeplatform.fixtures.AbstractInstalledToolChainIntegrationSpec
import org.gradle.nativeplatform.fixtures.app.CppHelloWorldApp

class VisualStudioFileCustomizationIntegrationTest extends AbstractInstalledToolChainIntegrationSpec {

    def app = new CppHelloWorldApp()

    def setup() {
        settingsFile << """
            rootProject.name = 'app'
        """
        app.writeSources(file("src/main"))
        buildFile << """
    apply plugin: 'cpp'
    apply plugin: 'visual-studio'

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
        components {
            main(NativeExecutableSpec)
        }
    }
"""
    }

    def "can specify location of generated files"() {
        when:
        file("gradlew.bat") << "dummy wrapper"
        buildFile << '''
    model {
        visualStudio {
            projects.all { project ->
                projectFile.location = "very/deeply/nested/${project.name}.vcxproj"
                filtersFile.location = "other/filters.vcxproj.filters"
            }
            solution {
                solutionFile.location = "vs/${it.name}.solution"
            }
        }
    }
'''
        and:
        run "visualStudio"

        then:
        executedAndNotSkipped ":visualStudio"

        and:
        final projectFile = projectFile("very/deeply/nested/mainExe.vcxproj")
        assert projectFile.headerFiles == app.headerFiles*.withPath("../../../src/main").sort()
        assert projectFile.sourceFiles == ['../../../build.gradle'] + app.sourceFiles*.withPath("../../../src/main").sort()
        projectFile.projectConfigurations.values().each {
            assert it.buildCommand == "../../../gradlew.bat -p \"../../..\" :installMain${it.name.capitalize()}Executable"
            assert it.outputFile == OperatingSystem.current().getExecutableName("../../../build/install/main/${it.name}/lib/main")
        }
        def filtersFile = filtersFile("other/filters.vcxproj.filters")

        and:
        final mainSolution = solutionFile("vs/app.solution")
        mainSolution.assertHasProjects("mainExe")
        mainSolution.assertReferencesProject(projectFile, ['debug', 'release'])

        // Ensure that clean handles custom file locations
        when:
        run "cleanVisualStudio"

        then:
        projectFile.projectFile.assertDoesNotExist()
        filtersFile.file.assertDoesNotExist()
        mainSolution.file.assertDoesNotExist()
    }

    def "can add xml configuration to generated project files"() {
        when:
        buildFile << """
    model {
        visualStudio {
            projects.all { project ->
                projectFile.withXml { xml ->
                    Node globals = xml.asNode().PropertyGroup.find({it.'@Label' == 'Globals'}) as Node
                    globals.appendNode("ExtraInfo", "Some extra info")
                    globals.appendNode("ProjectName", project.name)
                }
            }
        }
    }
"""
        and:
        run "visualStudio"

        then:
        final projectFile = projectFile("mainExe.vcxproj")
        projectFile.globals.ExtraInfo[0].text() == "Some extra info"
        projectFile.globals.ProjectName[0].text() == "mainExe"
    }

    def "can add xml configuration to generated filter files"() {
        when:
        buildFile << '''
    model {
        visualStudio {
            projects.all { project ->
                filtersFile.withXml { xml ->
                    xml.asNode().appendNode("ExtraContent", "Filter - ${project.name}")
                }
            }
        }
    }
'''
        and:
        run "visualStudio"

        then:
        final filtersFile = filtersFile("mainExe.vcxproj.filters")
        filtersFile.xml.ExtraContent[0].text() == "Filter - mainExe"
    }

    def "can add text content to generated solution files"() {
        when:
        buildFile << '''
    model {
        visualStudio {
            solution { solution ->
                solution.solutionFile.withContent { content ->
                    String projectList = projects.collect({it.name}).join(',')
                    int insertPos = text.lastIndexOf("EndGlobal")
                    content.text = content.text.replace("EndGlobal", """
    GlobalSection(MyGlobalSection)
       Project-list: ${projectList}
    EndGlobalSection
EndGlobal
""")
                }
            }
        }
    }
'''

        and:
        run "visualStudio"

        then:
        final solutionFile = solutionFile("app.sln")
        solutionFile.content.contains "GlobalSection(MyGlobalSection)"
        solutionFile.content.contains "Project-list: mainExe"
    }

    def "can configure gradle command line"() {
        when:
        buildFile << """
tasks.withType(GenerateProjectFileTask) {
    it.gradleExe "myCustomGradleExe"
    it.gradleArgs "--configure-on-demand --another"
}
"""
        and:
        run "visualStudio"

        then:
        final projectFile = projectFile("mainExe.vcxproj")
        projectFile.projectConfigurations.values().each {
            assert it.buildCommand == "myCustomGradleExe --configure-on-demand --another :installMain${it.name.capitalize()}Executable"
        }
    }

    private SolutionFile solutionFile(String path) {
        return new SolutionFile(file(path))
    }

    private ProjectFile projectFile(String path) {
        return new ProjectFile(file(path))
    }

    private FiltersFile filtersFile(String path) {
        return new FiltersFile(file(path))
    }
}
