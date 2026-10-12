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
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.integtests.tooling.r930.FetchCustomModelAction
import org.gradle.integtests.tooling.r930.FetchGradleBuildAction
import org.gradle.integtests.tooling.r930.Result
import org.gradle.test.fixtures.dsl.GradleDsl
import org.gradle.tooling.BuildException
import org.gradle.tooling.IntermediateResultHandler

import static org.gradle.test.fixtures.dsl.GradleDsl.GROOVY
import static org.gradle.test.fixtures.dsl.GradleDsl.KOTLIN

/**
 * Covers the resilient model building behaviour from Gradle 9.7 onwards: a failure observed while building a model
 * (a configuration failure or a failing model builder) is propagated to the client as a {@link BuildException}, even
 * though the per-model failure is still captured in the fetch result. See the {@code <9.7.0} cases in the r930 spec
 * for the previous behaviour, where such failures were only captured and the build succeeded.
 */
@ToolingApiVersion(">=9.3.0")
@TargetGradleVersion(">=9.7.0")
class FetchBuildActionCrossVersionSpec extends ToolingApiSpecification {

    def "resilient sync fails the build when a model builder throws an exception"() {
        given:
        setupInitScriptWithCustomModelBuilder("throw new RuntimeException('broken builder')")

        when:
        fails {
            action(new FetchCustomModelAction())
                .withArguments("--init-script=${file('init.gradle').absolutePath}")
                .run()
        }

        then:
        def e = thrown(BuildException)
        collectCauseMessages(e).any { it?.contains("broken builder") }
    }

    def "resilient sync fails the build when project configuration fails due to #description with #dsl DSL"() {
        given:
        settingsFile << "rootProject.name = 'root'"
        setupInitScriptWithCustomModelBuilder()
        writeBuildFile(dsl, error)

        when:
        fails {
            action(new FetchCustomModelAction())
                .withArguments("--init-script=${file('init.gradle').absolutePath}")
                .run()
        }

        then:
        def e = thrown(BuildException)
        collectCauseMessages(e).any { it?.contains(expectedCause) }

        where:
        description          | error                                                            | expectedCause                  | dsl
        "script compilation" | "broken !!!"                                                     | "broken !!!"                   | GROOVY
        "runtime exception"  | """throw new RuntimeException("broken project configuration")""" | "broken project configuration" | GROOVY
        "script compilation" | "broken !!!"                                                     | "broken !!!"                   | KOTLIN
        "runtime exception"  | """throw RuntimeException("broken project configuration")"""     | "broken project configuration" | KOTLIN
    }

    def "'#method' fails the build in the presence of project build script failures"() {
        given:
        settingsFile << "rootProject.name = 'root'"
        setupInitScriptWithCustomModelBuilder()
        writeBuildFile(GROOVY, "broken !!!")

        when:
        fails {
            action(buildAction)
                .withArguments("--init-script=${file('init.gradle').absolutePath}")
                .run()
        }

        then:
        def e = thrown(BuildException)
        collectCauseMessages(e).any { it?.contains("broken !!!") }

        where:
        method                                                       | buildAction
        "fetch(modelType)"                                           | FetchCustomModelAction.withFetchModelCall()
        "fetch(target,modelType)"                                    | FetchCustomModelAction.withFetchTargetModelCall()
        "fetch(modelType,parameterType,parameterInitializer)"        | FetchCustomModelAction.withFetchModelParametersCall()
        "fetch(target,modelType,parameterType,parameterInitializer)" | new FetchCustomModelAction()
    }

    def "'#method' fails the build with the same settings failure as other fetch methods with #dsl DSL"() {
        given:
        setupInitScriptWithCustomModelBuilder()
        writeSettingsFile(dsl, "garbage !!!")

        when:
        fails {
            action(buildAction)
                .withArguments("--init-script=${file('init.gradle').absolutePath}")
                .run()
        }

        then:
        def e = thrown(BuildException)
        def expectedCause = dsl == GROOVY ? "Could not compile settings file" : "Script compilation error"
        collectCauseMessages(e).any { it?.contains(expectedCause) }

        where:
        method                                                       | buildAction
        "fetch(modelType)"                                           | FetchCustomModelAction.withFetchModelCall()
        "fetch(target,modelType)"                                    | FetchCustomModelAction.withFetchTargetModelCall()
        "fetch(modelType,parameterType,parameterInitializer)"        | FetchCustomModelAction.withFetchModelParametersCall()
        "fetch(target,modelType,parameterType,parameterInitializer)" | new FetchCustomModelAction()

        combined:
        dsl << [GROOVY, KOTLIN]
    }

