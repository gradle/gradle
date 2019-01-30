/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.integtests.tooling.r44

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.tooling.BuildActionFailureException
import org.gradle.tooling.GradleConnectionException
import org.gradle.tooling.ResultHandler
import org.gradle.tooling.UnknownModelException
import org.gradle.tooling.UnsupportedVersionException
import org.gradle.util.GradleVersion

class ParameterizedToolingModelCrossVersionSpec extends ToolingApiSpecification {
    def setup() {
        buildFile << """
            import org.gradle.tooling.provider.model.ToolingModelBuilder
            import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry
            import javax.inject.Inject
            
            allprojects {
                apply plugin: CustomPlugin
            }
            
            interface CustomParameter {
                void setValue(String str);
                String getValue();
            }
            
            class DefaultCustomModel implements Serializable {
                private final boolean builtWithParameter;
                private final String parameterValue;
                DefaultCustomModel(CustomParameter parameter) {
                    if (parameter == null) {
                        parameterValue = "noParameter";
                    } else {
                        parameterValue = parameter.getValue();
                    }
                    builtWithParameter = true;
                }
                DefaultCustomModel() {
                    parameterValue = "noParameter"
                    builtWithParameter = false;
                }
                public boolean isBuiltWithParameter() {
                    return builtWithParameter;
                }
                public String getParameterValue() {
                    return parameterValue;
                }
            }
            
            class DefaultCustomModel2 implements Serializable {
                String getValue() {
                    return "myValue";
                }
            }
            
            class CustomPlugin implements Plugin<Project> {
                @Inject
                CustomPlugin(ToolingModelBuilderRegistry registry) {
                    registry.register(new CustomBuilder());
                    registry.register(new CustomBuilder2());
                }
            
                public void apply(Project project) {
                }
            }
            
            class CustomBuilder2 implements ToolingModelBuilder {
                boolean canBuild(String modelName) {
                    return modelName == '${CustomModel2.name}'
                }
                Object buildAll(String modelName, Project project) {
                    return new DefaultCustomModel2();
                }
            }
        """

        if (getTargetVersion() < GradleVersion.version("4.4")) {
            buildFile << """
                class CustomBuilder implements ToolingModelBuilder {
                    boolean canBuild(String modelName) {
                        return modelName == '${CustomModel.name}'
                    }
                    Object buildAll(String modelName, Project project) {
                        return new DefaultCustomModel();
                    }
                }
            """
        } else {
            buildFile << """
                import org.gradle.tooling.provider.model.ParameterizedToolingModelBuilder
                
                class CustomBuilder implements ParameterizedToolingModelBuilder<CustomParameter> {
                    boolean canBuild(String modelName) {
                        return modelName == '${CustomModel.name}'
                    }
                    Class<CustomParameter> getParameterType() {
                        return CustomParameter.class;
                    }
                    Object buildAll(String modelName, Project project) {
                        return new DefaultCustomModel();
                    }
                    Object buildAll(String modelName, CustomParameter parameter, Project project) {
                        return new DefaultCustomModel(parameter);
                    }
                }
            """
        }
    }

    @TargetGradleVersion(">=4.4")
    @ToolingApiVersion(">=4.4")
    def "can get models with parameters"() {
        when:
        def model = withConnection { connection ->
            connection.action(new ParameterAction()).run()
        }

        then:
        model.isBuiltWithParameter()
        model.getParameterValue() == "myParameter"
    }

    @TargetGradleVersion(">=4.4")
    def "can get model without parameters for old gradle versions"() {
        when:
        def model = withConnection { connection ->
            connection.action(new NoParameterAction()).run()
        }

        then:
        !model.isBuiltWithParameter()
        model.getParameterValue() == "noParameter"
    }

    @TargetGradleVersion(">=2.6 <4.4")
    @ToolingApiVersion(">=4.4")
    def "error when get model with parameters for old gradle versions"() {
        def handler = Mock(ResultHandler)
        def version = targetDist.version.version

        when:
        withConnection { connection ->
            connection.action(new ParameterAction()).run(handler)
        }

        then:
        0 * handler.onComplete(_)
        1 * handler.onFailure(_) >> { args ->
            GradleConnectionException failure = args[0]
            assert failure instanceof BuildActionFailureException
            GradleConnectionException cause = failure.cause
            assert cause instanceof UnsupportedVersionException
            assert cause.message == "Gradle version ${version} does not support parameterized tooling models."
        }
    }

    @TargetGradleVersion(">=4.4")
    @ToolingApiVersion(">=4.4")
    def "can use one model output as input for another"() {
        when:
        def model = withConnection { connection ->
            connection.action(new MultipleParametersAction()).run()
        }

        then:
        !model.get(0).isBuiltWithParameter()
        model.get(0).getParameterValue() == "noParameter"
        model.get(1).isBuiltWithParameter()
        model.get(1).getParameterValue() == "noParameter:parameter1"
        model.get(2).isBuiltWithParameter()
        model.get(2).getParameterValue() == "noParameter:parameter1:parameter2"
    }

    @TargetGradleVersion(">=4.4")
    @ToolingApiVersion(">=4.4")
    def "error when passing parameter to non parameterized builder"() {
        def handler = Mock(ResultHandler)

        when:
        withConnection { connection ->
            connection.action(new InvalidModel2Action()).run(handler)
        }

        then:
        0 * handler.onComplete(_)
        1 * handler.onFailure(_) >> { args ->
            GradleConnectionException failure = args[0]
            assert failure instanceof BuildActionFailureException
            GradleConnectionException cause = failure.cause
            assert cause instanceof UnknownModelException
            assert cause.message == "No model of type '${CustomModel2.simpleName}' is available in this build."
            assert cause.cause.message == "No parameterized builders are available to build a model of type '${CustomModel2.name}'."
        }
    }
}
