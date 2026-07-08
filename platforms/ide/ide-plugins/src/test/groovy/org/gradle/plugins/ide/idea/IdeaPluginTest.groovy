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
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.reflect.TypeOf
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

    def "registers no tasks"() {
        when:
        applyPluginToProjects()

        then:
        [project, childProject].every { p ->
            ['idea', 'openIdea', 'cleanIdea', 'ideaProject', 'cleanIdeaProject', 'ideaModule', 'cleanIdeaModule', 'ideaWorkspace', 'cleanIdeaWorkspace'].every {
                p.tasks.findByName(it) == null
            }
        }
    }

    def "configures idea project"() {
        when:
        applyPluginToProjects()

        then:
        project.idea.project.wildcards == ['!?*.java', '!?*.groovy', '!?*.class', '!?*.scala'] as Set
        project.idea.project.languageLevel.level ==  new IdeaLanguageLevel(JavaVersion.VERSION_1_6).level
        project.idea.project.modules == [project.idea.module, childProject.idea.module, anotherChildProject.idea.module]
        project.idea.project.jdkName == JavaVersion.current().toString()

        childProject.idea.project == null
    }

    def "adds special configuration if Java plugin is applied"() {
        when:
        applyPluginToProjects()
        project.apply(plugin: 'java')

        then:
        project.idea.project.languageLevel.level == new IdeaLanguageLevel(project.java.sourceCompatibility).level

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

     def "adds single entry libraries from source sets"() {
        when:
        applyPluginToProjects()
        project.apply(plugin: 'java')

        project.sourceSets.main.output.dir 'generated-folder'
        project.sourceSets.main.output.dir 'ws-generated'

        project.sourceSets.test.output.dir 'generated-test'
        project.sourceSets.test.output.dir 'test-resources'

        then:
        def runtime = project.idea.module.singleEntryLibraries.RUNTIME
        runtime.any { it.name.contains('generated-folder') }
        runtime.any { it.name.contains('ws-generated') }

        def test = project.idea.module.singleEntryLibraries.TEST
        test.any { it.name.contains('generated-test') }
        test.any { it.name.contains('test-resources') }
     }

    def "project language level set to highest module sourceCompatibility"() {
        when:
        applyPluginToProjects()
        project.apply(plugin: JavaPlugin)
        childProject.apply(plugin: JavaPlugin)
        anotherChildProject.apply(plugin: JavaPlugin)


        and:
        project.java.sourceCompatibility = JavaVersion.VERSION_1_5
        childProject.java.sourceCompatibility = JavaVersion.VERSION_1_6
        anotherChildProject.java.sourceCompatibility = JavaVersion.VERSION_1_7

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

    private applyPluginToProjects() {
        project.apply plugin: IdeaPlugin
        childProject.apply plugin: IdeaPlugin
        anotherChildProject.apply plugin: IdeaPlugin
    }
}
