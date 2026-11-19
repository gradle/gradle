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
import org.gradle.integtests.tooling.fixture.ToolingApi
import spock.lang.Issue

class ToolingApiParallelModelBuildingIntegrationTest extends AbstractIntegrationSpec {

    final ToolingApi toolingApi = new ToolingApi(distribution, temporaryFolder)

    def setup() {
        toolingApi.requireIsolatedDaemons()
    }

    @Issue("https://github.com/gradle/gradle/issues/39657")
    def "views shared by parallel nested actions do not return null for non-null properties"() {
        given:
        64.times {
            createDirs("p$it")
            settingsFile << "include 'p$it'\n"
        }
        propertiesFile << "org.gradle.tooling.parallel=true"
        buildFile << """
            import org.gradle.tooling.provider.model.ToolingModelBuilder
            import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry

            import javax.inject.Inject

            class Edge {
                final String from
                final String to

                Edge(String from, String to) {
                    this.from = from
                    this.to = to
                }
            }

            class Dependency {
                final Edge edge

                Dependency(Edge edge) {
                    this.edge = edge
                }
            }

            class DependencyGraph {
                final List<Dependency> dependencies

                DependencyGraph(List<Dependency> dependencies) {
                    this.dependencies = dependencies
                }
            }

            // Every project gets its own Dependency objects, all pointing to the same Edge instances,
            // so the parallel nested actions read the same Edge views
            class SharedEdgesPlugin implements Plugin<Project> {
                static final List<Edge> EDGES = (0..<20000).collect { new Edge("lib" + it, "lib" + (it + 1)) }

                private final ToolingModelBuilderRegistry registry

                @Inject
                SharedEdgesPlugin(ToolingModelBuilderRegistry registry) {
                    this.registry = registry
                }

                void apply(Project project) {
                    registry.register(new ToolingModelBuilder() {
                        boolean canBuild(String modelName) {
                            modelName == "${SharedEdgesModel.name}"
                        }

                        Object buildAll(String modelName, Project target) {
                            new DependencyGraph(EDGES.collect { new Dependency(it) })
                        }
                    })
                }
            }

            allprojects {
                apply plugin: SharedEdgesPlugin
            }
        """

        when:
        List<String> nullReads = []
        toolingApi.withConnection { connection ->
            // Each run adapts the models into a new object graph, so the views start with empty caches
            for (int run = 0; run < 10 && nullReads.empty; run++) {
                nullReads += connection.action(new ReadSharedEdgesInParallelAction())
                    // The race shows up with C1-compiled code much more often than with C2
                    .addJvmArguments("-XX:TieredStopAtLevel=1")
                    .run()
            }
        }

        then:
        nullReads == []
    }
}
