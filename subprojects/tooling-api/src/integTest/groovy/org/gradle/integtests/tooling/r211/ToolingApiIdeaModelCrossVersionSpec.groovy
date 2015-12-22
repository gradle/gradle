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

package org.gradle.integtests.tooling.r211
import org.gradle.api.JavaVersion
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.internal.jvm.Jvm
import org.gradle.tooling.model.UnsupportedMethodException
import org.gradle.tooling.model.idea.IdeaProject

@ToolingApiVersion(">=2.11")
@TargetGradleVersion(">=2.11")
class ToolingApiIdeaModelCrossVersionSpec extends ToolingApiSpecification {

    def setup(){
        settingsFile << "rootProject.name = 'root'"
    }

    @TargetGradleVersion(">=1.0-milestone-8 <2.11")
    def "older Gradle versions infer project source settings from default idea plugin language level"() {
        given:
        if (projectAppliesJavaPlugin) { buildFile << "apply plugin: 'java'"}

        when:
        def ideaProject = loadIdeaProjectModel()

        then:
        ideaProject.javaSourceSettings.sourceLanguageLevel == expectedSourceLanguageLevel
        ideaProject.javaSourceSettings.sourceLanguageLevel == toJavaVersion(ideaProject.languageLevel)

        where:
        projectAppliesJavaPlugin | expectedSourceLanguageLevel
        false                    | defaultIdeaPluginLanguageLevelForNonJavaProjects
        true                     | defaultIdeaPluginLanguageLevelForJavaProjects
    }

    @TargetGradleVersion(">=1.0-milestone-8 <2.11")
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
        ideaProject.javaSourceSettings.sourceLanguageLevel == JavaVersion.VERSION_1_3
        toJavaVersion(ideaProject.languageLevel) == JavaVersion.VERSION_1_3

