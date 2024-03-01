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

package org.gradle.plugins.ide.tooling.r211

import org.gradle.api.JavaVersion
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.tooling.model.UnsupportedMethodException
import org.gradle.tooling.model.idea.IdeaProject

import static org.gradle.plugins.ide.tooling.r210.ConventionsExtensionsCrossVersionFixture.javaSourceCompatibility
import static org.gradle.plugins.ide.tooling.r210.ConventionsExtensionsCrossVersionFixture.javaTargetCompatibility

@TargetGradleVersion(">=2.11")
class ToolingApiIdeaModelCrossVersionSpec extends ToolingApiSpecification {

    def setup() {
        settingsFile << "rootProject.name = 'root'"
    }

    @TargetGradleVersion(">=2.6 <2.11")
    def "older Gradle versions infer project source settings from default idea plugin language level"() {
        given:
        if (projectAppliesJavaPlugin) {
            buildFile << "apply plugin: 'java'"
        }

        when:
        def ideaProject = loadIdeaProjectModel()

        then:
        ideaProject.javaLanguageSettings.languageLevel == expectedSourceLanguageLevel
        ideaProject.javaLanguageSettings.languageLevel == toJavaVersion(ideaProject.languageLevel)

        where:
        projectAppliesJavaPlugin | expectedSourceLanguageLevel
        false                    | defaultIdeaPluginLanguageLevelForNonJavaProjects
        true                     | defaultIdeaPluginLanguageLevelForJavaProjects
    }

    @TargetGradleVersion(">=2.6 <2.11")
    def "older Gradle versions infer project source settings from configured idea plugin language level"() {
        given:
        buildFile << """
            apply plugin: 'idea'
            ${applyJavaPlugin ? "apply plugin: 'java'" : ""}
            idea {
                project {
                    languageLevel = '1.3'
                }
            }
        """

        when:
        def ideaProject = loadIdeaProjectModel()

        then:
        ideaProject.javaLanguageSettings.languageLevel == JavaVersion.VERSION_1_3
        toJavaVersion(ideaProject.languageLevel) == JavaVersion.VERSION_1_3

        where:
        applyJavaPlugin << [false, true]
    }

    @TargetGradleVersion(">=2.6 <2.11")
    def "older Gradle version throw exception when querying idea module java settings"() {
        when:
        def ideaProject = loadIdeaProjectModel()
        ideaProject.modules.find { it.name == 'root' }.getJavaLanguageSettings()

        then:
        thrown(UnsupportedMethodException)
    }

    @TargetGradleVersion(">=2.6 <2.11")
    def "older Gradle version throws exception when querying idea project java bytecode version or jdk"() {
        given:
        def ideaProject = loadIdeaProjectModel()

        when:
        ideaProject.javaLanguageSettings.getTargetBytecodeVersion()

        then:
        thrown(UnsupportedMethodException)

        when:
        ideaProject.javaLanguageSettings.getJdk()

        then:
        thrown(UnsupportedMethodException)
    }

    def "java source settings are null for non java modules"() {
        given:
        settingsFile << "\ninclude 'root', 'child1', 'child2'"
        buildFile << """
            apply plugin: 'idea'
            idea {
                project {
                    languageLevel = '1.5'
                }
            }
        """

        when:
        def ideaProject = loadIdeaProjectModel()

        then:

        ideaProject.javaLanguageSettings.languageLevel.isJava5()
        // modules
        ideaProject.modules.find { it.name == 'root' }.javaLanguageSettings == null
        ideaProject.modules.find { it.name == 'child1' }.javaLanguageSettings == null
        ideaProject.modules.find { it.name == 'child2' }.javaLanguageSettings == null
    }

    def "can retrieve project and module language level for multi project build"() {
        given:
        settingsFile << "\ninclude 'root', 'child1', 'child2', 'child3'"
        buildFile << """
            apply plugin: 'idea'

            project(':child1') {
            }

            project(':child2') {
                apply plugin: 'java'
                ${javaSourceCompatibility(targetVersion, JavaVersion.VERSION_1_2)}
            }

            project(':child3') {
                apply plugin: 'java'
                ${javaSourceCompatibility(targetVersion, JavaVersion.VERSION_1_5)}
            }

        """

        when:
        def ideaProject = loadIdeaProjectModel()

        then:
        ideaProject.javaLanguageSettings.languageLevel == JavaVersion.VERSION_1_5
        ideaProject.modules.find { it.name == 'root' }.javaLanguageSettings == null
        ideaProject.modules.find { it.name == 'child1' }.javaLanguageSettings == null
        ideaProject.modules.find { it.name == 'child2' }.javaLanguageSettings.languageLevel == JavaVersion.VERSION_1_2
        ideaProject.modules.find { it.name == 'child3' }.javaLanguageSettings.languageLevel == null // inherited
    }

    @TargetGradleVersion("=2.11")
    def "explicit idea project language level overrules sourceCompatibility settings"() {
        given:
        settingsFile << "\ninclude 'root', 'child1', 'child2', 'child3'"
        buildFile << """
            apply plugin: 'idea'

            idea {
                project {
                    languageLevel = 1.7
                }
            }

            project(':child1') {
            }

            project(':child2') {
                apply plugin: 'java'
                ${javaSourceCompatibility(targetVersion, JavaVersion.VERSION_1_2)}
            }

            project(':child3') {
                apply plugin: 'java'
                ${javaSourceCompatibility(targetVersion, JavaVersion.VERSION_1_5)}
            }

        """

        when:
        def ideaProject = loadIdeaProjectModel()

        then:
        ideaProject.javaLanguageSettings.languageLevel == JavaVersion.VERSION_1_7
        ideaProject.modules.find { it.name == 'root' }.javaLanguageSettings == null
        ideaProject.modules.find { it.name == 'child1' }.javaLanguageSettings == null
        ideaProject.modules.find { it.name == 'child2' }.javaLanguageSettings.languageLevel == null
        ideaProject.modules.find { it.name == 'child3' }.javaLanguageSettings.languageLevel == null
    }

