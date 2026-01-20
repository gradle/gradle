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

package org.gradle.integtests.tooling.r940

import org.gradle.integtests.tooling.fixture.RelatedToolingAPITests
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.r48.CustomBuildFinishedModel
import org.gradle.integtests.tooling.r48.CustomProjectsLoadedModel
import org.gradle.tooling.BuildAction
import org.gradle.tooling.BuildActionFailureException
import org.gradle.tooling.BuildController
import org.gradle.tooling.BuildException
import org.gradle.tooling.IntermediateResultHandler
import org.gradle.tooling.events.OperationType
import org.gradle.tooling.events.ProgressEvent
import org.gradle.tooling.events.ProgressListener
import org.gradle.tooling.events.task.TaskFailureResult
import org.gradle.tooling.events.task.TaskFinishEvent
import org.gradle.util.GradleVersion

import java.util.function.Supplier

import static org.gradle.integtests.tooling.r940.PhasedBuildActionCrossVersionSpec.CustomBuildFinishedAction.CallType
import static org.gradle.integtests.tooling.r940.PhasedBuildActionCrossVersionSpec.CustomBuildFinishedAction.FAILURE_RESULT
import static org.junit.Assume.assumeTrue

@TargetGradleVersion(">=9.4.0")
@RelatedToolingAPITests(
    tests = org.gradle.integtests.tooling.r48.PhasedBuildActionCrossVersionSpec
)
class PhasedBuildActionCrossVersionSpec extends ToolingApiSpecification {

    IntermediateResultHandlerCollector projectsLoadedHandler
    IntermediateResultHandlerCollector buildFinishedHandler

    def setup() {
        projectsLoadedHandler = new IntermediateResultHandlerCollector()
        buildFinishedHandler = new IntermediateResultHandlerCollector()
        buildFile << """
            import org.gradle.tooling.provider.model.ParameterizedToolingModelBuilder
            import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry
            import javax.inject.Inject

            allprojects {
                apply plugin: CustomPlugin
            }

            class DefaultCustomModel implements Serializable {
                private final String value;
                DefaultCustomModel(String value) {
                    this.value = value;
                }
                public String getValue() {
                    return value;
                }
            }

            interface CustomParameter {
                void setTasks(List<String> tasks);
                List<String> getTasks();
            }

            class CustomPlugin implements Plugin<Project> {
                @Inject
                CustomPlugin(ToolingModelBuilderRegistry registry) {
                    registry.register(new CustomBuilder());
                }

                public void apply(Project project) {
                }
            }

            class CustomBuilder implements ParameterizedToolingModelBuilder<CustomParameter> {
                boolean canBuild(String modelName) {
                    return modelName == '${CustomProjectsLoadedModel.name}' || modelName == '${CustomBuildFinishedModel.name}'
                }

                Class<CustomParameter> getParameterType() {
                    return CustomParameter.class;
                }

                Object buildAll(String modelName, Project project) {
                    if (modelName == '${CustomProjectsLoadedModel.name}') {
                        return new DefaultCustomModel('loading');
                    }
                    if (modelName == '${CustomBuildFinishedModel.name}') {
                        return new DefaultCustomModel('build');
                    }
                    return null
                }

                Object buildAll(String modelName, CustomParameter parameter, Project project) {
                    if (modelName == '${CustomProjectsLoadedModel.name}') {
                        StartParameter startParameter = project.getGradle().getStartParameter();
                        Set<String> tasks = new HashSet(startParameter.getTaskNames());
                        tasks.addAll(parameter.getTasks());
                        startParameter.setTaskNames(tasks);
                        return new DefaultCustomModel('loadingWithTasks');
                    }
                    return null
                }
            }
        """
    }

