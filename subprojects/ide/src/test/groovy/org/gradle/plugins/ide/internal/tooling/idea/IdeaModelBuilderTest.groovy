/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.plugins.ide.internal.tooling.idea

import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.plugins.GroovyPlugin
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.scala.ScalaPlugin
import org.gradle.internal.jvm.Jvm
import org.gradle.plugins.ide.idea.IdeaPlugin
import org.gradle.plugins.ide.internal.tooling.GradleProjectBuilder
import org.gradle.plugins.ide.internal.tooling.IdeaModelBuilder
import org.gradle.plugins.ide.internal.tooling.model.DefaultGradleProject
import org.gradle.test.fixtures.AbstractProjectBuilderSpec
import org.gradle.testfixtures.ProjectBuilder

class IdeaModelBuilderTest extends AbstractProjectBuilderSpec {
    Project child1
    Project child2

    def setup() {
        child1 = ProjectBuilder.builder().withName("child1").withParent(project).build()
        child2 = ProjectBuilder.builder().withName("child2").withParent(project).build()
        [project, child1, child2].each { it.pluginManager.apply(IdeaPlugin) }
    }

    def "project source language level matches idea plugin language level for non-jvm projects"() {
        when:
        def ideaProject = buildIdeaProjectModel()

        then:
        ideaProject.javaLanguageSettings.languageLevel == defaultIdeaPluginLanguageLevelForNonJavaProjects
        ideaProject.javaLanguageSettings.languageLevel == toJavaVersion(ideaProject.languageLevel)
    }

    def "project source language level matches idea plugin language level for jvm projects with default configuration"() {
        given:
        project.plugins.apply(pluginType)

        when:
        def ideaProject = buildIdeaProjectModel()

        then:
        ideaProject.javaLanguageSettings.languageLevel == defaultIdeaPluginLanguageLevelForJavaProjects
        ideaProject.javaLanguageSettings.languageLevel == toJavaVersion(ideaProject.languageLevel)

        where:
        pluginType << [JavaPlugin, GroovyPlugin, ScalaPlugin]
    }

    def "project source language level matches idea plugin language level for jvm projects with explicit configuration"() {
        given:
        project.plugins.apply(JavaPlugin)
        project.idea.project.languageLevel = sourceLanguageLevel

        when:
        def ideaProject = buildIdeaProjectModel()

        then:
        ideaProject.javaLanguageSettings.languageLevel.toString() == sourceLanguageLevel
        ideaProject.javaLanguageSettings.languageLevel == toJavaVersion(ideaProject.languageLevel)

        where:
        sourceLanguageLevel << ['1.1', '1.2', '1.3', '1.4', '1.5', '1.6', '1.7', '1.8', '9', '10', '11', '12', '13', '14', '15', '16', '17']
    }

    def "module java source settings are null for non-jvm projects"() {
        when:
        def ideaProject = buildIdeaProjectModel()

        then:
        ideaProject.modules.find { it.name == 'test-project' }.javaLanguageSettings == null
    }

    def "module source language level matches sourceCompatibility for java projects"() {
        given:
        project.plugins.apply(JavaPlugin)
        child1.plugins.apply(JavaPlugin)
        project.sourceCompatibility = '19'
        child1.sourceCompatibility = sourceCompatibility

        when:
        def ideaProject = buildIdeaProjectModel()

        then:
        ideaProject.modules.find { it.name == 'child1' }.javaLanguageSettings.languageLevel.toString() == sourceCompatibility

        where:
        sourceCompatibility << ['1.1', '1.2', '1.3', '1.4', '1.5', '1.6', '1.7', '1.8', '9', '10', '11', '12', '13', '14', '15', '16', '17']
    }

    def "module language level is not inherited for non equal project and module language level"() {
        given:
        project.plugins.apply(JavaPlugin)
        child1.plugins.apply(JavaPlugin)
        project.sourceCompatibility = '1.2'
        child1.sourceCompatibility = '1.3'
        when:
        def ideaProject = buildIdeaProjectModel()

        then:
        ideaProject.javaLanguageSettings.languageLevel == JavaVersion.VERSION_1_3
        ideaProject.modules.find { it.name == 'test-project' }.javaLanguageSettings.languageLevel == JavaVersion.VERSION_1_2
    }