    def "can query java sdk for idea project"() {
        given:
        buildFile << """
apply plugin: 'java'

description = org.gradle.internal.jvm.Jvm.current().javaHome.toString()
"""
        when:
        def ideaProject = loadIdeaProjectModel()
        def gradleProject = ideaProject.modules.find({ it.name == 'root' }).gradleProject

        then:
        ideaProject.javaLanguageSettings.jdk != null
        ideaProject.javaLanguageSettings.jdk.javaVersion == JavaVersion.current()
        ideaProject.javaLanguageSettings.jdk.javaHome.toString() == gradleProject.description
    }

    def "module java sdk overwrite always null"() {
        given:
        settingsFile << "\ninclude 'root', 'child1', 'child2', 'child3'"
        buildFile << """
            allprojects {
                apply plugin:'java'
                apply plugin:'idea'
                ${javaTargetCompatibility(targetVersion, JavaVersion.VERSION_1_5)}
            }

        """

        when:
        def ideaProject = loadIdeaProjectModel()

        then:
        ideaProject.javaLanguageSettings.jdk.javaVersion == JavaVersion.current()
        ideaProject.javaLanguageSettings.jdk.javaHome

        ideaProject.modules.find { it.name == 'root' }.javaLanguageSettings.jdk == null
        ideaProject.modules.find { it.name == 'child1' }.javaLanguageSettings.jdk == null
        ideaProject.modules.find { it.name == 'child2' }.javaLanguageSettings.jdk == null
        ideaProject.modules.find { it.name == 'child3' }.javaLanguageSettings.jdk == null
    }

    def "can query target bytecode version for idea project and modules"() {
        given:
        settingsFile << "\ninclude 'root', 'child1', 'child2', 'child3'"
        buildFile << """
            allprojects {
                apply plugin:'java'
                apply plugin:'idea'
                ${javaTargetCompatibility(targetVersion, JavaVersion.VERSION_1_5)}
            }

        """

        when:
        def ideaProject = loadIdeaProjectModel()

        then:
        ideaProject.javaLanguageSettings.targetBytecodeVersion == JavaVersion.VERSION_1_5
        ideaProject.modules.find { it.name == 'root' }.javaLanguageSettings.targetBytecodeVersion == null
        ideaProject.modules.find { it.name == 'child1' }.javaLanguageSettings.targetBytecodeVersion == null
        ideaProject.modules.find { it.name == 'child2' }.javaLanguageSettings.targetBytecodeVersion == null
        ideaProject.modules.find { it.name == 'child3' }.javaLanguageSettings.targetBytecodeVersion == null
    }

    def "can have different target bytecode version among modules"() {
        given:
        settingsFile << "\ninclude 'root', 'child1', ':child2:child3', 'child4'"
        buildFile << """
            apply plugin:'java'
            ${javaTargetCompatibility(targetVersion, JavaVersion.VERSION_1_5)}

            project(':child1') {
                apply plugin:'java'
                ${javaTargetCompatibility(targetVersion, JavaVersion.VERSION_1_5)}
            }

            project(':child2') {
                apply plugin:'java'
                ${javaTargetCompatibility(targetVersion, JavaVersion.VERSION_1_6)}
            }

            project(':child2:child3') {
                apply plugin:'java'
                ${javaTargetCompatibility(targetVersion, JavaVersion.VERSION_1_7)}
            }
            project(':child4') {
            }
        """

        when:
        def ideaProject = loadIdeaProjectModel()

        then:
        ideaProject.javaLanguageSettings.targetBytecodeVersion == JavaVersion.VERSION_1_7
        ideaProject.modules.find { it.name == 'root' }.javaLanguageSettings.targetBytecodeVersion == JavaVersion.VERSION_1_5
        ideaProject.modules.find { it.name == 'child1' }.javaLanguageSettings.targetBytecodeVersion == JavaVersion.VERSION_1_5
        ideaProject.modules.find { it.name == 'child2' }.javaLanguageSettings.targetBytecodeVersion == JavaVersion.VERSION_1_6
        ideaProject.modules.find { it.name == 'child3' }.javaLanguageSettings.targetBytecodeVersion == null
        ideaProject.modules.find { it.name == 'child4' }.javaLanguageSettings == null
    }

    private IdeaProject loadIdeaProjectModel() {
        withConnection { connection -> connection.getModel(IdeaProject) }
    }

    private JavaVersion toJavaVersion(ideaLanguageLevel) {
        JavaVersion.valueOf(ideaLanguageLevel.level.replaceFirst("JDK", "VERSION"));
    }

    private JavaVersion getDefaultIdeaPluginLanguageLevelForNonJavaProjects() {
        JavaVersion.VERSION_1_6 // see IdeaPlugin#configureIdeaProject(Project)
    }

    private JavaVersion getDefaultIdeaPluginLanguageLevelForJavaProjects() {
        return JavaVersion.current()
    }
}
