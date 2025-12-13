/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.integtests.tooling.r930

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.test.fixtures.file.TestFile
import org.gradle.tooling.BuildAction
import org.gradle.tooling.BuildActionExecuter
import org.gradle.tooling.BuildController
import org.gradle.tooling.model.gradle.GradleBuild
import org.gradle.util.GradleVersion

import java.util.function.Consumer

@ToolingApiVersion('>=9.3')
@TargetGradleVersion('>=9.3')
class ResilientGradleBuildSyncCrossVersionSpec extends ToolingApiSpecification {
    static final String RESILIENT_MODEL_TRUE = "-Dorg.gradle.internal.resilient-model-building=true"
    static final String BROKEN_SETTINGS_CONTENT = "broken settings file content!!!"
    static final String BROKEN_BUILD_CONTENT = "broken build file content!!!"
    static final String ISOLATED_PROJECTS_FLAG = "-Dorg.gradle.internal.isolated-projects.tooling=true"
    static final String UNSAFE_ISOLATED_PROJECTS_FLAG = "-Dorg.gradle.unsafe.isolated-projects=true"

    def setup() {
        settingsFile.delete() // This is automatically created by `ToolingApiSpecification`
    }

    BuildActionResult runFetchModelAction(Consumer<BuildActionExecuter<BuildActionResult>> configurer = {}) {
        succeeds {
            def action = action(new FetchModelAction())
            if (targetVersion < GradleVersion.version("9.4.0")){
                // In Gradle 9.4+, resilient model fetching is enabled by default for all "fetch" actions.
                // For earlier versions, we explicitly enable it with the flag to ensure consistent behavior.
                action.withArguments(RESILIENT_MODEL_TRUE)
            }
            configurer.accept(action)
            action.run()
        }
    }

    def "should fetch root project model when settings #description"() {
        given:
        settingsKotlinFile << """
            rootProject.name = "root"
            $causeOfFailure
        """

        when:
        def result = runFetchModelAction()

        then:
        result.failures.size() == 1
        result.failures[0].contains(expectedFailure)
        result.model.includedBuilds.empty
        result.model.editableBuilds.empty
        result.model.rootProject.projectIdentifier.projectPath == ":"
        // If Settings scripts fails to evaluate the name falls back to directory name
        result.model.rootProject.name == expectedRootProjectName ?: settingsKotlinFile.parentFile.name
        result.model.rootProject.projectIdentifier.buildIdentifier.rootDir == settingsKotlinFile.parentFile
        result.model.projects == [result.model.rootProject] as Set

        where:
        description         | causeOfFailure                                         | expectedRootProjectName | expectedFailure
        "compilation error" | "broken settings file content!!!"                      | null                    | "Script compilation error"
        "runtime error"     | "throw GradleException(\"Gradle exception boom !!!\")" | "root"                  | "Gradle exception boom !!!"
    }

    def "should fetch root project model despite broken root build file with #description"() {
        given:
        createRootProject()
        buildFileKts << """
            $causeOfFailure
        """

        when:
        def result = runFetchModelAction()

        then:
        result.failures.isEmpty()  // it did not fail
        result.model.includedBuilds.empty
        result.model.editableBuilds.empty
        result.model.rootProject.projectIdentifier.projectPath == ":"
        result.model.rootProject.name == "root"
        result.model.rootProject.projectIdentifier.buildIdentifier.rootDir == settingsKotlinFile.parentFile
        result.model.projects == [result.model.rootProject] as Set

        where:
        description         | causeOfFailure
        "compilation error" | "broken gradle script content!!!"
        "runtime error"     | "throw GradleException(\"Gradle exception boom !!!\")"
    }

    def "should fetch root project and included build models despite broken included build files"() {
        given:
        createRootProject()
        createIncludedBuild("included1")
        createIncludedBuild("included2")

        when:
        def result = runFetchModelAction()

        then:
        result.failures.isEmpty()
        result.model.includedBuilds.size() == 2
    }

    def "should fetch root project and included plugin project models despite broken included build file"() {
        given:
        settingsKotlinFile << """
            pluginManagement {
                includeBuild("included-plugin")
            }
            rootProject.name = "root"
        """

        def includedPlugin = createDirs("included-plugin").get(0)
        includedPlugin.file(settingsKotlinFileName) << """rootProject.name = "included-plugin" """
        blowUpBuildGradleKts(includedPlugin)

        when:
        def result = runFetchModelAction()

        then:
        result.failures.isEmpty()
        result.model.rootProject.projectIdentifier.projectPath == ":"
        result.model.rootProject.name == "root"
        result.model.projects == [result.model.rootProject] as Set

        // Check included and editable builds
        result.model.includedBuilds.size() == 1
        result.model.editableBuilds.size() == 1
        result.model.includedBuilds[0].rootProject.projectIdentifier.projectPath == ":"
        result.model.includedBuilds[0].rootProject.name == "included-plugin"
        result.model.includedBuilds[0].projects == [result.model.includedBuilds[0].rootProject] as Set
        result.model.editableBuilds == result.model.includedBuilds
    }

