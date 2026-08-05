/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.integtests.tooling.r970

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.integtests.tooling.r930.KotlinDslPluginRelatedToolingApiSpecification
import org.gradle.integtests.tooling.r930.ResilientGradleBuildBuilderCrossVersionSpec.BuildActionResult
import org.gradle.integtests.tooling.r930.ResilientGradleBuildBuilderCrossVersionSpec.FetchModelAction
import org.gradle.integtests.tooling.r940.GradleBuildAction
import org.gradle.integtests.tooling.r940.GradleBuildModel
import org.gradle.integtests.tooling.r940.GradleBuildModelCollector
import org.gradle.test.fixtures.file.TestFile
import org.gradle.tooling.BuildException
import org.gradle.tooling.IntermediateResultHandler
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.model.gradle.GradleBuild

import java.util.regex.Pattern

/**
 * Covers the resilient {@code GradleBuild} model building behaviour from Gradle 9.7 onwards: configuration failures
 * captured while building the model also fail the build, while the partial model is still returned to the client.
 * See the {@code <9.7.0} cases in the r930/r940 specs for the previous behaviour, where the build succeeded.
 */
@ToolingApiVersion('>=9.3.0')
@TargetGradleVersion('>=9.7.0')
class ResilientGradleBuildBuilderCrossVersionSpec extends KotlinDslPluginRelatedToolingApiSpecification {

    static final String BROKEN_SETTINGS_CONTENT = "broken settings file content!!!"
    static final String BROKEN_BUILD_CONTENT = "broken build file content!!!"
    static final String ISOLATED_PROJECTS_FLAG = "-Dorg.gradle.internal.isolated-projects.tooling=true"
    static final String UNSAFE_ISOLATED_PROJECTS_FLAG = "-Dorg.gradle.unsafe.isolated-projects=true"

    private BuildException buildFailure

    GradleBuildModelCollector modelCollector
    TestFile initScriptFile

    def setup() {
        settingsFile.delete() // This is automatically created by `ToolingApiSpecification`
        modelCollector = new GradleBuildModelCollector()
        initScriptFile = file("init.gradle")
        initScriptFile << """
            import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry
            import org.gradle.tooling.provider.model.ToolingModelBuilder
            import javax.inject.Inject
            gradle.lifecycle.beforeProject {
                it.plugins.apply(CustomPlugin)
            }
            class StartParametersModel implements Serializable {
                String getValue() { 'greetings' }
            }
            class SetupStartParametersBuilder implements ToolingModelBuilder {
                boolean canBuild(String modelName) {
                    return modelName == 'org.gradle.integtests.tooling.r940.StartParametersModel'
                }
                Object buildAll(String modelName, Project project) {
                    def tasks = new HashSet<String>(project.gradle.startParameter.taskNames)
                    tasks.add("prepareKotlinBuildScriptModel")
                    tasks.add("printHelloTask")
                    project.gradle.startParameter.setTaskNames(tasks)
                    return new StartParametersModel()
                }
            }
            class CustomPlugin implements Plugin<Project> {
                @Inject
                CustomPlugin(ToolingModelBuilderRegistry registry) {
                    registry.register(new SetupStartParametersBuilder())
                }
                public void apply(Project project) {
                    project.tasks.register("printHelloTask") {
                        doLast {
                            println "Hello from a task"
                        }
                    }
                }
            }
            """.stripIndent()
    }

    def "fails the build but returns partial root project model when settings #description"() {
        given:
        settingsKotlinFile << """
            rootProject.name = "root"
            $causeOfFailure
        """

        when:
        def result = runFetchModelActionExpectingFailure()

        then: "the build fails with the settings failure"
        anyCauseContains(buildFailure, expectedFailure)

        and: "the partial model with the failure is still returned"
        result.failures.size() == 1
        result.failures[0].contains(expectedFailure)
        result.model.includedBuilds.empty
        result.model.editableBuilds.empty
        result.model.rootProject.projectIdentifier.projectPath == ":"
        result.model.rootProject.name == (expectedRootProjectName ?: settingsKotlinFile.parentFile.name)
        result.model.projects == [result.model.rootProject] as Set

        where:
        description         | causeOfFailure                                         | expectedRootProjectName | expectedFailure
        "compilation error" | "broken settings file content!!!"                      | null                    | "Script compilation error"
        "runtime error"     | "throw GradleException(\"Gradle exception boom !!!\")" | "root"                  | "Gradle exception boom !!!"
    }

