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
import org.gradle.integtests.tooling.r16.CustomModel
import org.gradle.test.fixtures.dsl.GradleDsl
import org.gradle.tooling.BuildAction
import org.gradle.tooling.BuildController
import org.gradle.tooling.FetchModelResult
import org.gradle.tooling.model.gradle.BasicGradleProject
import org.gradle.tooling.model.gradle.GradleBuild
import org.gradle.util.GradleVersion

import static org.gradle.test.fixtures.dsl.GradleDsl.GROOVY
import static org.gradle.test.fixtures.dsl.GradleDsl.KOTLIN

@ToolingApiVersion(">=9.3.0")
class FetchBuildActionCrossVersionSpec extends ToolingApiSpecification {

    def "can request GradleBuild model"() {
        given:
        settingsFile << "rootProject.name = 'root'"

        when:
        def result = succeeds {
            action(new FetchGradleBuildAction())
                .run()
        }

        then:
        result.modelValue == ['root']
    }

    @TargetGradleVersion("<9.4.0")
    def "returns a failure for GradleBuild model if settings script fails due to #description with #dsl DSL"() {
        given:
        settingsFile.delete()
        settingsKotlinFile.delete()
        writeSettingsFile(dsl, error)

        when:
        def result = succeeds {
            action(new FetchGradleBuildAction())
                .run()
        }

        then:

        if(targetVersion < GradleVersion.version("8.9") && expectedCause == "Unexpected input") {
            expectedCause = "Could not compile settings file"
        }

        result.modelValue == null
        result.causes.size() == 1
        result.causes[0].contains(expectedCause)

        where:
        description          | error                                                  | expectedCause               | dsl
        "script compilation" | "broken !!!"                                           | "Unexpected input"          | GROOVY
        "runtime exception"  | """throw RuntimeException("broken settings script")""" | "broken settings script"    | GROOVY
        "script compilation" | "broken !!!"                                           | "broken !!!"                | KOTLIN
        "runtime exception"  | """throw RuntimeException("broken settings script")""" | "broken settings script"    | KOTLIN

    }

    def "can request unknown model"() {
        when:
        def causes = succeeds {
            action(new FetchUnknownModelAction())
                .run()
        }

        then:
        causes == ["No builders are available to build a model of type 'org.gradle.integtests.tooling.r930.FetchBuildActionCrossVersionSpec\$UnknownModel'."]
    }

    def "can request a custom model with #dsl DSL"() {
        given:
        setupInitScriptWithCustomModelBuilder()

        when:
        def result = succeeds {
            action(new FetchCustomModelAction())
                .withArguments("--init-script=${file('init.gradle').absolutePath}")
                .run()
        }

        then:
        result.modelValue == "greetings"
        result.failureMessages.isEmpty()
        result.causes.isEmpty()

        where:
        dsl << [GROOVY, KOTLIN]
    }

    def "returns a failure if a model builder throws an exception with #dsl DSL"() {
        given:
        setupInitScriptWithCustomModelBuilder("throw new RuntimeException('broken builder')")

        when:
        def result = succeeds {
            action(new FetchCustomModelAction())
                .withArguments("--init-script=${file('init.gradle').absolutePath}")
                .run()
        }

        then:
        result.modelValue == null
        result.failureMessages == ["broken builder"]
    }

    def "returns a failure if project configuration fails due to #description with #dsl DSL"() {
        given:
        settingsFile << "rootProject.name = 'root'"
        setupInitScriptWithCustomModelBuilder()

        writeBuildFile(dsl, error)

        when:
        def result = succeeds {
            action(new FetchCustomModelAction())
                .withArguments("--init-script=${file('init.gradle').absolutePath}")
                .run()
        }

        then:

        if(targetVersion < GradleVersion.version("8.9") && cause == "Could not compile build file ") {
            cause = "Could not open cp_proj generic class cache for"
        }


        result.modelValue == null
        result.failureMessages == ["A problem occurred configuring root project 'root'."]
        result.causes.size() == 1
        result.causes[0].contains(cause)

        where:
        description          | error                                                            | cause                                               | dsl
        "script compilation" | "broken !!!"                                                     | "Could not compile build file "                     | GROOVY
        "runtime exception"  | """throw new RuntimeException("broken project configuration")""" | "A problem occurred evaluating root project 'root'" | GROOVY
        "script compilation" | "broken !!!"                                                     | "broken !!!"                                        | KOTLIN
        "runtime exception"  | """throw RuntimeException("broken project configuration")"""     | "broken project configuration"                      | KOTLIN
    }

