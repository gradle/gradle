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
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.scala.ScalaPlugin
import org.gradle.api.problems.Problems
import org.gradle.api.problems.internal.DefaultProblems
import org.gradle.api.reflect.TypeOf
import org.gradle.api.tasks.Delete
import org.gradle.internal.operations.BuildOperationProgressEventEmitter
import org.gradle.plugins.ide.idea.model.IdeaLanguageLevel
import org.gradle.plugins.ide.idea.model.IdeaModel
import org.gradle.test.fixtures.AbstractProjectBuilderSpec
import org.gradle.util.TestUtil
import spock.lang.Issue

import static org.gradle.api.reflect.TypeOf.typeOf

class IdeaPluginTest extends AbstractProjectBuilderSpec {
    private ProjectInternal childProject
    private ProjectInternal anotherChildProject

    def setup() {
        childProject = TestUtil.createChildProject(project, "child")
        anotherChildProject = TestUtil.createChildProject(project, "child2")
        Problems.init(new DefaultProblems(Mock(BuildOperationProgressEventEmitter)))
    }

    def "adds extension to root project"() {
        when:
        applyPluginToProjects()

        then:
        project.idea instanceof IdeaModel
        project.idea.project != null
        project.idea.project.location.get().asFile == project.file("test-project.ipr")
        project.idea.module.outputFile == project.file("test-project.iml")
    }

    def "adds extension to child project"() {
        when:
        applyPluginToProjects()

        then:
        childProject.idea instanceof IdeaModel
        childProject.idea.project == null
        childProject.idea.module.outputFile == childProject.file("child.iml")
    }

    def "adds 'ideaProject' task to root project"() {
        when:
        applyPluginToProjects()

        then:
        assertThatCleanIdeaDependsOnDeleteTask(project, project.cleanIdeaProject)
        GenerateIdeaProject ideaProjectTask = project.ideaProject
        ideaProjectTask instanceof GenerateIdeaProject
        ideaProjectTask.outputFile == new File(project.projectDir, project.name + ".ipr")
        ideaProjectTask.ideaProject.modules == [project.idea.module, childProject.idea.module, anotherChildProject.idea.module]
        ideaProjectTask.ideaProject.jdkName == JavaVersion.current().toString()
        ideaProjectTask.ideaProject.languageLevel.level == "JDK_1_6"

        childProject.tasks.findByName('ideaProject') == null
        childProject.tasks.findByName('cleanIdeaProject') == null
    }

    def "adds 'openIdea' task to root project"() {
        when:
        applyPluginToProjects()

        then:
        project.tasks.openIdea != null
    }

    def "configures idea project"() {
        when:
        applyPluginToProjects()

        then:
        project.idea.project.wildcards == ['!?*.java', '!?*.groovy', '!?*.class', '!?*.scala'] as Set
        project.idea.project.languageLevel.level ==  new IdeaLanguageLevel(JavaVersion.VERSION_1_6).level
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

        project.idea.module.scopes == [
                PROVIDED: [plus: [project.configurations.compileClasspath], minus: []],
                COMPILE: [plus: [], minus: []],
                RUNTIME: [plus: [project.configurations.runtimeClasspath], minus: []],
                TEST: [plus: [project.configurations.testCompileClasspath, project.configurations.testRuntimeClasspath], minus: []],
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
        childProject.pluginManager.apply(ScalaPlugin)

        then:
        def parentIdeaProject = project.tasks.ideaProject
        def parentIdeaModule = project.tasks.ideaModule
        def childIdeaModule = childProject.tasks.ideaModule

        childIdeaModule.taskDependencies.getDependencies(childIdeaModule).contains(parentIdeaProject)
        !parentIdeaModule.taskDependencies.getDependencies(parentIdeaModule).contains(parentIdeaProject)
    }

    def "project language level set to highest module sourceCompatibility"() {
        when:
        applyPluginToProjects()
        project.apply(plugin: JavaPlugin)
        childProject.apply(plugin: JavaPlugin)
        anotherChildProject.apply(plugin: JavaPlugin)


        and:
        project.sourceCompatibility = JavaVersion.VERSION_1_5
        childProject.sourceCompatibility = JavaVersion.VERSION_1_6
        anotherChildProject.sourceCompatibility = JavaVersion.VERSION_1_7

        then:
        project.idea.project.languageLevel.level == new IdeaLanguageLevel(JavaVersion.VERSION_1_7).level
    }

    def "declares public type of idea extension"() {
        when:
        applyPluginToProjects()

        then:
        publicTypeOfExtension("idea") == typeOf(IdeaModel)
    }

    @Issue('https://github.com/gradle/gradle/issues/8749')
    def "can add to file set properties"() {
        given:
        applyPluginToProjects()
        def source = new File("foo")

        when:
        property(project.idea.module).add(source)

        then:
        property(project.idea.module).contains(source)

        where:
        property << [{ it.sourceDirs }, { it.resourceDirs }, { it.excludeDirs }]
    }

    @Issue('https://github.com/gradle/gradle/issues/8749')
    def "can add to file set properties when java plugin is applied too"() {
        given:
        project.apply plugin: JavaPlugin
        applyPluginToProjects()
        def source = new File("foo")

        when:
        property(project.idea.module).add(source)

        then:
        property(project.idea.module).contains(source)

        where:
        property << [{ it.sourceDirs }, { it.resourceDirs }, { it.excludeDirs }]
    }

    private TypeOf<?> publicTypeOfExtension(String named) {
        project.extensions.extensionsSchema.find { it.name == named }.publicType
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
        anotherChildProject.apply plugin: IdeaPlugin
    }
}