    def "fails the build but returns root build and included build models when a settings convention plugin is broken"() {
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
        def result = runFetchModelActionExpectingFailure()

        then: "the build fails with the plugin failure"
        anyCauseContains(buildFailure, "Execution failed for task ':build-logic:compileKotlin'")

        and: "the partial models with the failure are still returned"
        result.failures.size() == 1
        result.model.rootProject.projectIdentifier.projectPath == ":"
        result.model.projects == [result.model.rootProject] as Set
        result.model.includedBuilds.size() == 0
        result.model.editableBuilds.size() == 1
        result.model.editableBuilds[0].rootProject.name == "build-logic"
    }

    def "fails the build but returns root project and included build models when included settings files are broken"() {
        given:
        createRootProject()
        createFailingSettingsIncludedProject("included1")
        // The second included build will be skipped due to failure in the first one
        createFailingSettingsIncludedProject("included2")

        when:
        def result = runFetchModelActionExpectingFailure()

        then: "the build fails with the settings failure"
        anyCauseContains(buildFailure, "Script compilation error")

        and: "the partial models with the failure are still returned"
        result.failures.size() == 1
        result.failures.toString().contains("Script compilation error")
        result.model.includedBuilds.size() == 1
        result.model.includedBuilds[0].buildIdentifier.rootDir == file("included1")
    }

    def "fails the build on every invocation when caching models with isolated projects"() {
        given:
        def intermediateCaching = [
            ISOLATED_PROJECTS_FLAG,
            UNSAFE_ISOLATED_PROJECTS_FLAG
        ]
        createRootProject()
        createFailingSettingsIncludedProject("included")

        when:
        def result = runFetchModelActionExpectingFailure(intermediateCaching)

        then:
        anyCauseContains(buildFailure, "Script compilation error")
        result.failures.toString().contains("Script compilation error")
        !result.model.includedBuilds.isEmpty()

        when: "the failed result is not reused from the cache"
        result = runFetchModelActionExpectingFailure(intermediateCaching)

        then:
        anyCauseContains(buildFailure, "Script compilation error")
        result.failures.toString().contains("Script compilation error")
        !result.model.includedBuilds.isEmpty()
    }

    def "fails the build and returns included builds nested within buildSrc composite build partially when compilation failures in #brokenFile"() {
        given:
        settingsKotlinFile << """
            rootProject.name = "root"
        """

        def buildSrcSettingsFile = file("buildSrc/settings.gradle.kts")
        buildSrcSettingsFile << """
            includeBuild("../buildSrc-included")
        """

        def buildSrcIncluded = file("buildSrc-included")
        buildSrcIncluded.file("settings.gradle.kts") << """
            rootProject.name = "buildSrc-included"
        """

        when:
        file(brokenFile) << """ broken !!! """
        fails { model(it) }

        then:
        def e = thrown(BuildException)
        e.cause.message.contains(expectedTopFailure)
        expectedInFailureTree.every { anyCauseContains(e, it) }
        def model = modelCollector.model
        assertFailures(model, *expectedFailures)
        assertModel(model, modelAvailable, expectedIncludedBuilds, expectedEditableBuilds)

        where:
        brokenFile                              | modelAvailable | expectedTopFailure                 | expectedInFailureTree                                                     | expectedFailures | expectedIncludedBuilds | expectedEditableBuilds
        "settings.gradle.kts"                   | false
                | "Build completed with 2 failures."
                | ["Script compilation error", "The settings are not yet available for build ':'."]
                | ["The settings are not yet available for build ':'\\."]
                | []
                | []

        "buildSrc/settings.gradle.kts"          | true
                | "Script compilation error"
                | []
                | [".*Settings file.*buildSrc\\" + File.separatorChar + "settings\\.gradle\\.kts.*Script compilation error.*"]
                | []
                | ["buildSrc"]

        "buildSrc/build.gradle.kts"             | true
                | "Script compilation error"
                | []
                | [".*Build file.*buildSrc\\" + File.separatorChar + "build\\.gradle\\.kts.*Script compilation error.*",
                   "A problem occurred configuring project ':buildSrc'."]
                | []
                | ["buildSrc", "buildSrc-included"]

        "buildSrc-included/settings.gradle.kts" | true
                | "Script compilation error"
                | []
                | [".*Settings file.*buildSrc-included\\" + File.separatorChar + "settings\\.gradle\\.kts.*Script compilation error.*"]
                | ["UNKNOWN"]
                | ["buildSrc", "UNKNOWN"]

        "buildSrc-included/build.gradle.kts"    | true
                | "Script compilation error"
                | []
                | [".*Build file.*buildSrc-included\\" + File.separatorChar + "build\\.gradle\\.kts.*Script compilation error.*",
                   "A problem occurred configuring project ':buildSrc-included'."]
                | []
                | ["buildSrc", "buildSrc-included"]
    }