    def "build finished action is run even if build fails with #method"() {
        // Fetch API is only supported on TAPI >= 9.3.0
        assumeTrue(callType != CallType.FETCH || toolingApi.toolingApiVersion >= GradleVersion.version("9.3.0"))

        buildFile << """
            task broken {
                doLast { throw new RuntimeException("broken") }
            }
        """

        when:
        fails { connection ->
            connection.action()
                .projectsLoaded(new CustomProjectsLoadedAction(), projectsLoadedHandler)
                .buildFinished(new CustomBuildFinishedAction(callType), buildFinishedHandler)
                .build()
                .forTasks("broken")
                .run()
        }

        then:
        BuildException e = thrown()
        e.message.startsWith("Could not run phased build action using")
        e.cause.message.contains("Execution failed for task ':broken' (registered in build file 'build.gradle').")
        failure.output.contains("Running CustomProjectsLoadedAction")
        failure.output.contains("Running CustomBuildFinishedAction")
        failure.assertHasDescription("Execution failed for task ':broken' (registered in build file 'build.gradle').")

        where:
        method                        | callType
        "BuildController.getModel()"  | CallType.GET_MODEL
        "BuildController.findModel()" | CallType.FIND_MODEL
        "BuildController.fetch()"     | CallType.FETCH
    }

    def "build finished intermediate result handler is run even if task fails with #method"() {
        // Fetch API is only supported on TAPI >= 9.3.0
        assumeTrue(callType != CallType.FETCH || toolingApi.toolingApiVersion >= GradleVersion.version("9.3.0"))

        buildFile << """
            task broken {
                doLast { throw new RuntimeException("broken") }
            }
        """

        when:
        fails { connection ->
            connection.action()
                .projectsLoaded(new CustomProjectsLoadedAction(), projectsLoadedHandler)
                .buildFinished(new CustomBuildFinishedAction(callType), buildFinishedHandler)
                .build()
                .forTasks("broken")
                .run()
        }

        then:
        BuildException e = thrown()
        e.message.startsWith("Could not run phased build action using")
        e.cause.message.contains("Execution failed for task ':broken' (registered in build file 'build.gradle').")
        failure.output.contains("Running CustomBuildFinishedAction")
        projectsLoadedHandler.getResult() == "loading"
        buildFinishedHandler.wasOnCompleteCalled

        and:
        failure.assertHasDescription("Execution failed for task ':broken' (registered in build file 'build.gradle').")

        where:
        method                        | callType
        "BuildController.getModel()"  | CallType.GET_MODEL
        "BuildController.findModel()" | CallType.FIND_MODEL
        "BuildController.fetch()"     | CallType.FETCH
    }

    def "method #description query model if a task fails"() {
        // Fetch API is only supported on TAPI >= 9.3.0
        assumeTrue(callType != CallType.FETCH || toolingApi.toolingApiVersion >= GradleVersion.version("9.3.0"))

        buildFile << """
            task broken {
                doLast { throw new RuntimeException("broken") }
            }
        """

        when:
        fails { connection ->
            connection.action()
                .projectsLoaded(new CustomProjectsLoadedAction(), projectsLoadedHandler)
                .buildFinished(new CustomBuildFinishedAction(callType), buildFinishedHandler)
                .build()
                .forTasks("broken")
                .run()
        }

        then:
        BuildException e = thrown()
        e.message.startsWith("Could not run phased build action using")
        e.cause.message.contains("Execution failed for task ':broken' (registered in build file 'build.gradle').")
        failure.assertHasDescription("Execution failed for task ':broken' (registered in build file 'build.gradle').")
        failure.output.contains("Running CustomBuildFinishedAction")
        projectsLoadedHandler.getResult() == "loading"
        buildFinishedHandler.getResult() == expectedResult

        where:
        description                          | callType            | expectedResult
        "BuildController.getModel() CANNOT"  | CallType.GET_MODEL  | FAILURE_RESULT
        "BuildController.findModel() CANNOT" | CallType.FIND_MODEL | FAILURE_RESULT
        "BuildController.fetch() CAN"        | CallType.FETCH      | "build"
    }

    def "buildAction and task failure error is reported if both fails and buildFinishedHandler is not called"() {
        buildFile << """
            task broken {
                doLast { throw new RuntimeException("broken") }
            }
        """

        when:
        fails { connection ->
            connection.action()
                .projectsLoaded(new CustomProjectsLoadedAction(), projectsLoadedHandler)
                .buildFinished(new CustomFailingBuildFinishedAction(), buildFinishedHandler)
                .build()
                .forTasks("broken")
                .run()
        }

        then:
        BuildActionFailureException e = thrown()
        e.message.startsWith("The supplied phased action failed with an exception")
        e.cause.message.contains("Error from CustomFailingBuildFinishedAction")
        failure.output.contains("Running CustomFailingBuildFinishedAction")
        failure.error.contains("FAILURE: Build completed with 2 failures.")
        failure.error.contains("1: BuildAction failed with an exception.")
        failure.error.contains("2: Task failed with an exception.")
        projectsLoadedHandler.getResult() == "loading"
        !buildFinishedHandler.wasOnCompleteCalled
    }

