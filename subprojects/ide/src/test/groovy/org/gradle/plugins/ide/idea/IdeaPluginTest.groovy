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
import org.gradle.api.tasks.Delete
import org.gradle.util.HelperUtil
import spock.lang.Specification

/**
 * @author Hans Dockter
 */
class IdeaPluginTest extends Specification {
    private final DefaultProject project = HelperUtil.createRootProject()
    private final Project childProject = HelperUtil.createChildProject(project, "child", new File("."))
    private final IdeaPlugin ideaPlugin = new IdeaPlugin()

    def "adds configurer task to root project only"() {
        when:
        applyPluginToProjects()

        then:
        project.ideaConfigurer instanceof IdeaConfigurer
        childProject.tasks.findByName('ideaConfigurer') == null
    }

    def "adds generator task configurers with required dependencies"() {
        when:
        applyPluginToProjects()

        then:
        project.ideaWorkspaceConfigurer.dependsOn.contains(project.ideaConfigurer)
        project.ideaProjectConfigurer.dependsOn.contains(project.ideaConfigurer)
        childProject.ideaModuleConfigurer.dependsOn.contains(project.ideaConfigurer)
    }

    def "makes sure generator tasks act after they are configured"() {
        when:
        applyPluginToProjects()

        then:
        project.ideaWorkspace.dependsOn.contains(project.ideaWorkspaceConfigurer)
        project.ideaProject.dependsOn.contains(project.ideaProjectConfigurer)
        childProject.ideaModule.dependsOn.contains(childProject.ideaModuleConfigurer)
    }

    def "makes all generation and clean tasks depend on configurer"() {
        when:
        applyPluginToProjects()

        then:
        [project.ideaWorkspace, project.cleanIdeaWorkspace,
         project.ideaModule, project.cleanIdeaModule,
         project.ideaProject, project.cleanIdeaProject].each {
            assert it.dependsOn.contains(project.rootProject.ideaConfigurer)
        }
    }

    def "adds 'ideaProject' task to root project"() {
        when:
        applyPluginToProjects()

        then:
        assertThatCleanIdeaDependsOnDeleteTask(project, project.cleanIdeaProject)
        IdeaProject ideaProjectTask = project.ideaProject
        ideaProjectTask instanceof IdeaProject
        ideaProjectTask.outputFile == new File(project.projectDir, project.name + ".ipr")
        ideaProjectTask.subprojects == project.rootProject.allprojects
        ideaProjectTask.javaVersion == JavaVersion.VERSION_1_6.toString()
        ideaProjectTask.wildcards == ['!?*.java', '!?*.groovy'] as Set

        childProject.tasks.findByName('ideaProject') == null
        childProject.tasks.findByName('cleanIdeaProject') == null
    }

    def "adds 'ideaWorkspace' task to root project"() {
        when:
        applyPluginToProjects()

        then:
        project.ideaWorkspace instanceof IdeaWorkspace
        assertThatCleanIdeaDependsOnDeleteTask(project, project.cleanIdeaWorkspace)

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
        project.ideaProject.javaVersion == project.sourceCompatibility.toString()

        IdeaModule ideaModuleTask = project.ideaModule
        ideaModuleTask.sourceDirs == project.sourceSets.main.allSource.sourceTrees.srcDirs.flatten() as Set
        ideaModuleTask.testSourceDirs == project.sourceSets.test.allSource.sourceTrees.srcDirs.flatten() as Set
        def configurations = project.configurations
        ideaModuleTask.scopes == [
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
        project.ideaModule.excludeDirs == [project.buildDir, project.file('.gradle')] as Set
    }

    def "adds 'cleanIdea' task to projects"() {
        when:
        applyPluginToProjects()

        then:
        project.cleanIdea instanceof Task
        childProject.cleanIdea instanceof Task
    }

    private void assertThatIdeaModuleIsProperlyConfigured(Project project) {
        IdeaModule ideaModuleTask = project.ideaModule
        assert ideaModuleTask instanceof IdeaModule
        assert ideaModuleTask.outputFile == new File(project.projectDir, project.name + ".iml")
        assert ideaModuleTask.moduleDir == project.projectDir
        assert ideaModuleTask.sourceDirs == [] as Set
        assert ideaModuleTask.testSourceDirs == [] as Set
        assert ideaModuleTask.excludeDirs == [project.buildDir, project.file('.gradle')] as Set
        assert ideaModuleTask.variables == [:]
        assertThatCleanIdeaDependsOnDeleteTask(project, project.cleanIdeaModule)
    }

    private void assertThatCleanIdeaDependsOnDeleteTask(Project project, Task dependsOnTask) {
        assert dependsOnTask instanceof Delete
        assert project.cleanIdea.taskDependencies.getDependencies(project.cleanIdea).contains(dependsOnTask)
    }

    private applyPluginToProjects() {
        ideaPlugin.apply(project)
        ideaPlugin.apply(childProject)
    }
}