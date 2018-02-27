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

package org.gradle.integtests.tooling.r47

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.tooling.BuildActionFailureException
import org.gradle.tooling.UnsupportedVersionException
import org.jetbrains.kotlin.com.google.common.collect.Lists

import java.util.regex.Pattern

@ToolingApiVersion(">=4.7")
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
                    return modelName == '${CustomAfterConfigurationModel.name}' || modelName == '${CustomAfterBuildModel.name}'
                }
                
                Class<CustomParameter> getParameterType() {
                    return CustomParameter.class;
                }
                
                Object buildAll(String modelName, Project project) {
                    if (modelName == '${CustomAfterConfigurationModel.name}') {
                        return new DefaultCustomModel('configuration');
                    }
                    if (modelName == '${CustomAfterBuildModel.name}') {
                        return new DefaultCustomModel('build');
                    }
                    return null
                }
                
                Object buildAll(String modelName, CustomParameter parameter, Project project) {
                    if (modelName == '${CustomAfterConfigurationModel.name}') {
                        project.setDefaultTasks(parameter.getTasks());
                        return new DefaultCustomModel('configurationWithTasks');
                    }
                    return null
                }
            }
        """
    }

    @TargetGradleVersion(">=4.7")
    def "can run phased action"() {
        ResultHandlerCollector afterLoadingHandler = new ResultHandlerCollector()
        ResultHandlerCollector afterConfigurationHandler = new ResultHandlerCollector()
        ResultHandlerCollector afterBuildHandler = new ResultHandlerCollector()

        when:
        withConnection { connection ->
            connection.phasedAction().addAfterLoadingAction(new CustomAfterLoadingAction(), afterLoadingHandler)
                .addAfterConfigurationAction(new CustomAfterConfiguringAction(null), afterConfigurationHandler)
                .addAfterBuildAction(new CustomAfterBuildAction(), afterBuildHandler)
                .build()
                .run()
        }

        then:
        afterLoadingHandler.getResult() == "loading"
        afterLoadingHandler.getFailure() == null
        afterConfigurationHandler.getResult() == "configuration"
        afterConfigurationHandler.getFailure() == null
        afterBuildHandler.getResult() == "build"
        afterBuildHandler.getFailure() == null
    }

    @TargetGradleVersion(">=4.7")
    def "failures are received and future actions not run"() {
        ResultHandlerCollector afterLoadingHandler = new ResultHandlerCollector()
        ResultHandlerCollector afterConfigurationHandler = new ResultHandlerCollector()
        ResultHandlerCollector afterBuildHandler = new ResultHandlerCollector()

        when:
        withConnection { connection ->
            connection.phasedAction().addAfterLoadingAction(new CustomAfterLoadingAction(), afterLoadingHandler)
                .addAfterConfigurationAction(new FailAction(), afterConfigurationHandler)
                .addAfterBuildAction(new CustomAfterBuildAction(), afterBuildHandler)
                .build()
                .run()
        }

        then:
        BuildActionFailureException e = thrown()
        e.message == "The supplied phased action failed with an exception."
        e.cause instanceof RuntimeException
        e.cause.message == "actionFailure"
        afterLoadingHandler.getResult() == "loading"
        afterLoadingHandler.getFailure() == null
        afterConfigurationHandler.getResult() == null
        afterConfigurationHandler.getFailure() instanceof BuildActionFailureException
        afterConfigurationHandler.getFailure().message == "The supplied build action failed with an exception."
        afterConfigurationHandler.getFailure().cause instanceof RuntimeException
        afterConfigurationHandler.getFailure().cause.message == "actionFailure"
        afterBuildHandler.getResult() == null
        afterBuildHandler.getFailure() == null
    }

    @TargetGradleVersion(">=4.7")
    def "can modify task graph in after configuration action"() {
        ResultHandlerCollector afterConfigurationHandler = new ResultHandlerCollector()
        def stdOut = new ByteArrayOutputStream()

        when:
        withConnection { connection ->
            connection.phasedAction().addAfterConfigurationAction(new CustomAfterConfiguringAction(Lists.newArrayList("hello")), afterConfigurationHandler)
                .build()
                .setStandardOutput(stdOut)
                .run()
        }

        then:
        afterConfigurationHandler.getResult() == "configurationWithTasks"
        stdOut.toString().contains("hello")
    }

    @TargetGradleVersion(">=4.7")
    def "can run pre-defined tasks and after build action is run after tasks are executed"() {
        ResultHandlerCollector afterBuildHandler = new ResultHandlerCollector()
        def stdOut = new ByteArrayOutputStream()

        when:
        withConnection { connection ->
            connection.phasedAction().addAfterBuildAction(new CustomAfterBuildAction(), afterBuildHandler)
                .build()
                .forTasks("bye")
                .setStandardOutput(stdOut)
                .run()
        }

        then:
        Pattern regex = Pattern.compile(".*hello.*bye.*afterBuildAction.*", Pattern.DOTALL)
        assert stdOut.toString().matches(regex)
        afterBuildHandler.getResult() == "build"
        stdOut.toString().contains("hello")
        stdOut.toString().contains("bye")
    }

    @TargetGradleVersion("<4.7")
    def "exception when not supported gradle version"() {
        def version = targetDist.version.version
        ResultHandlerCollector afterBuildHandler = new ResultHandlerCollector()

        when:
        withConnection { connection ->
            connection.phasedAction().addAfterBuildAction(new CustomAfterBuildAction(), afterBuildHandler)
                .build()
                .run()
        }

        then:
        UnsupportedVersionException e = thrown()
        e.message == "The version of Gradle you are using (${version}) does not support the PhasedBuildActionExecuter API. Support for this is available in Gradle 4.7 and all later versions."
    }
}