    def "resilient sync fails the build when a build-scoped model builder throws"() {
        given:
        settingsFile << "rootProject.name = 'root'"
        file("init.gradle") << """
            import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry

            class FailingBuildScopedModelBuilder implements org.gradle.tooling.provider.model.internal.BuildScopeModelBuilder {
                boolean canBuild(String modelName) {
                    return modelName == 'org.gradle.integtests.tooling.r16.CustomModel'
                }
                Object create(org.gradle.internal.build.BuildState target) {
                    throw new RuntimeException("broken build-scoped builder")
                }
            }

            beforeSettings { settings ->
                settings.services.get(ToolingModelBuilderRegistry).register(new FailingBuildScopedModelBuilder())
            }
        """

        when:
        fails {
            action(FetchCustomModelAction.withFetchModelCall())
                .withArguments("--init-script=${file('init.gradle').absolutePath}")
                .run()
        }

        then:
        def e = thrown(BuildException)
        collectCauseMessages(e).any { it?.contains("broken build-scoped builder") }
    }

    def "resilient sync fails the build but returns a partial GradleBuild model when root settings fail"() {
        given:
        settingsFile << """throw new RuntimeException("broken root settings")"""
        Result<List<String>> fetchResult = null

        when:
        fails {
            action()
                .buildFinished(new FetchGradleBuildAction(), { fetchResult = it } as IntermediateResultHandler)
                .build()
                .run()
        }

        then: "the build fails with the settings failure"
        def e = thrown(BuildException)
        collectCauseMessages(e).any { it?.contains("broken root settings") }

        and: "the client still receives the fetch result carrying the failure"
        fetchResult != null
        (fetchResult.failureMessages + fetchResult.causes).any { it?.contains("broken root settings") }
    }

    def "resilient sync fails the build but returns a partial GradleBuild model when included build settings fail"() {
        given:
        settingsFile << """
            rootProject.name = 'root'
            includeBuild("nested")
        """
        file("nested/settings.gradle") << """throw new RuntimeException("broken included settings")"""
        Result<List<String>> fetchResult = null

        when:
        fails {
            action()
                .buildFinished(new FetchGradleBuildAction(), { fetchResult = it } as IntermediateResultHandler)
                .build()
                .run()
        }

        then: "the build fails with the settings failure"
        def e = thrown(BuildException)
        collectCauseMessages(e).any { it?.contains("broken included settings") }

        and: "the client still receives a partial model carrying the failure"
        fetchResult != null
        fetchResult.modelValue == ['root']
        (fetchResult.failureMessages + fetchResult.causes).any { it?.contains("broken included settings") }
    }

    private void writeBuildFile(GradleDsl dsl, String s) {
        if (dsl == KOTLIN) {
            buildFileKts << s
        } else {
            buildFile << s
        }
    }

    private void writeSettingsFile(GradleDsl dsl, String s) {
        if (dsl == KOTLIN) {
            settingsFile.delete()
            settingsKotlinFile << s
        } else {
            settingsFile << s
        }
    }

    def setupInitScriptWithCustomModelBuilder(String builderLogic = "return new CustomModel()") {
        file("init.gradle").text = """
            import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry
            import org.gradle.tooling.provider.model.ToolingModelBuilder
            import javax.inject.Inject

            allprojects {
                project.plugins.apply(CustomPlugin)
            }

            class CustomModel implements Serializable {
                static final INSTANCE = new CustomThing()
                String getValue() { 'greetings' }
                CustomThing getThing() { return INSTANCE }
                Set<CustomThing> getThings() { return [INSTANCE] }
                Map<String, CustomThing> getThingsByName() { return [child: INSTANCE] }
                CustomThing findThing(String name) { return INSTANCE }
            }

            class CustomThing implements Serializable {
            }

            class CustomBuilder implements ToolingModelBuilder {
                boolean canBuild(String modelName) {
                    return modelName == '${org.gradle.integtests.tooling.r16.CustomModel.name}'
                }
                Object buildAll(String modelName, Project project) {
                    ${builderLogic}
                }
            }
            class CustomPlugin implements Plugin<Project> {
                @Inject
                CustomPlugin(ToolingModelBuilderRegistry registry) {
                    registry.register(new CustomBuilder())
                }
                public void apply(Project project) {
                    println "Registered CustomBuilder for project: " + (project != null ? project.name : "<no project>")
                }
            }
            """
    }
}