    def "explicit project language level does not affect module language level"() {
        given:
        project.plugins.apply(JavaPlugin)
        child1.plugins.apply(JavaPlugin)
        child2.plugins.apply(JavaPlugin)
        project.idea.project.languageLevel = '1.2'
        project.sourceCompatibility = '1.3'
        child1.sourceCompatibility = '1.4'
        child2.sourceCompatibility = '1.5'

        when:
        def ideaProject = buildIdeaProjectModel()

        then:
        ideaProject.javaLanguageSettings.languageLevel == JavaVersion.VERSION_1_2
        ideaProject.modules.find { it.name == 'test-project' }.javaLanguageSettings.languageLevel == JavaVersion.VERSION_1_3
        ideaProject.modules.find { it.name == 'child1' }.javaLanguageSettings.languageLevel == JavaVersion.VERSION_1_4
        ideaProject.modules.find { it.name == 'child2' }.javaLanguageSettings.languageLevel == JavaVersion.VERSION_1_5
    }

    def "explicit project language level doesn't affect module bytecode level"() {
        given:
        project.plugins.apply(JavaPlugin)
        child1.plugins.apply(JavaPlugin)
        child2.plugins.apply(JavaPlugin)
        project.idea.project.languageLevel = '1.2'
        project.targetCompatibility = '1.3'
        child1.targetCompatibility = '1.4'
        child2.targetCompatibility = '1.5'

        when:
        def ideaProject = buildIdeaProjectModel()

        then:
        ideaProject.javaLanguageSettings.languageLevel == JavaVersion.VERSION_1_2
        ideaProject.modules.find { it.name == 'test-project' }.javaLanguageSettings.targetBytecodeVersion == JavaVersion.VERSION_1_3
        ideaProject.modules.find { it.name == 'child1' }.javaLanguageSettings.targetBytecodeVersion == JavaVersion.VERSION_1_4
        ideaProject.modules.find { it.name == 'child2' }.javaLanguageSettings.targetBytecodeVersion == null
    }

    def "can handle multi project builds where no projects are Java projects"() {
        when:
        def ideaProject = buildIdeaProjectModel()

        then:
        ideaProject.javaLanguageSettings.languageLevel == defaultIdeaPluginLanguageLevelForNonJavaProjects
        ideaProject.modules.find { it.name == 'test-project' }.javaLanguageSettings == null
        ideaProject.modules.find { it.name == 'child1' }.javaLanguageSettings == null
        ideaProject.modules.find { it.name == 'child2' }.javaLanguageSettings == null
    }

    def "can handle multi project builds with different source language levels"() {
        given:
        [project, child1, child2].each { it.plugins.apply(JavaPlugin) }
        project.sourceCompatibility = '1.3'
        child1.sourceCompatibility = '1.2'
        child2.sourceCompatibility = '1.3'

        when:
        def ideaProject = buildIdeaProjectModel()

        then:
        ideaProject.javaLanguageSettings.languageLevel == JavaVersion.VERSION_1_3
        ideaProject.modules.find { it.name == 'test-project' }.javaLanguageSettings.languageLevel == null
        ideaProject.modules.find { it.name == 'child1' }.javaLanguageSettings.languageLevel == JavaVersion.VERSION_1_2
        ideaProject.modules.find { it.name == 'child2' }.javaLanguageSettings.languageLevel == null
    }

    def "can handle multi project builds where only some projects are java projects"() {
        given:
        project.plugins.apply(JavaPlugin)
        project.sourceCompatibility = '1.4'
        child1.plugins.apply(JavaPlugin)
        child1.sourceCompatibility = '1.3'

        when:
        def ideaProject = buildIdeaProjectModel()

        then:
        ideaProject.javaLanguageSettings.languageLevel == JavaVersion.VERSION_1_4
        ideaProject.modules.find { it.name == 'test-project' }.javaLanguageSettings.languageLevel == null
        ideaProject.modules.find { it.name == 'child1' }.javaLanguageSettings.languageLevel == JavaVersion.VERSION_1_3
        ideaProject.modules.find { it.name == 'child2' }.javaLanguageSettings == null
    }

    def "project target runtime matches current jvm in use"() {
        when:
        [project, child1, child2].each {
            it.plugins.apply(JavaPlugin)
        }
        def ideaProject = buildIdeaProjectModel()
        then:
        ideaProject.javaLanguageSettings.jdk.javaHome == Jvm.current().javaHome
        ideaProject.javaLanguageSettings.jdk.javaVersion == Jvm.current().javaVersion
        ideaProject.modules.find { it.name == 'test-project' }.javaLanguageSettings.jdk == null
        ideaProject.modules.find { it.name == 'child1' }.javaLanguageSettings.jdk == null
        ideaProject.modules.find { it.name == 'child2' }.javaLanguageSettings.jdk == null
    }

