/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.plugins.ide.idea

import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.internal.project.DefaultProject
import org.gradle.api.plugins.scala.ScalaPlugin
import org.gradle.api.tasks.Delete
import org.gradle.plugins.ide.idea.model.IdeaLanguageLevel
import org.gradle.util.HelperUtil
import spock.lang.Specification

class IdeaPluginTest extends Specification {
    private final DefaultProject project = HelperUtil.createRootProject()
    private final Project childProject = HelperUtil.createChildProject(project, "child", new File("."))

    def "adds 'ideaProject' task to root project"() {
        when:
        applyPluginToProjects()

        then:
        assertThatCleanIdeaDependsOnDeleteTask(project, project.cleanIdeaProject)
        GenerateIdeaProject ideaProjectTask = project.ideaProject
        ideaProjectTask instanceof GenerateIdeaProject
        ideaProjectTask.outputFile == new File(project.projectDir, project.name + ".ipr")
        ideaProjectTask.ideaProject.modules == [project.idea.module, childProject.idea.module]
        ideaProjectTask.ideaProject.jdkName == JavaVersion.current().toString()
        ideaProjectTask.ideaProject.languageLevel.level == "JDK_1_6"

        childProject.tasks.findByName('ideaProject') == null
        childProject.tasks.findByName('cleanIdeaProject') == null
    }

    def "configures idea project"() {
        when:
        applyPluginToProjects()

        then:
        project.idea.project.wildcards == ['!?*.java', '!?*.groovy'] as Set
    }

    def "adds 'ideaWorkspace' task to root project"() {
        when:
        applyPluginToProjects()

        then:
        project.ideaWorkspace instanceof GenerateIdeaWorkspace
        assert project.cleanIdeaWorkspace instanceof Delete
        assert !project.cleanIdea.taskDependencies.getDependencies(project.cleanIdea).contains(project.cleanIdeaWorkspace)


        childProject.tasks.findByName('ideaWorkspace') == null
        childProject.tasks.findByName('cleanIdeaWorkspace') == null
    }

    def "adds 'ideaModule' task to projects"() {
        when:
        applyPluginToProjects()

        then:
        assertThatIdeaModuleIsProperlyConfigured(project)
        assertThatIdeaModuleIsProperlyConfigured(childProject)
    }

    def "adds special configuration if Java plugin is applied"() {
        when:
        applyPluginToProjects()
        project.apply(plugin: 'java')

        then:
        project.idea.project.languageLevel.level == new IdeaLanguageLevel(project.sourceCompatibility).level

        def configurations = project.configurations
        project.idea.module.scopes == [
                COMPILE: [plus: [configurations.compile], minus: []],
                RUNTIME: [plus: [configurations.runtime], minus: [configurations.compile]],
                TEST: [plus: [configurations.testRuntime], minus: [configurations.runtime]],
                PROVIDED: [plus: [], minus: []]
        ]
    }

    def "picks up late changes to build dir"() {
        when:
        applyPluginToProjects()
        project.apply(plugin: 'java')
        project.buildDir = project.file('target')

        then:
        project.idea.module.excludeDirs == [project.buildDir, project.file('.gradle')] as Set
    }

    def "adds 'cleanIdea' task to projects"() {
        when:
        applyPluginToProjects()

        then:
        project.cleanIdea instanceof Task
        childProject.cleanIdea instanceof Task
    }

     def "adds single entry libraries from source sets"() {
        when:
        applyPluginToProjects()
        project.apply(plugin: 'java')

        project.sourceSets.main.output.dir 'generated-folder'
        project.sourceSets.main.output.dir 'ws-generated'

        project.sourceSets.test.output.dir 'generated-test'
        project.sourceSets.test.output.dir 'test-resources'

        then:
        def runtime = project.ideaModule.module.singleEntryLibraries.RUNTIME
        runtime.any { it.name.contains('generated-folder') }
        runtime.any { it.name.contains('ws-generated') }

        def test = project.ideaModule.module.singleEntryLibraries.TEST
        test.any { it.name.contains('generated-test') }
        test.any { it.name.contains('test-resources') }
     }

    def "makes scala modules depend on root's project"() {
        applyPluginToProjects()

        when:
        childProject.plugins.apply(ScalaPlugin)

        then:
        def parentIdeaProject = project.tasks.ideaProject
        def parentIdeaModule = project.tasks.ideaModule
        def childIdeaModule = childProject.tasks.ideaModule

        childIdeaModule.taskDependencies.getDependencies(childIdeaModule).contains(parentIdeaProject)
        !parentIdeaModule.taskDependencies.getDependencies(parentIdeaModule).contains(parentIdeaProject)
    }

    private void assertThatIdeaModuleIsProperlyConfigured(Project project) {
        GenerateIdeaModule ideaModuleTask = project.ideaModule
        assert ideaModuleTask instanceof GenerateIdeaModule
        assert ideaModuleTask.outputFile == new File(project.projectDir, project.name + ".iml")
        assertThatCleanIdeaDependsOnDeleteTask(project, project.cleanIdeaModule)
    }

    private void assertThatCleanIdeaDependsOnDeleteTask(Project project, Task dependsOnTask) {
        assert dependsOnTask instanceof Delete
        assert project.cleanIdea.taskDependencies.getDependencies(project.cleanIdea).contains(dependsOnTask)
    }

    private applyPluginToProjects() {
        project.apply plugin: IdeaPlugin
        childProject.apply plugin: IdeaPlugin
    }
}