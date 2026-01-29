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

package org.gradle.integtests.tooling.r940


import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.test.fixtures.file.TestFile
import org.gradle.tooling.BuildAction
import org.gradle.tooling.BuildController
import org.gradle.tooling.BuildException
import org.gradle.tooling.Failure
import org.gradle.tooling.IntermediateResultHandler
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.model.gradle.GradleBuild

import java.util.regex.Pattern

class ResilientGradleBuildBuilderCrossVersionSpec extends ToolingApiSpecification {

    TestFile initScriptFile
    KotlinModelCollector modelCollector

    def setup() {
        modelCollector = new KotlinModelCollector()
        settingsFile.delete() // This is automatically created by `ToolingApiSpecification`

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
            class CustomThing implements Serializable {
            }
            class SetupStartParametersBuilder implements ToolingModelBuilder {
                boolean canBuild(String modelName) {
                    return modelName == '${StartParametersModel.name}'
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
                    println "Registered SetupStartParametersBuilder for project: " + (project != null ? project.name : "<no project>")
                }
            }
            """.stripIndent()
    }

    @ToolingApiVersion('>=9.3.0')
    @TargetGradleVersion('>=9.4.0')
    def "returns buildSrc model even if broken convention plugin defined in buildSrc"() {
        given:
        settingsKotlinFile << """
            rootProject.name = "root"
        """
        buildKotlinFile << """
            plugins {
                id("my-conventions")
            }
        """

        file("buildSrc/build.gradle.kts") << """
            plugins {
                `kotlin-dsl`
            }
            
            repositories {
                gradlePluginPortal()
            }
        """

        file("buildSrc/src/main/kotlin/my-conventions.gradle.kts") << """
             broken !!! 
        """

        when:
        fails { model(it) }

        then:
        def e = thrown(BuildException)
        e.cause.message.contains("Execution failed for task ':buildSrc:compileKotlin'.")
        def model = modelCollector.model
        assertFailures(model,
                "Execution failed for task ':buildSrc:compileKotlin'.",
                "Execution failed for task ':buildSrc:compileKotlin'.",
        )
        assertModel(model, true, [], ["buildSrc"])
    }

    @ToolingApiVersion('>=8.8')
    @TargetGradleVersion('>=8.8')
    def "returns included builds nested within buildSrc composite build when nothing broken - NON RESILIENT"() {
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
        succeeds { model(it, false) }

        then:
        noExceptionThrown()
        def model = modelCollector.model
        assertFailures(model)
        assertModel(model, true, [], ["buildSrc", "buildSrc-included"])
    }

    @ToolingApiVersion('>=9.3.0')
    @TargetGradleVersion('>=9.4.0')
    def "returns included builds nested within buildSrc composite build when nothing broken"() {
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
        succeeds { model(it) }

        then:
        noExceptionThrown()
        def model = modelCollector.model
        assertFailures(model)
        assertModel(model, true, [], ["buildSrc", "buildSrc-included"])
    }

    @ToolingApiVersion('>=9.3.0')
    @TargetGradleVersion('>=9.4.0')
    def "returns included builds nested within buildSrc composite build partially when compilation failures in #brokenFile"() {
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
        e.cause.message.contains("Script compilation error")
        def model = modelCollector.model
        assertFailures(model, *expectedFailures)
        assertModel(model, modelAvailable, expectedIncludedBuilds, expectedEditableBuilds)

        where:
        brokenFile                              | modelAvailable | expectedFailures | expectedIncludedBuilds | expectedEditableBuilds
        "settings.gradle.kts"                   | false
                | [
                    "The settings are not yet available for build."
                ]
                | []
                | []

        "buildSrc/settings.gradle.kts"          | true
                | [
                    ".*Settings file.*buildSrc\\" + File.separatorChar + "settings\\.gradle\\.kts.*Script compilation error.*"
                ]
                | []
                | ["buildSrc"]

        "buildSrc/build.gradle.kts"             | true
                | [
                    ".*Build file.*buildSrc\\" + File.separatorChar + "build\\.gradle\\.kts.*Script compilation error.*",
                    "A problem occurred configuring project ':buildSrc'.",
                ]
                | []
                | ["buildSrc", "buildSrc-included"]

        "buildSrc-included/settings.gradle.kts" | true
                | [
                    ".*Settings file.*buildSrc-included\\" + File.separatorChar + "settings\\.gradle\\.kts.*Script compilation error.*"
                ]
                | ["UNKNOWN"]
                | ["buildSrc", "UNKNOWN"]

        "buildSrc-included/build.gradle.kts"    | true
                | [
                    ".*Build file.*buildSrc-included\\" + File.separatorChar + "build\\.gradle\\.kts.*Script compilation error.*",
                    "A problem occurred configuring project ':buildSrc-included'.",
                ]
                | []
                | ["buildSrc", "buildSrc-included"]
    }

    void assertModel(GradleBuildModel model, boolean available, List includedBuilds, List editableBuilds) {
        if (available) {
            assert model.model != null
            assert model.model.includedBuilds.collect { getRootProjectName(it) } == includedBuilds
            assert model.model.editableBuilds.collect { getRootProjectName(it) } == editableBuilds
        } else {
            assert model.model == null
        }
    }

    private String getRootProjectName(GradleBuild gradleBuild) {
        if (gradleBuild.rootProject == null) {
            return "UNKNOWN"
        } else {
            return gradleBuild.rootProject.name
        }
    }

    void assertFailures(GradleBuildModel model, String... expected) {
        assert model.failures.size() == expected.size(): "Expected ${expected.size()} failures, but got ${model.failures.size()}"
        int i = 0
        for (String failure : model.failures) {
            matchFailure(failure, expected[i++])
        }
    }

    private static void matchFailure(String failureMessage, String expectedPattern) {
        def pattern = Pattern.compile(expectedPattern, Pattern.DOTALL)
        def matcher = pattern.matcher(failureMessage)
        assert matcher.matches(): "Exception \"${failureMessage}\" doesn't match expected pattern \"${expectedPattern}\""
    }

    GradleBuildModel model(ProjectConnection conn) {
        return model(conn, true)
    }

    GradleBuildModel model(ProjectConnection conn, boolean resilient) {
        return GradleBuildAction.model(conn, initScriptFile, modelCollector, resilient)
    }

    static class GradleBuildModel implements Serializable {

        final GradleBuild model
        final Collection<String> failures

        GradleBuildModel(GradleBuild model, Collection<Failure> failures) {
            this.model = model
            this.failures = failures.collect { it.getMessage() }
        }
    }

    static class GradleBuildAction implements BuildAction<GradleBuildModel>, Serializable {

        private final boolean resilient

        GradleBuildAction(boolean resilient) {
            this.resilient = resilient
        }

        @Override
        GradleBuildModel execute(BuildController controller) {
            if (resilient)  {
                def result = controller.fetch(GradleBuild.class)
                return new GradleBuildModel(result.model, result.failures)
            } else {
                def model = controller.getModel(GradleBuild.class)
                return new GradleBuildModel(model, Collections.emptyList())
            }
        }

        private static GradleBuildModel model(ProjectConnection conn, File initScript, IntermediateResultHandler<GradleBuildModel> modelHandler, boolean resilient) {
            def model = null

            Iterable<String> arguments = ["--init-script=${initScript.absolutePath}"]
            arguments += "-Dorg.gradle.internal.resilient-model-building=$resilient"

            conn.action()
                    .buildFinished(new GradleBuildAction(resilient)) {
                        modelHandler.onComplete(it)
                        model = it
                    }.build()
                    .forTasks([])
                    .withArguments(*arguments)
                    .run()
            return model
        }
    }

    static class KotlinModelCollector implements IntermediateResultHandler<GradleBuildModel> {
        GradleBuildModel model

        @Override
        void onComplete(GradleBuildModel result) {
            this.model = result
        }
    }
}