    def "synched module bytecode level marked as inherited"() {

        when:
        [project, child1, child2].each {
            it.plugins.apply(JavaPlugin)
            it.targetCompatibility = "1.5"
        }
        def ideaProject = buildIdeaProjectModel()

        then:
        ideaProject.javaLanguageSettings.targetBytecodeVersion == JavaVersion.VERSION_1_5
        ideaProject.modules.find { it.name == 'test-project' }.javaLanguageSettings.targetBytecodeVersion == null
        ideaProject.modules.find { it.name == 'child1' }.javaLanguageSettings.targetBytecodeVersion == null
        ideaProject.modules.find { it.name == 'child2' }.javaLanguageSettings.targetBytecodeVersion == null
    }

    def "can have mixed bytecode level"() {
        when:
        [project, child1, child2].each {
            it.plugins.apply(JavaPlugin)
        }
        project.targetCompatibility = "1.5"
        child1.targetCompatibility = "1.6"
        child2.targetCompatibility = "1.7"

        def ideaProject = buildIdeaProjectModel()

        then:
        ideaProject.javaLanguageSettings.targetBytecodeVersion == JavaVersion.VERSION_1_7
        ideaProject.modules.find { it.name == 'test-project' }.javaLanguageSettings.targetBytecodeVersion == JavaVersion.VERSION_1_5
        ideaProject.modules.find { it.name == 'child1' }.javaLanguageSettings.targetBytecodeVersion == JavaVersion.VERSION_1_6
        ideaProject.modules.find { it.name == 'child2' }.javaLanguageSettings.targetBytecodeVersion == null
    }

    def "non jvm modules have no java sourceSettings applied"() {
        when:
        [project, child1].each {
            it.plugins.apply(JavaPlugin)
        }
        project.targetCompatibility = "1.6"
        child1.targetCompatibility = "1.7"

        def ideaProject = buildIdeaProjectModel()

        then:
        ideaProject.javaLanguageSettings.targetBytecodeVersion == JavaVersion.VERSION_1_7

        ideaProject.modules.find { it.name == 'test-project' }.javaLanguageSettings.targetBytecodeVersion == JavaVersion.VERSION_1_6
        ideaProject.modules.find { it.name == 'child1' }.javaLanguageSettings.targetBytecodeVersion == null
        ideaProject.modules.find { it.name == 'child2' }.javaLanguageSettings == null
    }

    def "non convention source and target compatibility properties are ignored"() {
        when:
        project.ext.sourceCompatibility = '1.2'
        project.ext.targetCompatibility = '1.2'
        project.plugins.apply(JavaPlugin)

        def ideaProject = buildIdeaProjectModel()

        then:
        ideaProject.javaLanguageSettings.languageLevel == defaultIdeaPluginLanguageLevelForJavaProjects
    }

    private DefaultIdeaProject buildIdeaProjectModel() {
        def builder = createIdeaModelBuilder()
        buildIdeaProject(builder, project)
    }

    private IdeaModelBuilder createIdeaModelBuilder() {
        def gradleProjectBuilder = Mock(GradleProjectBuilder)
        gradleProjectBuilder.buildAll(_) >> Mock(DefaultGradleProject)
        new IdeaModelBuilder(gradleProjectBuilder)
    }

    private DefaultIdeaProject buildIdeaProject(modelBuilder, project) {
        modelBuilder.buildAll("org.gradle.tooling.model.idea.IdeaProject", project)
    }

    private JavaVersion toJavaVersion(ideaLanguageLevel) {
        JavaVersion.valueOf(ideaLanguageLevel.level.replaceFirst("JDK", "VERSION"));
    }

    private JavaVersion getDefaultIdeaPluginLanguageLevelForNonJavaProjects() {
        JavaVersion.VERSION_1_6 // see IdeaPlugin#configureIdeaProject(Project)
    }

    private JavaVersion getDefaultIdeaPluginLanguageLevelForJavaProjects() {
        JavaVersion.current() // see IdeaPlugin#configureIdeaProjectForJava(Project)
    }
}