    private void writeBuildFile(GradleDsl dsl, String s) {
        if (dsl == KOTLIN) {
            buildFileKts << s
        } else {
            buildFile << s
        }
    }

    def "'#method' method returns the same successful result as other fetch methods"() {
        given:
        setupInitScriptWithCustomModelBuilder()

        when:
        def result = succeeds {
            action(buildAction)
                .withArguments("--init-script=${file('init.gradle').absolutePath}")
                .run()
        }

        then:
        result.modelValue == "greetings"
        result.failureMessages.isEmpty()
        result.causes.isEmpty()

        where:
        method                                                       | buildAction
        "fetch(modelType)"                                           | FetchCustomModelAction.withFetchModelCall()
        "fetch(target,modelType)"                                    | FetchCustomModelAction.withFetchTargetModelCall()
        "fetch(modelType,parameterType,parameterInitializer)"        | FetchCustomModelAction.withFetchModelParametersCall()
        "fetch(target,modelType,parameterType,parameterInitializer)" | new FetchCustomModelAction()
    }

    def "'#method' returns the same result as other fetch methods in the presence of project build script failures"() {
        given:
        settingsFile << "rootProject.name = 'root'"
        setupInitScriptWithCustomModelBuilder()

        writeBuildFile(dsl, "broken !!!")

        when:
        def result = succeeds {
            action(new FetchCustomModelAction())
                .withArguments("--init-script=${file('init.gradle').absolutePath}")
                .run()
        }

        then:
        result.modelValue == null
        result.failureMessages.size() == 1
        result.failureMessages[0].contains("A problem occurred configuring root project 'root'.")

        where:
        method                                                       | buildAction                                              | dsl
        "fetch(modelType)"                                           | FetchCustomModelAction.withFetchModelCall()              | GROOVY
        "fetch(target,modelType)"                                    | FetchCustomModelAction.withFetchTargetModelCall()        | GROOVY
        "fetch(modelType,parameterType,parameterInitializer)"        | FetchCustomModelAction.withFetchModelParametersCall()    | GROOVY
        "fetch(target,modelType,parameterType,parameterInitializer)" | new FetchCustomModelAction()                             | GROOVY
        "fetch(modelType)"                                           | FetchCustomModelAction.withFetchModelCall()              | KOTLIN
        "fetch(target,modelType)"                                    | FetchCustomModelAction.withFetchTargetModelCall()        | KOTLIN
        "fetch(modelType,parameterType,parameterInitializer)"        | FetchCustomModelAction.withFetchModelParametersCall()    | KOTLIN
        "fetch(target,modelType,parameterType,parameterInitializer)" | new FetchCustomModelAction()                             | KOTLIN

    }

    def "'#method' method returns the same failed result as other fetch methods with #dsl DSL"() {
        given:
        setupInitScriptWithCustomModelBuilder()

        writeSettingsFile(dsl, "garbage !!!")

        when:
        def result = succeeds {
            action(buildAction)
                .withArguments("--init-script=${file('init.gradle').absolutePath}")
                .run()
        }

        then:
        result.modelValue == null
        result.failureMessages.size() == 1
        // A bit different error messages from Gradle 8.7 onwards,
        // compilation error was wrapped in a more generic error message before
        def expectedFailure
        if(dsl == GROOVY) {
            expectedFailure = targetVersion >= GradleVersion.version("8.7")
                ? "Could not compile settings file"
                : "Could not open cp_settings generic class cache for settings file"
            result.failureMessages[0].contains("Script compilation error:")
        } else {
            expectedFailure = "Script compilation error"
        }
        result.failureMessages[0].contains(expectedFailure)

        where:
        method                                                       | buildAction                                              | dsl
        "fetch(modelType)"                                           | FetchCustomModelAction.withFetchModelCall()              | GROOVY
        "fetch(target,modelType)"                                    | FetchCustomModelAction.withFetchTargetModelCall()        | GROOVY
        "fetch(modelType,parameterType,parameterInitializer)"        | FetchCustomModelAction.withFetchModelParametersCall()    | GROOVY
        "fetch(target,modelType,parameterType,parameterInitializer)" | new FetchCustomModelAction()                             | GROOVY
        "fetch(modelType)"                                           | FetchCustomModelAction.withFetchModelCall()              | KOTLIN
        "fetch(target,modelType)"                                    | FetchCustomModelAction.withFetchTargetModelCall()        | KOTLIN
        "fetch(modelType,parameterType,parameterInitializer)"        | FetchCustomModelAction.withFetchModelParametersCall()    | KOTLIN
        "fetch(target,modelType,parameterType,parameterInitializer)" | new FetchCustomModelAction()                             | KOTLIN
    }

