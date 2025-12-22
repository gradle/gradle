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
import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.internal.jvm.Jvm
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions
import org.gradle.tooling.model.idea.IdeaProject
import org.junit.Assume

import static org.gradle.plugins.ide.tooling.r210.ConventionsExtensionsCrossVersionFixture.javaSourceCompatibility
import static org.gradle.plugins.ide.tooling.r210.ConventionsExtensionsCrossVersionFixture.javaTargetCompatibility

class ToolingApiIdeaModelCrossVersionSpec extends ToolingApiSpecification {

    def setup() {
        settingsFile << "rootProject.name = 'root'"
    }

    def "java source settings are null for non java modules"() {
        given:
        includeProjects("root","child1", "child2")
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
        includeProjects("root","child1", "child2", "child3")
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

    @Requires(value = [IntegTestPreconditions.Java17HomeAvailable, IntegTestPreconditions.Java21HomeAvailable, IntegTestPreconditions.NotEmbeddedExecutor])
    def "can query java sdk for idea project"() {
        Assume.assumeTrue("Target Gradle version supports running with Java " + jvm.javaVersionMajor, targetDist.daemonWorksWith(jvm.javaVersionMajor))

        given:
        buildFile << """
apply plugin: 'java'

description = org.gradle.internal.jvm.Jvm.current().javaHome.toString()
"""
        when:
        def ideaProject = loadIdeaProjectModel(jvm)
        def gradleProject = ideaProject.modules.find({ it.name == 'root' }).gradleProject

        then:
        ideaProject.javaLanguageSettings.jdk != null
        ideaProject.javaLanguageSettings.jdk.javaVersion == JavaVersion.toVersion(jvm.javaVersion.majorVersion)
        ideaProject.javaLanguageSettings.jdk.javaHome.toString() == gradleProject.description

        where:
        jvm << [AvailableJavaHomes.jdk17, AvailableJavaHomes.jdk21]
    }

    @Requires(value = [IntegTestPreconditions.Java17HomeAvailable, IntegTestPreconditions.Java21HomeAvailable, IntegTestPreconditions.NotEmbeddedExecutor])
    def "module java sdk overwrite always null"() {
        Assume.assumeTrue("Target Gradle version supports running with Java " + jvm.javaVersionMajor, targetDist.daemonWorksWith(jvm.javaVersionMajor))

        given:
        includeProjects("root","child1", "child2", "child3")
        buildFile << """
            allprojects {
                apply plugin:'java'
                apply plugin:'idea'
                ${javaTargetCompatibility(targetVersion, JavaVersion.VERSION_1_5)}
            }

        """

        when:
        def ideaProject = loadIdeaProjectModel(jvm)

        then:
        ideaProject.javaLanguageSettings.jdk.javaVersion == JavaVersion.toVersion(jvm.javaVersion.majorVersion)
        ideaProject.javaLanguageSettings.jdk.javaHome

        ideaProject.modules.find { it.name == 'root' }.javaLanguageSettings.jdk == null
        ideaProject.modules.find { it.name == 'child1' }.javaLanguageSettings.jdk == null
        ideaProject.modules.find { it.name == 'child2' }.javaLanguageSettings.jdk == null
        ideaProject.modules.find { it.name == 'child3' }.javaLanguageSettings.jdk == null

        where:
        jvm << [AvailableJavaHomes.jdk17, AvailableJavaHomes.jdk21]
    }

    def "can query target bytecode version for idea project and modules"() {
        given:
        includeProjects("root","child1", "child2", "child3")
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
        includeProjects("root","child1", "child2:child3", "child4")
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

    private IdeaProject loadIdeaProjectModel(Jvm jvm = null) {
        loadToolingModel(IdeaProject, jvm)
    }
}
