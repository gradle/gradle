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
import org.gradle.plugins.ide.idea.IdeaPlugin
import org.gradle.plugins.ide.idea.model.IdeaLanguageLevel
import org.gradle.plugins.ide.internal.tooling.GradleProjectBuilder
import org.gradle.plugins.ide.internal.tooling.IdeaModelBuilder
import org.gradle.tooling.internal.gradle.DefaultGradleProject
import org.gradle.util.TestUtil
import spock.lang.Specification

class IdeaModelBuilderTest extends Specification {

    Project root
    Project child1
    Project child2

    def setup() {
        root = TestUtil.builder().withName("root").build()
        child1 = TestUtil.builder().withName("child1").withParent(root).build()
        child2 = TestUtil.builder().withName("child2").withParent(root).build()
        [root, child1, child2].each { it.pluginManager.apply(IdeaPlugin) }
    }

    def "project source language level matches idea plugin language level for non-jvm projects"() {
        when:
        def ideaProject = buildIdeaProjectModel()

        then:
        ideaProject.javaSourceSettings.sourceLanguageLevel == defaultIdeaPluginLanguageLevelForNonJavaProjects
        ideaProject.javaSourceSettings.sourceLanguageLevel == toJavaVersion(ideaProject.languageLevel)
    }

    def "project source language level matches idea plugin language level for jvm projects with default configuration"() {
        given:
        root.plugins.apply(pluginType)

        when:
        def ideaProject = buildIdeaProjectModel()

        then:
        ideaProject.javaSourceSettings.sourceLanguageLevel == defaultIdeaPluginLanguageLevelForJavaProjects
        ideaProject.javaSourceSettings.sourceLanguageLevel == toJavaVersion(ideaProject.languageLevel)

        where:
        pluginType << [JavaPlugin, GroovyPlugin, ScalaPlugin]
    }

    def "project source language level matches idea plugin language level for jvm projects with explicit configuration"() {
        given:
        root.plugins.apply(JavaPlugin)
        root.idea.project.languageLevel = sourceLanguageLevel

        when:
        def ideaProject = buildIdeaProjectModel()

        then:
        ideaProject.javaSourceSettings.sourceLanguageLevel.toString() == sourceLanguageLevel
        ideaProject.javaSourceSettings.sourceLanguageLevel == toJavaVersion(ideaProject.languageLevel)

        where:
        sourceLanguageLevel << ['1.1', '1.2', '1.3', '1.4', '1.5', '1.6', '1.7', '1.8', '1.9']
    }

    def "module source language level matches idea plugin language level for non-jvm projects"() {
        when:
        def ideaProject = buildIdeaProjectModel()

        then:
        ideaProject.modules.find { it.name == 'root'}.javaSourceSettings.sourceLanguageLevel == defaultIdeaPluginLanguageLevelForNonJavaProjects
        ideaProject.modules.find { it.name == 'root'}.javaSourceSettings.sourceLanguageLevel == toJavaVersion(ideaProject.languageLevel)
    }

    def "module source language level matches source compatibility level from java plugin for jvm projects"() {
        given:
        root.plugins.apply(JavaPlugin)
        root.sourceCompatibility = sourceCompatibility

        when:
        def ideaProject = buildIdeaProjectModel()

        then:
        ideaProject.modules.find { it.name == 'root'}.javaSourceSettings.sourceLanguageLevel.toString() == sourceCompatibility

        where:
        sourceCompatibility << ['1.1', '1.2', '1.3', '1.4', '1.5', '1.6', '1.7', '1.8', '1.9']
    }

    def "if the project source language level and the module source language level are the same then the module source language level is inherited"() {
        given:
        root.plugins.apply(JavaPlugin)
        root.idea.project.languageLevel = '1.2'
        root.sourceCompatibility = '1.2'

        when:
        def ideaProject = buildIdeaProjectModel()

        then:
        ideaProject.javaSourceSettings.sourceLanguageLevel == JavaVersion.VERSION_1_2
        ideaProject.modules.find { it.name == 'root'}.javaSourceSettings.sourceLanguageLevel == JavaVersion.VERSION_1_2
        ideaProject.modules.find { it.name == 'root'}.javaSourceSettings.isSourceLanguageLevelInherited()
    }

    def "if the project source language level and the module source language level are not the same then the module source language level is not inherited"() {
        given:
        root.plugins.apply(JavaPlugin)
        root.idea.project.languageLevel = '1.2'
        root.sourceCompatibility = '1.3'

        when:
        def ideaProject = buildIdeaProjectModel()

        then:
        ideaProject.javaSourceSettings.sourceLanguageLevel == JavaVersion.VERSION_1_2
        ideaProject.modules.find { it.name == 'root'}.javaSourceSettings.sourceLanguageLevel == JavaVersion.VERSION_1_3
        !ideaProject.modules.find { it.name == 'root'}.javaSourceSettings.isSourceLanguageLevelInherited()
    }

    def "can handle multi project builds where no projects are Java projects with default configuration"() {
        when:
        def ideaProject = buildIdeaProjectModel()

        then:
        ideaProject.javaSourceSettings.sourceLanguageLevel == defaultIdeaPluginLanguageLevelForNonJavaProjects
        ideaProject.modules.find { it.name == 'root'}.javaSourceSettings.sourceLanguageLevel == defaultIdeaPluginLanguageLevelForNonJavaProjects
        ideaProject.modules.find { it.name == 'root'}.javaSourceSettings.sourceLanguageLevel == toJavaVersion(ideaProject.languageLevel)
        ideaProject.modules.find { it.name == 'child1'}.javaSourceSettings.sourceLanguageLevel == toJavaVersion(ideaProject.languageLevel)
        ideaProject.modules.find { it.name == 'child2'}.javaSourceSettings.sourceLanguageLevel == toJavaVersion(ideaProject.languageLevel)
        ideaProject.modules.find { it.name == 'root'}.javaSourceSettings.isSourceLanguageLevelInherited()
        ideaProject.modules.find { it.name == 'child1'}.javaSourceSettings.isSourceLanguageLevelInherited()
        ideaProject.modules.find { it.name == 'child2'}.javaSourceSettings.isSourceLanguageLevelInherited()
    }

