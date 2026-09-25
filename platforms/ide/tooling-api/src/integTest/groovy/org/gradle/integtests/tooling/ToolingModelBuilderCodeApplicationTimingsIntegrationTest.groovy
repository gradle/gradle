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

package org.gradle.integtests.tooling

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.BuildOperationTreeFixture
import org.gradle.integtests.fixtures.executer.GradleDistribution
import org.gradle.integtests.fixtures.executer.UnderDevelopmentGradleDistribution
import org.gradle.integtests.tooling.fixture.ToolingApi
import org.gradle.internal.code.operations.CodeApplicationsProgressDetails
import org.gradle.internal.operations.trace.BuildOperationTrace

import static org.gradle.internal.time.SimulatedWork.simulateWork
import static org.gradle.internal.time.SimulatedWork.workNanos

/**
 * Tests that time spent building a tooling model is reported by the
 * {@link CodeApplicationsProgressDetails} progress event against the application that
 * registered the model builder.
 */
class ToolingModelBuilderCodeApplicationTimingsIntegrationTest extends AbstractIntegrationSpec {

    private static final String SOME_PLUGIN = "Apply plugin SomePlugin to root project 'root'"

    final GradleDistribution dist = new UnderDevelopmentGradleDistribution()
    final ToolingApi toolingApi = new ToolingApi(dist, temporaryFolder)

    def setup() {
        settingsFile("""
            rootProject.name = "root"
        """)
    }

    def "attributes time spent in a tooling model builder to the application that registered it"() {
        given:
        buildFile("""
            import org.gradle.tooling.provider.model.ToolingModelBuilder
            import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry

            apply plugin: SomePlugin

            abstract class SomePlugin implements Plugin<Project> {
                @javax.inject.Inject
                abstract ToolingModelBuilderRegistry getRegistry()

                void apply(Project p) {
                    ${simulateWork()}

                    registry.register(new SomeModelBuilder())
                }
            }

            class SomeModelBuilder implements ToolingModelBuilder {
                boolean canBuild(String modelName) {
                    return modelName == "${CustomModel.name}"
                }

                Object buildAll(String modelName, Project project) {
                    ${simulateWork()}
                    return new SomeModel("it works")
                }
            }

            class SomeModel implements Serializable {
                private final String message
                SomeModel(String message) { this.message = message }
                String getMessage() { message }
            }
        """)

        when:
        def model = fetchCustomModel()

        then:
        model.message == "it works"

        and:
        def timings = timingsForPlugin(SOME_PLUGIN)
        timings["TOOLING_MODEL_BUILDER"] >= workNanos()
        timings.keySet() == ["MAIN", "TOOLING_MODEL_BUILDER"] as Set
    }

    private CustomModel fetchCustomModel() {
        return toolingApi.withConnection { connection ->
            connection.model(CustomModel)
                .withArguments(
                    "-D${BuildOperationTrace.SYSPROP}=${tracePath}",
                    "-D${BuildOperationTrace.TREE_SYSPROP}=false"
                )
                .get()
        }
    }

    /**
     * The timings reported for the application made by the given plugin application operation.
     */
    private Map<String, Long> timingsForPlugin(String pluginDisplayName) {
        def operations = new BuildOperationTreeFixture(BuildOperationTrace.readTree(tracePath))

        def applicationId = operations.only(pluginDisplayName).details.applicationId
        assert applicationId != null

        def events = operations.progress(CodeApplicationsProgressDetails)
        assert events.size() == 1, "Expected one timings event, found ${events.size()}"

        def application = events.first().details["codeApplications"][applicationId.toString()] as Map<String, Object>
        assert application != null, "No code application reported for '$pluginDisplayName'"
        return application["timings"] as Map<String, Long>
    }

    private String getTracePath() {
        return file("operations").absolutePath
    }

}

interface CustomModel {

    String getMessage()

}
