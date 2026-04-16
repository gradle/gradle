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

package org.gradle.integtests.tooling.r960

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.TestExecutionPreconditions
import org.gradle.tooling.BuildAction
import org.gradle.tooling.BuildController
import spock.lang.Issue

@Issue("https://github.com/gradle/gradle/issues/37550")
@ToolingApiVersion(">=8.13")
@TargetGradleVersion(">=9.6")
@Requires(
    value = TestExecutionPreconditions.NotEmbeddedExecutor,
    reason = "JVM arguments require the external executor to be passed to the Gradle daemon."
)
class JvmArgumentSystemPropertyCrossVersionSpec extends ToolingApiSpecification {

    interface StartParameterPropertyModel {
        String getPropertyValue()
    }

    static class FetchStartParameterPropertyAction implements BuildAction<StartParameterPropertyModel>, Serializable {
        @Override
        StartParameterPropertyModel execute(BuildController controller) {
            return controller.getModel(StartParameterPropertyModel)
        }
    }

    def setup() {
        groovyFile "buildSrc/src/main/groovy/StartParameterPropertyPlugin.groovy", """
            import javax.inject.Inject
            import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry
            import org.gradle.tooling.provider.model.ToolingModelBuilder
            import org.gradle.api.Project
            import org.gradle.api.Plugin

            abstract class StartParameterPropertyPlugin implements Plugin<Project> {

                @Inject
                abstract ToolingModelBuilderRegistry getRegistry()

                void apply(Project project) {
                    registry.register(new StartParameterPropertyBuilder())
                }
            }

            class StartParameterPropertyModelImpl implements Serializable {
                String propertyValue
            }

            class StartParameterPropertyBuilder implements ToolingModelBuilder {

                boolean canBuild(String modelName) {
                    return modelName.endsWith("StartParameterPropertyModel")
                }

                Object buildAll(String modelName, Project project) {
                    def value = project.gradle.startParameter.systemPropertiesArgs.get("test.system.property")
                    return new StartParameterPropertyModelImpl(propertyValue: value)
                }
            }
        """

        buildFile """
            apply plugin: StartParameterPropertyPlugin
        """
    }

    def "system properties passed via addJvmArguments are available in startParameter.systemPropertiesArgs"() {
        when:
        def model = withConnection { connection ->
            connection.action(new FetchStartParameterPropertyAction())
                .addJvmArguments("-Dtest.system.property=expected-value")
                .run()
        }

        then:
        model.propertyValue == "expected-value"
    }
}
