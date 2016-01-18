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
        ideaProject.javaSourceSettings.languageLevel == defaultIdeaPluginLanguageLevelForNonJavaProjects
        ideaProject.javaSourceSettings.languageLevel == toJavaVersion(ideaProject.languageLevel)
    }

    def "project source language level matches idea plugin language level for jvm projects with default configuration"() {
        given:
        root.plugins.apply(pluginType)

        when:
        def ideaProject = buildIdeaProjectModel()

        then:
        ideaProject.javaSourceSettings.languageLevel == defaultIdeaPluginLanguageLevelForJavaProjects
        ideaProject.javaSourceSettings.languageLevel == toJavaVersion(ideaProject.languageLevel)

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
        ideaProject.javaSourceSettings.languageLevel.toString() == sourceLanguageLevel
        ideaProject.javaSourceSettings.languageLevel == toJavaVersion(ideaProject.languageLevel)

        where:
        sourceLanguageLevel << ['1.1', '1.2', '1.3', '1.4', '1.5', '1.6', '1.7', '1.8', '1.9']
    }

    def "module java source settings are null for non-jvm projects"() {
        when:
        def ideaProject = buildIdeaProjectModel()

        then:
        ideaProject.modules.find { it.name == 'root'}.javaSourceSettings == null
    }

    def "module source language level matches sourceCompatibility for java projects"() {
        given:
        root.plugins.apply(JavaPlugin)
        child1.plugins.apply(JavaPlugin)
        root.sourceCompatibility = '1.9'
        child1.sourceCompatibility = sourceCompatibility

        when:
        def ideaProject = buildIdeaProjectModel()

        then:
        ideaProject.modules.find { it.name == 'child1'}.javaSourceSettings.languageLevel.toString() == sourceCompatibility

        where:
        sourceCompatibility << ['1.1', '1.2', '1.3', '1.4', '1.5', '1.6', '1.7', '1.8']
    }

    def "module language level is not inherited for non equal project and module language level"() {
        given:
        root.plugins.apply(JavaPlugin)
        child1.plugins.apply(JavaPlugin)
        root.sourceCompatibility = '1.2'
        child1.sourceCompatibility = '1.3'
        when:
        def ideaProject = buildIdeaProjectModel()

        then:
        ideaProject.javaSourceSettings.languageLevel == JavaVersion.VERSION_1_3
        ideaProject.modules.find { it.name == 'root'}.javaSourceSettings.languageLevel == JavaVersion.VERSION_1_2
    }

    def "explicit project language level results in inherited module language level"() {
        given:
        root.plugins.apply(JavaPlugin)
        child1.plugins.apply(JavaPlugin)
        child2.plugins.apply(JavaPlugin)
        root.idea.project.languageLevel = '1.2'
        root.sourceCompatibility = '1.3'
        child1.sourceCompatibility = '1.4'
        child2.sourceCompatibility = '1.5'

        when:
        def ideaProject = buildIdeaProjectModel()

        then:
        ideaProject.javaSourceSettings.languageLevel == JavaVersion.VERSION_1_2
        ideaProject.modules.find { it.name == 'root'}.javaSourceSettings.languageLevel == null
        ideaProject.modules.find { it.name == 'child1'}.javaSourceSettings.languageLevel == null
        ideaProject.modules.find { it.name == 'child2'}.javaSourceSettings.languageLevel == null
    }

    def "can handle multi project builds where no projects are Java projects"() {
        when:
        def ideaProject = buildIdeaProjectModel()

        then:
        ideaProject.javaSourceSettings.languageLevel == defaultIdeaPluginLanguageLevelForNonJavaProjects
        ideaProject.modules.find { it.name == 'root'}.javaSourceSettings == null
        ideaProject.modules.find { it.name == 'child1'}.javaSourceSettings == null
        ideaProject.modules.find { it.name == 'child2'}.javaSourceSettings == null
    }

    def "can handle multi project builds with different source language levels"() {
        given:
        [root, child1, child2].each { it.plugins.apply(JavaPlugin) }
        root.sourceCompatibility = '1.3'
        child1.sourceCompatibility = '1.2'
        child2.sourceCompatibility = '1.3'

        when:
        def ideaProject = buildIdeaProjectModel()

        then:
        ideaProject.javaSourceSettings.languageLevel == JavaVersion.VERSION_1_3
        ideaProject.modules.find { it.name == 'root'}.javaSourceSettings.languageLevel == null
        ideaProject.modules.find { it.name == 'child1'}.javaSourceSettings.languageLevel == JavaVersion.VERSION_1_2
        ideaProject.modules.find { it.name == 'child2'}.javaSourceSettings.languageLevel == null
    }

    def "can handle multi project builds where only some projects are java projects"() {
        given:
        root.plugins.apply(JavaPlugin)
        root.sourceCompatibility = '1.4'
        child1.plugins.apply(JavaPlugin)
        child1.sourceCompatibility = '1.3'

        when:
        def ideaProject = buildIdeaProjectModel()

        then:
        ideaProject.javaSourceSettings.languageLevel == JavaVersion.VERSION_1_4
        ideaProject.modules.find { it.name == 'root'}.javaSourceSettings.languageLevel == null
        ideaProject.modules.find { it.name == 'child1'}.javaSourceSettings.languageLevel == JavaVersion.VERSION_1_3
        ideaProject.modules.find { it.name == 'child2'}.javaSourceSettings == null
    }

    def "project target runtime matches current jvm in use"() {
        when:
        [root, child1, child2].each {
            it.plugins.apply(JavaPlugin)
        }
        def ideaProject = buildIdeaProjectModel()
        then:
        ideaProject.javaSourceSettings.targetRuntime.homeDirectory == Jvm.current().javaHome
        ideaProject.javaSourceSettings.targetRuntime.javaVersion == Jvm.current().javaVersion
        ideaProject.modules.find { it.name == 'root'}.javaSourceSettings.javaSDK == null
        ideaProject.modules.find { it.name == 'child1'}.javaSourceSettings.javaSDK == null
        ideaProject.modules.find { it.name == 'child2'}.javaSourceSettings.javaSDK == null
    }

    def "synched module bytecode level marked as inherited"() {

        when:
        [root, child1, child2].each {
            it.plugins.apply(JavaPlugin)
            it.targetCompatibility = "1.5"
        }
        def ideaProject = buildIdeaProjectModel()

        then:
        ideaProject.javaSourceSettings.targetBytecodeVersion == JavaVersion.VERSION_1_5
        ideaProject.modules.find { it.name == 'root'}.javaSourceSettings.targetBytecodeVersion == null
        ideaProject.modules.find { it.name == 'child1'}.javaSourceSettings.targetBytecodeVersion == null
        ideaProject.modules.find { it.name == 'child2'}.javaSourceSettings.targetBytecodeVersion == null
    }

    def "can have mixed bytecode level"() {
        when:
        [root, child1, child2].each {
            it.plugins.apply(JavaPlugin)
        }
        root.targetCompatibility = "1.5"
        child1.targetCompatibility = "1.6"
        child2.targetCompatibility = "1.7"

        def ideaProject = buildIdeaProjectModel()

        then:
        ideaProject.javaSourceSettings.targetBytecodeVersion == JavaVersion.VERSION_1_7
        ideaProject.modules.find { it.name == 'root'}.javaSourceSettings.targetBytecodeVersion == JavaVersion.VERSION_1_5
        ideaProject.modules.find { it.name == 'child1'}.javaSourceSettings.targetBytecodeVersion == JavaVersion.VERSION_1_6
        ideaProject.modules.find { it.name == 'child2'}.javaSourceSettings.targetBytecodeVersion == null
    }

    def "non jvm modules have no java sourceSettings applied"() {
        when:
        [root, child1].each {
            it.plugins.apply(JavaPlugin)
        }
        root.targetCompatibility = "1.6"
        child1.targetCompatibility = "1.7"

        def ideaProject = buildIdeaProjectModel()

        then:
        ideaProject.javaSourceSettings.targetBytecodeVersion == JavaVersion.VERSION_1_7

        ideaProject.modules.find { it.name == 'root'}.javaSourceSettings.targetBytecodeVersion == JavaVersion.VERSION_1_6
        ideaProject.modules.find { it.name == 'child1'}.javaSourceSettings.targetBytecodeVersion == null
        ideaProject.modules.find { it.name == 'child2'}.javaSourceSettings == null
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
