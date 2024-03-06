/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.integtests.tooling.r88

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.test.fixtures.server.http.BlockingHttpServer
import org.junit.Rule

@TargetGradleVersion(">=8.8")
@ToolingApiVersion(">=8.8")
class ParallelActionExecutionCrossVersionSpec extends ToolingApiSpecification {
    @Rule
    BlockingHttpServer server = new BlockingHttpServer()

    def setup() {
        server.start()

        settingsFile << """
            rootProject.name = 'root'
            include 'a', 'b'
        """

        buildFile << """
            import javax.inject.Inject
            import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry
            import org.gradle.tooling.provider.model.ToolingModelBuilder

            class CustomPlugin implements Plugin<Project> {
                ToolingModelBuilderRegistry registry

                @Inject
                CustomPlugin(ToolingModelBuilderRegistry registry) {
                    this.registry = registry
                }

                void apply(Project project) {
                    registry.register(new CustomBuilder())
                }
            }

            class CustomModel implements Serializable {
                String path
            }

            class CustomBuilder implements ToolingModelBuilder {
                boolean canBuild(String modelName) {
                    return modelName == "org.gradle.integtests.tooling.r88.CustomModel"
                }

                Object buildAll(String modelName, Project project) {
                    ${server.callFromBuildUsingExpression("project.name")}
                    return new CustomModel(path: project.path);
                }
            }
        """

        buildFile << """
            allprojects {
                apply plugin: CustomPlugin
                apply plugin: 'java'
            }
        """
    }

    def "nested actions that query a project model run in parallel by default"() {
        server.expectConcurrent('root', 'a', 'b')

        when:
        def models = withConnection {
            def action = action(new ActionRunsNestedActions())
            action.standardOutput = System.out
            action.standardError = System.err
            action.run()
        }

        then:
        models.mayRunInParallel
        models.projects.path == [':', ':a', ':b']
    }

    def "nested actions that query a project model do not run in parallel when target Gradle version supports it and disabled"() {
        server.expectConcurrent(1, 'root', 'a', 'b')

        when:
        def models = withConnection {
            def action = action(new ActionRunsNestedActions())
            collectOutputs(action)
            action.addArguments(["-Dorg.gradle.internal.tooling.parallel=false"])
            action.run()
        }

        then:
        !models.mayRunInParallel
        models.projects.path == [':', ':a', ':b']
    }

    // TODO: add test showing that --(no-)parallel does not affect
}
