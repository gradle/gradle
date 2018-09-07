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

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.tooling.BuildActionFailureException
import org.gradle.tooling.UnsupportedVersionException
import org.gradle.tooling.events.OperationType
import org.gradle.tooling.events.ProgressEvent
import org.gradle.tooling.events.ProgressListener

import java.util.regex.Pattern

@ToolingApiVersion(">=4.8")
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

            task defTask {
                doLast {
                    println "default"
                }
            }
            
            allprojects {
                apply plugin: CustomPlugin
                defaultTasks 'defTask'
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

    @TargetGradleVersion(">=4.8")
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

    @TargetGradleVersion(">=4.8")
    def "failures are received and future actions not run"() {
        IntermediateResultHandlerCollector projectsLoadedHandler = new IntermediateResultHandlerCollector()
        IntermediateResultHandlerCollector buildFinishedHandler = new IntermediateResultHandlerCollector()

        when:
        withConnection { connection ->
            connection.action().projectsLoaded(new FailAction(), projectsLoadedHandler)
                .buildFinished(new CustomBuildFinishedAction(), buildFinishedHandler)
                .build()
                .run()
        }

        then:
        def e = thrown BuildActionFailureException
        e.message == "The supplied phased action failed with an exception."
        e.cause instanceof RuntimeException
        e.cause.message == "actionFailure"
        projectsLoadedHandler.getResult() == null
        buildFinishedHandler.getResult() == null
    }

    @TargetGradleVersion(">=4.8")
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

    @TargetGradleVersion(">=4.8")
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

    @TargetGradleVersion(">=4.8")
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

    @TargetGradleVersion(">=4.8")
    def "default tasks are not run if no tasks are specified"() {
        IntermediateResultHandlerCollector buildFinishedHandler = new IntermediateResultHandlerCollector()
        def stdOut = new ByteArrayOutputStream()

        when:
        withConnection { connection ->
            connection.action().buildFinished(new CustomBuildFinishedAction(), buildFinishedHandler)
                .build()
                .setStandardOutput(stdOut)
                .run()
        }

        then:
        buildFinishedHandler.getResult() == "build"
        stdOut.toString().contains("buildFinishedAction")
        !stdOut.toString().contains("default")
    }

    @TargetGradleVersion("<4.8")
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
        def e = thrown UnsupportedVersionException
        e.message == "The version of Gradle you are using (${version}) does not support the PhasedBuildActionExecuter API. Support for this is available in Gradle 4.8 and all later versions."
    }
}