        where:
        applyJavaPlugin << [false, true]
    }

    @TargetGradleVersion(">=1.0-milestone-8 <2.11")
    def "older Gradle version throw exception when querying idea module source settings"() {
        when:
        def ideaProject = loadIdeaProjectModel()
        ideaProject.modules.find { it.name == 'root' }.getJavaSourceSettings()

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

        ideaProject.javaSourceSettings.sourceLanguageLevel.isJava5()
        // modules
        ideaProject.modules.find { it.name == 'root' }.javaSourceSettings == null
        ideaProject.modules.find { it.name == 'child1' }.javaSourceSettings == null
        ideaProject.modules.find { it.name == 'child2' }.javaSourceSettings == null
    }

    def "can retrieve project and module source language level for multi project build"() {
        given:
        settingsFile << "\ninclude 'root', 'child1', 'child2', 'child3'"
        buildFile << """
            apply plugin: 'idea'
            idea {
                project {
                    languageLevel = '1.5'
                }
            }

            project(':child1') {
            }

            project(':child2') {
                apply plugin: 'java'
                sourceCompatibility = '1.2'
            }

            project(':child3') {
                apply plugin: 'java'
                sourceCompatibility = '1.5'
            }

        """

        when:
        def ideaProject = loadIdeaProjectModel()

        then:
        ideaProject.javaSourceSettings.sourceLanguageLevel.isJava5()
        ideaProject.modules.find { it.name == 'root' }.javaSourceSettings == null
        ideaProject.modules.find { it.name == 'child1' }.javaSourceSettings == null
        ideaProject.modules.find { it.name == 'child2' }.javaSourceSettings.sourceLanguageLevel == JavaVersion.VERSION_1_2
        !ideaProject.modules.find { it.name == 'child2' }.javaSourceSettings.isSourceLanguageLevelInherited()
        ideaProject.modules.find { it.name == 'child3' }.javaSourceSettings.sourceLanguageLevel == JavaVersion.VERSION_1_5
        ideaProject.modules.find { it.name == 'child3' }.javaSourceSettings.isSourceLanguageLevelInherited()
    }

    @TargetGradleVersion(">=1.0-milestone-8")
    def "can query target runtime for idea project"() {
        when:
        def ideaProject = loadIdeaProjectModel()

        then:
        ideaProject.javaSourceSettings.targetRuntime.javaVersion == Jvm.current().javaVersion
        ideaProject.javaSourceSettings.targetRuntime.homeDirectory == Jvm.current().javaHome
    }

    def "can query target runtime for idea modules"() {
        given:
        settingsFile << "\ninclude 'root', 'child1', 'child2', 'child3'"
        buildFile << """
            allprojects {
                apply plugin:'java'
                apply plugin:'idea'
                targetCompatibility = "1.5"
            }

        """

        when:
        def ideaProject = loadIdeaProjectModel()

        then:
        ideaProject.javaSourceSettings.targetRuntime.javaVersion == Jvm.current().javaVersion
        ideaProject.javaSourceSettings.targetRuntime.homeDirectory == Jvm.current().javaHome

        ideaProject.modules.find { it.name == 'root' }.javaSourceSettings.targetRuntime.javaVersion == Jvm.current().javaVersion
        ideaProject.modules.find { it.name == 'root' }.javaSourceSettings.targetRuntime.homeDirectory == Jvm.current().javaHome
        ideaProject.modules.find { it.name == 'root' }.javaSourceSettings.targetRuntimeInherited == true

        ideaProject.modules.find { it.name == 'child1' }.javaSourceSettings.targetRuntime.javaVersion == Jvm.current().javaVersion
        ideaProject.modules.find { it.name == 'child1' }.javaSourceSettings.targetRuntime.homeDirectory == Jvm.current().javaHome
        ideaProject.modules.find { it.name == 'child1' }.javaSourceSettings.targetRuntimeInherited == true

        ideaProject.modules.find { it.name == 'child2' }.javaSourceSettings.targetRuntime.javaVersion == Jvm.current().javaVersion
        ideaProject.modules.find { it.name == 'child2' }.javaSourceSettings.targetRuntime.homeDirectory == Jvm.current().javaHome
        ideaProject.modules.find { it.name == 'child2' }.javaSourceSettings.targetRuntimeInherited == true

        ideaProject.modules.find { it.name == 'child3' }.javaSourceSettings.targetRuntime.javaVersion == Jvm.current().javaVersion
        ideaProject.modules.find { it.name == 'child3' }.javaSourceSettings.targetRuntime.homeDirectory == Jvm.current().javaHome
        ideaProject.modules.find { it.name == 'child3' }.javaSourceSettings.targetRuntimeInherited == true
    }

    def "can query target bytecode level for idea project and modules"() {
        given:
        settingsFile << "\ninclude 'root', 'child1', 'child2', 'child3'"
        buildFile << """
            allprojects {
                apply plugin:'java'
                apply plugin:'idea'
                targetCompatibility = "1.5"
            }

        """

        when:
        def ideaProject = loadIdeaProjectModel()

        then:
        ideaProject.javaSourceSettings.targetBytecodeLevel == JavaVersion.VERSION_1_5
        ideaProject.modules.find { it.name == 'root' }.javaSourceSettings.targetBytecodeLevel == JavaVersion.VERSION_1_5
        ideaProject.modules.find { it.name == 'child1' }.javaSourceSettings.targetBytecodeLevel == JavaVersion.VERSION_1_5
        ideaProject.modules.find { it.name == 'child2' }.javaSourceSettings.targetBytecodeLevel == JavaVersion.VERSION_1_5
        ideaProject.modules.find { it.name == 'child3' }.javaSourceSettings.targetBytecodeLevel == JavaVersion.VERSION_1_5

        and: "one global target bytecode level"
        ideaProject.modules.find { it.name == 'root' }.javaSourceSettings.targetBytecodeLevelInherited == true
        ideaProject.modules.find { it.name == 'child1' }.javaSourceSettings.targetBytecodeLevelInherited == true
        ideaProject.modules.find { it.name == 'child2' }.javaSourceSettings.targetBytecodeLevelInherited == true
        ideaProject.modules.find { it.name == 'child3' }.javaSourceSettings.targetBytecodeLevelInherited == true
    }

    def "can have different target bytecode level among modules"() {
        given:
        settingsFile << "\ninclude 'root', 'child1', ':child2:child3', 'child4'"
        buildFile << """
            apply plugin:'java'
            targetCompatibility = "1.5"

            project(':child1') {
                apply plugin:'java'
                targetCompatibility = "1.5"
            }

            project(':child2') {
                apply plugin:'java'
                targetCompatibility = '1.6'
            }

            project(':child2:child3') {
                apply plugin:'java'
                targetCompatibility = '1.7'
            }
            project(':child4') {
            }
        """

        when:
        def ideaProject = loadIdeaProjectModel()

        then:
        ideaProject.javaSourceSettings.targetBytecodeLevel == JavaVersion.VERSION_1_7
        ideaProject.modules.find { it.name == 'root' }.javaSourceSettings.targetBytecodeLevel == JavaVersion.VERSION_1_5
        ideaProject.modules.find { it.name == 'child1' }.javaSourceSettings.targetBytecodeLevel == JavaVersion.VERSION_1_5
        ideaProject.modules.find { it.name == 'child2' }.javaSourceSettings.targetBytecodeLevel == JavaVersion.VERSION_1_6
        ideaProject.modules.find { it.name == 'child3' }.javaSourceSettings.targetBytecodeLevel == JavaVersion.VERSION_1_7
        ideaProject.modules.find { it.name == 'child4' }.javaSourceSettings == null

        and:
        ideaProject.modules.find { it.name == 'root' }.javaSourceSettings.targetBytecodeLevelInherited == false
        ideaProject.modules.find { it.name == 'child1' }.javaSourceSettings.targetBytecodeLevelInherited == false
        ideaProject.modules.find { it.name == 'child2' }.javaSourceSettings.targetBytecodeLevelInherited == false
        ideaProject.modules.find { it.name == 'child3' }.javaSourceSettings.targetBytecodeLevelInherited == true
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
        // see IdeaPlugin#configureIdeaProjectForJava(Project)
        getTargetDist().getVersion().toString().startsWith("1.0-milestone-8") ? JavaVersion.VERSION_1_5 : JavaVersion.current()
    }
}
