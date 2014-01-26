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
import org.gradle.nativebinaries.language.cpp.fixtures.AbstractInstalledToolChainIntegrationSpec
import org.gradle.nativebinaries.language.cpp.fixtures.app.CppHelloWorldApp

class VisualStudioFileCustomizationIntegrationTest extends AbstractInstalledToolChainIntegrationSpec {

    def app = new CppHelloWorldApp()

    def setup() {
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
    }
    executables {
        main {}
    }
"""
    }

    def "can specific location of generated files"() {
        when:
        buildFile << '''
    model {
        visualStudio {
            projects.all { project ->
                projectFile.location = "other/${project.name}.vcxproj"
                filtersFile.location = "other/filters.vcxproj.filters"
            }
            solutions.all {
                solutionFile.location = "vs/${it.name}.solution"
            }
        }
    }
'''
        and:
        run "mainVisualStudio"

        then:
        executedAndNotSkipped ":mainExeVisualStudio"

        and:
        final projectFile = projectFile("other/mainExe.vcxproj")
        filtersFile("other/filters.vcxproj.filters")

        final mainSolution = solutionFile("vs/mainExe.solution")
        mainSolution.assertHasProjects("mainExe")
        mainSolution.assertReferencesProject(projectFile, ['debug', 'release'])
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
        run "mainVisualStudio"

        then:
        final projectFile = projectFile("visualStudio/mainExe.vcxproj")
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
        run "mainVisualStudio"

        then:
        final filtersFile = filtersFile("visualStudio/mainExe.vcxproj.filters")
        filtersFile.xml.ExtraContent[0].text() == "Filter - mainExe"
    }

    def "can add text content to generated solution files"() {
        when:
        buildFile << '''
    model {
        visualStudio {
            solutions.all { solution ->
                solution.solutionFile.withContent { content ->
                    String projectList = solution.projects.collect({it.name}).join(',')
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
        run "mainVisualStudio"

        then:
        final solutionFile = solutionFile("visualStudio/mainExe.sln")
        solutionFile.content.contains "GlobalSection(MyGlobalSection)"
        solutionFile.content.contains "Project-list: mainExe"
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
