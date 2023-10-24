/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.api.problems

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.integtests.fixtures.AbstractIntegrationSpec

/**
 * Testing if the automatically added problem transformers are present, and if they are working correctly.
 */
class InjectedProblemTransformerIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        enableProblemsApiCheck()
    }

    def "task is going to be implicitly added to the problem"() {
        given:
        buildFile """
            import org.gradle.api.problems.Problem
            import org.gradle.api.problems.Severity
            import org.gradle.internal.deprecation.Documentation

            abstract class ProblemReportingTask extends DefaultTask {
                @Inject
                protected abstract Problems getProblems();

                @TaskAction
                void run() {
                    problems.createProblem {
                        it.label("label")
                        .undocumented()
                        .noLocation()
                        .category("type")
                    }.report()
                }
            }

            tasks.register("reportProblem", ProblemReportingTask)
            """

        when:
        run("reportProblem")

        then:
        collectedProblems.size() == 1
        def problem = collectedProblems[0]

        def taskPathLocations = problem["where"].findAll {
            it["type"] == "task"
        }
        taskPathLocations.size() == 1

//        def taskPathLocation = taskPathLocations[0]
//        taskPathLocation["identityPath"]["path"] == ":reportProblem"
    }

    def "plugin id is going to be implicitly added to the problem"() {
        given:
        settingsFile << """
            includeBuild("plugins")
        """

        file("plugins/src/main/java/PluginImpl.java") << """
            import ${Project.name};
            import ${Plugin.name};
            import ${Problems.name};
            import javax.inject.Inject;

            public abstract class PluginImpl implements Plugin<Project> {

                @Inject
                protected abstract Problems getProblems();

                public void apply(Project project) {
                    getProblems().createProblem(builder ->
                        builder
                            .label("label")
                            .undocumented()
                            .noLocation()
                            .category("type")
                    ).report();
                    project.getTasks().register("reportProblem", t -> {
                        t.doLast(t2 -> {

                        });
                    });
                }
            }
        """
        file("plugins/build.gradle") << """
            plugins {
                id("java-gradle-plugin")
            }
            gradlePlugin {
                plugins {
                    plugin {
                        id = "test.plugin"
                        implementationClass = "PluginImpl"
                    }
                }
            }
        """
        buildFile.text = """
            plugins {
                id("test.plugin")
            }
        """

        when:
        run("reportProblem")

        then:
        collectedProblems.size() == 1
        def problem = collectedProblems[0]

        def pluginIdLocations = problem["where"].findAll {
            it["type"] == "pluginId"
        }
        pluginIdLocations.size() == 1

//        def pluginIdLocation = pluginIdLocations[0]
//        pluginIdLocation["pluginId"] == "test.plugin"
    }
}