    private void writeSettingsFile(GradleDsl dsl, String s) {
        if (dsl == KOTLIN) {
            settingsFile.delete()
            settingsKotlinFile << s
        } else {
            settingsFile << s
        }
    }

    def "can query models per project"() {
        given:
        settingsFile << "rootProject.name = 'root'"
        setupInitScriptWithCustomModelBuilder()

        when:
        def result = succeeds {
            action(new FetchCustomModelPerProjectAction())
                .withArguments("--init-script=${file('init.gradle').absolutePath}")
                .run()
        }

        then:
        result.modelValue == ["root": "greetings"]
        result.failureMessages.isEmpty()
        result.causes.isEmpty()
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
                    return modelName == '${CustomModel.name}'
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

    static class FetchGradleBuildAction implements BuildAction<Result<List<String>>> {
        @Override
        Result<List<String>> execute(BuildController controller) {
            def result = controller.fetch(null, GradleBuild.class, null, null)
            def projectNames = null
            if (result.model != null) {
                assert result.model instanceof GradleBuild
                projectNames = result.model.projects.collect { it.name }
            }
            def failures = result.failures.stream()
                .map { it.message }
                .toList()
            def causes = result.failures.stream()
                .flatMap { it.causes.stream() }
                .map { it.message }
                .toList()
            return new Result(projectNames, failures, causes)
        }
    }

    static class FetchUnknownModelAction implements BuildAction<List<String>> {
        @Override
        List<String> execute(BuildController controller) {
            def result = controller.fetch(null, UnknownModel.class, null, null)
            assert result.model === null
            return result.failures.stream()
                .flatMap { it.causes.stream() }
                .map { it.message }
                .toList()
        }
    }

    static class FetchCustomModelAction implements BuildAction<Result<String>> {

        @Override
        Result execute(BuildController controller) {
            def result = fetch(controller)
            def failures = result.failures.stream()
                .map { it.message }
                .toList()
            def causes = result.failures.stream()
                .flatMap { it.causes.stream() }
                .map { it.message }
                .toList()
            return new Result(result.model?.value, failures, causes)
        }

        protected FetchModelResult<CustomModel> fetch(BuildController controller) {
            return controller.fetch(CustomModel.class, null, null)
        }

        static FetchCustomModelAction withFetchModelCall() {
            return new FetchCustomModelAction() {
                @Override
                FetchModelResult<CustomModel> fetch(BuildController controller) {
                    return controller.fetch(CustomModel.class)
                }
            }
        }

        static FetchCustomModelAction withFetchTargetModelCall() {
            return new FetchCustomModelAction() {
                @Override
                FetchModelResult<CustomModel> fetch(BuildController controller) {
                    return controller.fetch(CustomModel.class)
                }
            }
        }

        static FetchCustomModelAction withFetchModelParametersCall() {
            return new FetchCustomModelAction() {
                @Override
                FetchModelResult<CustomModel> fetch(BuildController controller) {
                    return controller.fetch(CustomModel.class, null, null)
                }
            }
        }
    }

    static class FetchCustomModelPerProjectAction implements BuildAction<Result<Map<String, String>>> {
        @Override
        Result execute(BuildController controller) {
            def gradleBuildResult = controller.fetch(GradleBuild.class, null, null)
            assert gradleBuildResult.model instanceof GradleBuild
            assert gradleBuildResult.failures.isEmpty()
            def gradleBuild = gradleBuildResult.model as GradleBuild
            def failures = []
            def causes = []
            def values = [:]
            for (BasicGradleProject project : gradleBuild.projects) {
                def result = controller.fetch(CustomModel.class, null, null)
                values[project.name] = result.model?.value
                failures += result.failures.stream()
                    .map { it.message }
                    .toList()
                causes += result.failures.stream()
                    .flatMap { it.causes.stream() }
                    .map { it.message }
                    .toList()
            }
            return new Result(values, failures, causes)
        }
    }

    interface UnknownModel {}

    static class Result<T> implements Serializable {
        T modelValue
        List<String> failureMessages = []
        List<String> causes = []

        Result(T modelValue, List<String> failureMessages, List<String> causes) {
            this.modelValue = modelValue
            this.failureMessages = failureMessages
            this.causes = causes
        }
    }
}