    def "can handle multi project builds where no projects are Java projects with explicit configuration"() {
        given:
        root.idea.project.languageLevel = '1.2'

        when:
        def ideaProject = buildIdeaProjectModel()

        then:
        ideaProject.javaSourceSettings.sourceLanguageLevel == JavaVersion.VERSION_1_2
        ideaProject.modules.find { it.name == 'root'}.javaSourceSettings.sourceLanguageLevel == JavaVersion.VERSION_1_2
        ideaProject.modules.find { it.name == 'child1'}.javaSourceSettings.sourceLanguageLevel == JavaVersion.VERSION_1_2
        ideaProject.modules.find { it.name == 'child2'}.javaSourceSettings.sourceLanguageLevel == JavaVersion.VERSION_1_2
        ideaProject.modules.find { it.name == 'root'}.javaSourceSettings.isSourceLanguageLevelInherited()
        ideaProject.modules.find { it.name == 'child1'}.javaSourceSettings.isSourceLanguageLevelInherited()
        ideaProject.modules.find { it.name == 'child2'}.javaSourceSettings.isSourceLanguageLevelInherited()
    }

    def "can handle multi project builds with different source language levels"() {
        given:
        [root, child1, child2].each { it.plugins.apply(JavaPlugin) }
        root.idea.project.languageLevel = '1.3'
        root.sourceCompatibility = '1.1'
        child1.sourceCompatibility = '1.2'
        child2.sourceCompatibility = '1.3'

        when:
        def ideaProject = buildIdeaProjectModel()

        then:
        ideaProject.javaSourceSettings.sourceLanguageLevel == JavaVersion.VERSION_1_3
        ideaProject.modules.find { it.name == 'root'}.javaSourceSettings.sourceLanguageLevel == JavaVersion.VERSION_1_1
        ideaProject.modules.find { it.name == 'child1'}.javaSourceSettings.sourceLanguageLevel == JavaVersion.VERSION_1_2
        ideaProject.modules.find { it.name == 'child2'}.javaSourceSettings.sourceLanguageLevel == JavaVersion.VERSION_1_3
        !ideaProject.modules.find { it.name == 'root'}.javaSourceSettings.isSourceLanguageLevelInherited()
        !ideaProject.modules.find { it.name == 'child1'}.javaSourceSettings.isSourceLanguageLevelInherited()
        ideaProject.modules.find { it.name == 'child2'}.javaSourceSettings.isSourceLanguageLevelInherited()
    }

    def "can handle multi project builds where only some projects are java projects"() {
        given:
        root.plugins.apply(JavaPlugin)
        root.sourceCompatibility = '1.4'
        root.idea.project.languageLevel = '1.2'
        child1.plugins.apply(JavaPlugin)
        child1.sourceCompatibility = '1.3'

        when:
        def ideaProject = buildIdeaProjectModel()

        then:
        ideaProject.javaSourceSettings.sourceLanguageLevel == JavaVersion.VERSION_1_2
        ideaProject.modules.find { it.name == 'root'}.javaSourceSettings.sourceLanguageLevel == JavaVersion.VERSION_1_4
        ideaProject.modules.find { it.name == 'child1'}.javaSourceSettings.sourceLanguageLevel == JavaVersion.VERSION_1_3
        ideaProject.modules.find { it.name == 'child2'}.javaSourceSettings.sourceLanguageLevel == JavaVersion.VERSION_1_2
        !ideaProject.modules.find { it.name == 'root'}.javaSourceSettings.isSourceLanguageLevelInherited()
        !ideaProject.modules.find { it.name == 'child1'}.javaSourceSettings.isSourceLanguageLevelInherited()
        ideaProject.modules.find { it.name == 'child2'}.javaSourceSettings.isSourceLanguageLevelInherited()
    }

    def "can handle multi project builds where root project is not a java project but some children are"() {
        given:
        root.idea.project.languageLevel = '1.2'
        child1.plugins.apply(JavaPlugin)
        child1.sourceCompatibility = '1.3'

        when:
        def ideaProject = buildIdeaProjectModel()

        then:
        ideaProject.javaSourceSettings.sourceLanguageLevel == JavaVersion.VERSION_1_2
        ideaProject.modules.find { it.name == 'root'}.javaSourceSettings.sourceLanguageLevel == JavaVersion.VERSION_1_2
        ideaProject.modules.find { it.name == 'child1'}.javaSourceSettings.sourceLanguageLevel == JavaVersion.VERSION_1_3
        ideaProject.modules.find { it.name == 'child2'}.javaSourceSettings.sourceLanguageLevel == JavaVersion.VERSION_1_2
        ideaProject.modules.find { it.name == 'root'}.javaSourceSettings.isSourceLanguageLevelInherited()
        !ideaProject.modules.find { it.name == 'child1'}.javaSourceSettings.isSourceLanguageLevelInherited()
        ideaProject.modules.find { it.name == 'child2'}.javaSourceSettings.isSourceLanguageLevelInherited()
    }

    private DefaultIdeaProject buildIdeaProjectModel() {
        def builder = createIdeaModelBuilder()
        buildIdeaProject(builder, root)
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