    def "should fetch root build and included build with a broken settings convention plugin"() {
        given:
        settingsKotlinFile << """
            pluginManagement {
                includeBuild("build-logic")
            }
            rootProject.name = "root"
            plugins {
                id("build-logic")
            }
            include("a")
        """
        def included = file("build-logic")
        included.file("settings.gradle.kts") << """
            rootProject.name = "build-logic"
            pluginManagement {
                $repositoriesBlock
            }
        """
        included.file("build.gradle.kts") << """
            plugins {
                `kotlin-dsl`
            }
            repositories {
                mavenCentral()
                gradlePluginPortal()
            }
        """
        included.file("src/main/kotlin/build-logic.settings.gradle.kts") << """
            broken !!!
        """
        file("a/build.gradle.kts") << """
        """

        when:
        def result = runFetchModelAction()

        then:
        result.failures.size() == 1
        result.model.rootProject.projectIdentifier.projectPath == ":"
        // Name was not setup due to failure when applying a plugin
        result.model.rootProject.name == settingsKotlinFile.parentFile.name
        // No other projects were configured due to settings failure
        result.model.projects == [result.model.rootProject] as Set

        // Check included and editable builds
        result.model.includedBuilds.size() == 0
        result.model.editableBuilds.size() == 1
        result.model.editableBuilds[0].rootProject.projectIdentifier.projectPath == ":"
        result.model.editableBuilds[0].rootProject.name == "build-logic"
        result.model.editableBuilds[0].projects.size() == 1
        result.model.editableBuilds[0].projects == [result.model.editableBuilds[0].rootProject] as Set
    }

    def "should fetch root project and included build models despite broken included settings files"() {
        given:
        createRootProject()

        createFailingSettingsIncludedProject("included1")
        // The second included build will be skipped due to failure in the first one
        createFailingSettingsIncludedProject("included2")

        when:
        def model = runFetchModelAction()

        then:
        model.failures.size() == 1
        model.failures.toString().contains("Script compilation error")
        model.model.includedBuilds.size() == 1
        model.model.includedBuilds[0].buildIdentifier.rootDir == file("included1")
    }

    def "should return failure when caching models with isolated projects"() {
        given:
        def intermediateCaching = [
            ISOLATED_PROJECTS_FLAG,
            UNSAFE_ISOLATED_PROJECTS_FLAG
        ]
        createRootProject()
        createFailingSettingsIncludedProject("included")


        when:
        def model = runFetchModelAction {
            it.addArguments(intermediateCaching)
        }

        then:
        model.failures.toString().contains("Script compilation error")
        !model.model.includedBuilds.isEmpty()

        when:
        model = runFetchModelAction {
            it.addArguments(intermediateCaching)
        }

        then:
        model.failures.toString().contains("Script compilation error")
        !model.model.includedBuilds.isEmpty()
    }

    TestFile blowUpSettings(TestFile included = null) {
        createBrokenFile(included, settingsKotlinFileName, BROKEN_SETTINGS_CONTENT)
    }

    TestFile blowUpBuildGradleKts(TestFile included = null) {
        createBrokenFile(included, defaultBuildKotlinFileName, BROKEN_BUILD_CONTENT)
    }

    private TestFile createBrokenFile(TestFile dir, String fileName, String content) {
        if (dir == null) {
            def targetFile = file(fileName)
            targetFile << content
            return targetFile
        } else {
            def targetFile = dir.file(fileName)
            targetFile << content
            return targetFile
        }
    }

    def createIncludedBuild(String includedBuildName) {
        def included = file(includedBuildName)
        settingsKotlinFile << """
            includeBuild("${includedBuildName}")
        """
        included.file(settingsKotlinFileName) << """
            rootProject.name = "${includedBuildName}"
        """
        blowUpBuildGradleKts(included)
    }

    TestFile createRootProject() {
        settingsKotlinFile << """
            rootProject.name = "root"
        """
    }

    def createFailingSettingsIncludedProject(String includedProjectName) {
        settingsKotlinFile << """
            includeBuild("${includedProjectName}")
        """
        def included = file(includedProjectName)
        blowUpSettings(included)
        blowUpBuildGradleKts(included)
    }

    static class BuildActionResult implements Serializable {
        final GradleBuild model
        final List<String> failures

        BuildActionResult(GradleBuild model, List<String> failures) {
            this.model = model
            this.failures = failures
        }
    }

    static class FetchModelAction implements BuildAction<BuildActionResult>, Serializable {
        @Override
        BuildActionResult execute(BuildController controller) {
            def result = controller.fetch(GradleBuild, null, null)
            return new BuildActionResult(result.getModel(), result.getFailures().collect { it.message })
        }
    }
}