    def "can listen to task failures with a phased build with #method"() {
        // Fetch API is only supported on TAPI >= 9.3.0
        assumeTrue(callType != CallType.FETCH || toolingApi.toolingApiVersion >= GradleVersion.version("9.3.0"))

        buildFile << """
            task broken {
                doLast { throw new RuntimeException("broken") }
            }
        """
        def failedTasks = []

        when:
        fails { connection ->
            connection.action()
                .projectsLoaded(new CustomProjectsLoadedAction(), projectsLoadedHandler)
                .buildFinished(new CustomBuildFinishedAction(callType), buildFinishedHandler)
                .build()
                .addProgressListener(new ProgressListener() {
                    @Override
                    void statusChanged(ProgressEvent e) {
                        if (e instanceof TaskFinishEvent && e.getResult() instanceof TaskFailureResult) {
                            failedTasks.add(e.descriptor.taskPath)
                        }
                    }
                }, OperationType.TASK)
                .forTasks("broken")
                .run()
        }

        then:
        BuildException e = thrown()
        e.message.startsWith("Could not run phased build action using")
        e.cause.message.contains("Execution failed for task ':broken' (registered in build file 'build.gradle').")
        failure.output.contains("Running CustomBuildFinishedAction")
        failedTasks == [":broken"]

        and:
        failure.assertHasDescription("Execution failed for task ':broken' (registered in build file 'build.gradle').")

        where:
        method                        | callType
        "BuildController.getModel()"  | CallType.GET_MODEL
        "BuildController.findModel()" | CallType.FIND_MODEL
        "BuildController.fetch()"     | CallType.FETCH
    }

    static class IntermediateResultHandlerCollector implements IntermediateResultHandler<String> {
        boolean wasOnCompleteCalled = false
        String result = null

        @Override
        void onComplete(String result) {
            wasOnCompleteCalled = true
            this.result = result
        }
    }

    static class CustomProjectsLoadedAction implements BuildAction<String> {

        @Override
        String execute(BuildController controller) {
            println("Running CustomProjectsLoadedAction")
            return controller.getModel(CustomProjectsLoadedModel.class).getValue()
        }
    }

    static class CustomBuildFinishedAction implements BuildAction<String> {

        static enum CallType {
            GET_MODEL,
            FIND_MODEL,
            FETCH
        }

        static final String FAILURE_RESULT = "<failure>"

        final CallType callType

        CustomBuildFinishedAction(CallType callType) {
            this.callType = callType
        }

        @Override
        String execute(BuildController controller) {
            println("Running CustomBuildFinishedAction")
            if (callType == CallType.GET_MODEL) {
                return tryGet(() -> controller.getModel(CustomBuildFinishedModel.class))
            } else if (callType == CallType.FIND_MODEL) {
                return tryGet(() -> controller.findModel(CustomBuildFinishedModel.class))
            } else if (callType == CallType.FETCH) {
                def result = controller.fetch(CustomBuildFinishedModel.class)
                if (result.model) {
                    assert result.failures.isEmpty()
                    return result.model.value
                } else {
                    assert !result.failures.isEmpty()
                    return FAILURE_RESULT
                }
            } else {
                throw new UnsupportedOperationException("Unknown callType: $callType")
            }
        }

        private static String tryGet(Supplier<String> tryGet) {
            try {
                return tryGet.get()
            } catch (Exception ignored) {
                return FAILURE_RESULT
            }
        }
    }

    static class CustomFailingBuildFinishedAction implements BuildAction<String> {

        @Override
        String execute(BuildController controller) {
            println("Running CustomFailingBuildFinishedAction")
            throw new RuntimeException("Error from CustomFailingBuildFinishedAction")
        }
    }
}
