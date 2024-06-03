/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.integtests.tooling.r48

import org.gradle.integtests.tooling.fixture.ActionDiscardsConfigurationFailure
import org.gradle.integtests.tooling.fixture.ActionQueriesModelThatRequiresConfigurationPhase
import org.gradle.integtests.tooling.fixture.ActionShouldNotBeCalled
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.tooling.BuildActionExecuter
import org.gradle.tooling.BuildActionFailureException
import org.gradle.tooling.BuildException
import org.gradle.tooling.UnsupportedVersionException
import org.gradle.tooling.events.OperationType
import org.gradle.tooling.events.ProgressEvent
import org.gradle.tooling.events.ProgressListener

import java.util.regex.Pattern

@TargetGradleVersion(">=4.8")
class PhasedBuildActionCrossVersionSpec extends ToolingApiSpecification {
    def setup() {
        buildFile << """
            import org.gradle.tooling.provider.model.ParameterizedToolingModelBuilder
            import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry
            import javax.inject.Inject

            task hello {
                doLast {
                    println "hello"
                }
            }

            task bye(dependsOn: hello) {
                doLast {
                    println "bye"
                }
            }

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

    def "can run phased action"() {
        IntermediateResultHandlerCollector projectsLoadedHandler = new IntermediateResultHandlerCollector()
        IntermediateResultHandlerCollector buildFinishedHandler = new IntermediateResultHandlerCollector()

        when:
        withConnection { connection ->
            connection.action().projectsLoaded(new CustomProjectsLoadedAction(null), projectsLoadedHandler)
                .buildFinished(new CustomBuildFinishedAction(), buildFinishedHandler)
                .build()
                .run()
        }

        then:
        projectsLoadedHandler.getResult() == "loading"
        buildFinishedHandler.getResult() == "build"
    }

    def "failures from action are received and future actions not run"() {
        def projectsLoadedHandler = new IntermediateResultHandlerCollector()
        def buildFinishedHandler = new IntermediateResultHandlerCollector()

        when:
        withConnection { connection ->
            def action = connection.action()
                .projectsLoaded(new FailAction(), projectsLoadedHandler)
                .buildFinished(new ActionShouldNotBeCalled(), buildFinishedHandler)
                .build()

            collectOutputs(action)
            action.run()
        }

        then:
        BuildActionFailureException e = thrown()
        e.message == "The supplied phased action failed with an exception."
        e.cause instanceof RuntimeException
        e.cause.message == "actionFailure"
        projectsLoadedHandler.getResult() == null
        buildFinishedHandler.getResult() == null

        and:
        if (targetDist.toolingApiHasCauseOnPhasedActionFail) {
            failure.assertHasDescription('actionFailure')
        } else {
            failure.assertHasCause('actionFailure')
        }
        assertHasConfigureFailedLogging()
    }

    @TargetGradleVersion(">=4.8 <7.3")
    def "actions are not run when configuration fails"() {
        def projectsLoadedHandler = new IntermediateResultHandlerCollector()
        def buildFinishedHandler = new IntermediateResultHandlerCollector()

        given:
        buildFile << """
            throw new RuntimeException("broken")
        """

        when:
        withConnection { connection ->
            def action = connection.action()
                .projectsLoaded(new ActionShouldNotBeCalled(), projectsLoadedHandler)
                .buildFinished(new ActionShouldNotBeCalled(), buildFinishedHandler)
                .build()
            collectOutputs(action)
            action.run()
        }

        then:
        BuildException e = thrown()
        e.message.startsWith("Could not run phased build action using")
        e.cause.message.contains("A problem occurred evaluating root project")
        projectsLoadedHandler.getResult() == null
        buildFinishedHandler.getResult() == null

        and:
        failure.assertHasDescription("A problem occurred evaluating root project")
        assertHasConfigureFailedLogging()
    }

    @TargetGradleVersion(">=7.3")
    def "action receives configuration failure"() {
        def projectsLoadedHandler = new IntermediateResultHandlerCollector()
        def buildFinishedHandler = new IntermediateResultHandlerCollector()

        given:
        buildFile << """
            throw new RuntimeException("broken")
        """

        when:
        withConnection { connection ->
            def action = connection.action()
                .projectsLoaded(new ActionQueriesModelThatRequiresConfigurationPhase(), projectsLoadedHandler)
                .buildFinished(new ActionShouldNotBeCalled(), buildFinishedHandler)
                .build()
            collectOutputs(action)
            action.run()
        }

        then:
        BuildActionFailureException e = thrown()
        e.message.startsWith("The supplied phased action failed with an exception.")
        e.cause.message.contains("A problem occurred configuring root project")
        projectsLoadedHandler.getResult() == null
        buildFinishedHandler.getResult() == null

        and:
        failure.assertHasDescription("A problem occurred evaluating root project")
        assertHasConfigureFailedLogging()
    }

    @TargetGradleVersion(">=7.3")
    def "build fails on configuration failure when projects loaded action discards failure"() {
        def projectsLoadedHandler = new IntermediateResultHandlerCollector()
        def buildFinishedHandler = new IntermediateResultHandlerCollector()

        given:
        buildFile << """
            throw new RuntimeException("broken")
        """

        when:
        withConnection { connection ->
            def action = connection.action()
                .projectsLoaded(new ActionDiscardsConfigurationFailure(), projectsLoadedHandler)
                .buildFinished(new ActionQueriesModelThatRequiresConfigurationPhase(), buildFinishedHandler)
                .build()
            collectOutputs(action)
            action.run()
        }

        then:
        BuildActionFailureException e = thrown()
        e.message.startsWith("The supplied phased action failed with an exception.")
        e.cause.message.contains("A problem occurred configuring root project")
        projectsLoadedHandler.getResult() == "result"
        buildFinishedHandler.getResult() == null

        and:
        failure.assertHasDescription("A problem occurred evaluating root project")
        assertHasConfigureFailedLogging()
    }

    def "build finished action does not run when build fails"() {
        def projectsLoadedHandler = new IntermediateResultHandlerCollector()
        def buildFinishedHandler = new IntermediateResultHandlerCollector()

        buildFile << """
            task broken {
                doLast { throw new RuntimeException("broken") }
            }
        """

        when:
        withConnection { connection ->
            def action = connection.action()
                .projectsLoaded(new CustomProjectsLoadedAction(null), projectsLoadedHandler)
                .buildFinished(new ActionShouldNotBeCalled(), buildFinishedHandler)
                .build()
            collectOutputs(action)
            action.forTasks("broken")
            action.run()
        }

        then:
        BuildException e = thrown()
        e.message.startsWith("Could not run phased build action using")
        e.cause.message.contains("Execution failed for task ':broken'.")
        projectsLoadedHandler.getResult() == "loading"
        buildFinishedHandler.getResult() == null

        and:
        failure.assertHasDescription("Execution failed for task ':broken'.")
        assertHasBuildFailedLogging()
    }

    def "build is interrupted immediately if action fails"() {
        IntermediateResultHandlerCollector projectsLoadedHandler = new IntermediateResultHandlerCollector()
        def events = ""

        when:
        withConnection { connection ->
            connection.action().projectsLoaded(new FailAction(), projectsLoadedHandler)
                .build()
                .forTasks(["hello"])
                .addProgressListener(new ProgressListener() {
                    @Override
                    void statusChanged(ProgressEvent event) {
                        events += event.getDescriptor().getDisplayName() + "\n"
                    }
                }, OperationType.TASK)
                .run()
        }

        then:
        thrown(BuildActionFailureException)
        events.empty
    }

    def "can modify task graph in projects evaluated action"() {
        IntermediateResultHandlerCollector projectsLoadedHandler = new IntermediateResultHandlerCollector()
        def stdOut = new ByteArrayOutputStream()

        when:
        withConnection { connection ->
            connection.action().projectsLoaded(new CustomProjectsLoadedAction(["hello"]), projectsLoadedHandler)
                .build()
                .forTasks([])
                .setStandardOutput(stdOut)
                .run()
        }

        then:
        projectsLoadedHandler.getResult() == "loadingWithTasks"
        stdOut.toString().contains("hello")
    }

    def "can run pre-defined tasks and build finished action is run after tasks are executed"() {
        IntermediateResultHandlerCollector buildFinishedHandler = new IntermediateResultHandlerCollector()
        def stdOut = new ByteArrayOutputStream()

        when:
        withConnection { connection ->
            connection.action().buildFinished(new CustomBuildFinishedAction(), buildFinishedHandler)
                .build()
                .forTasks("bye")
                .setStandardOutput(stdOut)
                .run()
        }

        then:
        Pattern regex = Pattern.compile(".*hello.*bye.*buildFinishedAction.*", Pattern.DOTALL)
        assert stdOut.toString().matches(regex)
        buildFinishedHandler.getResult() == "build"
        stdOut.toString().contains("hello")
        stdOut.toString().contains("bye")
    }

    def "do not run any tasks when none specified and #description"() {
        file('build.gradle') << """
            $configuration

            gradle.taskGraph.whenReady {
                throw new RuntimeException()
            }
        """

        when:
        withConnection { connection ->
            def builder = connection.action()
                .buildFinished(new CustomBuildFinishedAction(), new IntermediateResultHandlerCollector())
                .build()
            collectOutputs(builder)
            builder.run()
        }

        then:
        noExceptionThrown()
        assertHasConfigureSuccessfulLogging()

        where:
        description                                        | configuration
        "build logic does not define any additional tasks" | ""
        "build logic defines default tasks"                | "defaultTasks = ['broken']"
        "build logic injects tasks into start param"       | "gradle.startParameter.taskNames = ['broken']"
    }

    def "#description means run help task"() {
        file('build.gradle') << """
        """

        when:
        withConnection { connection ->
            def builder = connection.action()
                .buildFinished(new CustomBuildFinishedAction(), new IntermediateResultHandlerCollector())
                .build()
            action(builder)
            collectOutputs(builder)
            builder.run()
        }

        then:
        assertHasBuildSuccessfulLogging()
        result.assertTasksExecuted(":help")

        where:
        description                 | action
        "empty array of task names" | { BuildActionExecuter b -> b.forTasks() }
        "empty list of task names"  | { BuildActionExecuter b -> b.forTasks([]) }
    }

    def "#description means run default tasks when they are defined"() {
        file('build.gradle') << """
            defaultTasks = ["thing"]

            task thing { }
        """

        when:
        withConnection { connection ->
            def builder = connection.action()
                .buildFinished(new CustomBuildFinishedAction(), new IntermediateResultHandlerCollector())
                .build()
            action(builder)
            collectOutputs(builder)
            builder.run()
        }

        then:
        assertHasBuildSuccessfulLogging()
        result.assertTasksExecuted(":thing")

        where:
        description                 | action
        "empty array of task names" | { BuildActionExecuter b -> b.forTasks() }
        "empty list of task names"  | { BuildActionExecuter b -> b.forTasks([]) }
    }

    def "#description means run tasks injected by build logic"() {
        file('build.gradle') << """
            gradle.startParameter.taskNames = ["thing"]

            task thing { }
        """

        when:
        withConnection { connection ->
            def builder = connection.action()
                .buildFinished(new CustomBuildFinishedAction(), new IntermediateResultHandlerCollector())
                .build()
            action(builder)
            collectOutputs(builder)
            builder.run()
        }

        then:
        noExceptionThrown()
        assertHasBuildSuccessfulLogging()
        result.assertTasksExecuted(":thing")

        where:
        description                 | action
        "empty array of task names" | { BuildActionExecuter b -> b.forTasks() }
        "empty list of task names"  | { BuildActionExecuter b -> b.forTasks([]) }
    }

    @TargetGradleVersion(">=3.0 <4.8")
    def "exception when not supported gradle version"() {
        def version = targetDist.version.version
        IntermediateResultHandlerCollector buildFinishedHandler = new IntermediateResultHandlerCollector()

        when:
        withConnection { connection ->
            connection.action().buildFinished(new CustomBuildFinishedAction(), buildFinishedHandler)
                .build()
                .run()
        }

        then:
        UnsupportedVersionException e = thrown()
        e.message == "The version of Gradle you are using (${version}) does not support the PhasedBuildActionExecuter API. Support for this is available in Gradle 4.8 and all later versions."
    }
}