    private GradleBuildModel model(ProjectConnection conn) {
        def model = null
        conn.action()
            .buildFinished(new GradleBuildAction(true)) {
                modelCollector.onComplete(it)
                model = it
            }.build()
            .forTasks([])
            .withArguments("--init-script=${initScriptFile.absolutePath}")
            .run()
        return model
    }

    private void assertModel(GradleBuildModel model, boolean available, List includedBuilds, List editableBuilds) {
        if (available) {
            assert model.model != null
            assert model.model.includedBuilds.collect { getRootProjectName(it) } == includedBuilds
            assert model.model.editableBuilds.collect { getRootProjectName(it) } == editableBuilds
        } else {
            assert model.model == null
        }
    }

    private static String getRootProjectName(GradleBuild gradleBuild) {
        if (gradleBuild.rootProject == null) {
            return "UNKNOWN"
        } else {
            return gradleBuild.rootProject.name
        }
    }

    private static void assertFailures(GradleBuildModel model, String... expected) {
        assert model.failures.size() == expected.size(): "Expected ${expected.size()} failures, but got ${model.failures.size()}"
        int i = 0
        for (String failure : model.failures) {
            def pattern = Pattern.compile(expected[i++], Pattern.DOTALL)
            assert pattern.matcher(failure).matches(): "Exception \"${failure}\" doesn't match expected pattern \"${expected[i - 1]}\""
        }
    }

    private BuildActionResult runFetchModelActionExpectingFailure(List<String> extraArgs = []) {
        BuildActionResult captured = null
        buildFailure = null
        try {
            withConnection { connection ->
                def action = connection.action()
                    .buildFinished(new FetchModelAction(), { captured = it } as IntermediateResultHandler)
                    .build()
                if (extraArgs) {
                    action.addArguments(extraArgs)
                }
                action.run()
            }
        } catch (BuildException e) {
            buildFailure = e
        }
        assert buildFailure != null: "expected the build to fail"
        assert captured != null: "expected the fetch result to be delivered"
        return captured
    }

    private static boolean anyCauseContains(Throwable throwable, String text, int depth = 0) {
        if (throwable == null || depth > 50) {
            return false
        }
        if (throwable.message?.contains(text)) {
            return true
        }
        def causes = throwable.respondsTo('getCauses')
            ? throwable.causes
            : (throwable.cause != null ? [throwable.cause] : [])
        return causes.any { anyCauseContains(it as Throwable, text, depth + 1) }
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
        included.file(settingsKotlinFileName) << BROKEN_SETTINGS_CONTENT
        included.file(defaultBuildKotlinFileName) << BROKEN_BUILD_CONTENT
    }
}
