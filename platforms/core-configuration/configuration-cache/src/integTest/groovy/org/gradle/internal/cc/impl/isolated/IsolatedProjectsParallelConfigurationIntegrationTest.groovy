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

package org.gradle.internal.cc.impl.isolated

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.test.fixtures.server.http.BlockingHttpServer
import org.junit.Rule
import spock.lang.Ignore

class IsolatedProjectsParallelConfigurationIntegrationTest extends AbstractIsolatedProjectsIntegrationTest {

    @Rule
    BlockingHttpServer server = new BlockingHttpServer(5_000)

    def setup() {
        server.start()
    }

    def 'all projects are configured in parallel for #invocation'() {
        given:
        settingsFile """
            include(":a")
            include(":b")
            gradle.lifecycle.beforeProject {
                tasks.register("build")
            }
        """
        buildFile """
            ${server.callFromBuildUsingExpression("'configure-root'")}
        """
        buildFile "a/build.gradle", """
            ${server.callFromBuildUsingExpression("'configure-' + project.name")}
        """
        buildFile "b/build.gradle", """
            ${server.callFromBuildUsingExpression("'configure-' + project.name")}
        """

        server.expect("configure-root")
        server.expectConcurrent("configure-a", "configure-b")

        when:
        isolatedProjectsRun(*invocation)

        then:
        result.assertTasksExecuted(expectedTasks)

        where:
        invocation                             | expectedTasks
        ["build"]                              | [":a:build", ":b:build", ":build"]
        ["build", "--configure-on-demand"]     | [":a:build", ":b:build", ":build"]
        ["build", "--no-configure-on-demand"]  | [":a:build", ":b:build", ":build"]
        [":build"]                             | [":build"]
        [":build", "--configure-on-demand"]    | [":build"]
        [":build", "--no-configure-on-demand"] | [":build"]
    }

    @Ignore
    def "deadlock"() {
        given:
        settingsFile("build-library/settings.gradle.kts", """
            pluginManagement {
                includeBuild("../build-plugins")
            }
            include(":a")
        """)
        buildFile("build-library/a/build.gradle.kts", """
            plugins {
                id("shared")
            }
        """)
        buildFile("build-plugins/build.gradle.kts", """
            plugins {
                `groovy-gradle-plugin`
            }
            gradlePlugin {
                plugins {
                    create("shared") {
                        id = "shared"
                        implementationClass = "SharedPlugin"
                    }
                }
            }
        """)
        file("build-plugins/src/main/groovy/SharedPlugin.groovy") << """
            import ${Project.name}
            import ${Plugin.name}

            public abstract class SharedPlugin implements Plugin<Project> {

                @Override
                void apply(Project project) {}
            }
        """

        settingsFile """
            includeBuild("build-library")
        """

        expect:
        isolatedProjectsRun "help"
    }

    @Ignore
    def "plugin not found"() {
        given:
        settingsFile("build-library/settings.gradle.kts", """
            pluginManagement {
                includeBuild("../build-plugins")
            }
            include(":a")
            include(":b")
        """)
        buildFile("build-library/a/build.gradle.kts", """
            plugins {
                id("shared")
            }
        """)
        buildFile("build-library/b/build.gradle.kts", """
            plugins {
                id("shared")
            }
        """)
        buildFile("build-plugins/build.gradle.kts", """
            plugins {
                `groovy-gradle-plugin`
            }
            gradlePlugin {
                plugins {
                    create("shared") {
                        id = "shared"
                        implementationClass = "SharedPlugin"
                    }
                }
            }
        """)
        file("build-plugins/src/main/groovy/SharedPlugin.groovy") << """
            import ${Project.name}
            import ${Plugin.name}

            public abstract class SharedPlugin implements Plugin<Project> {

                @Override
                void apply(Project project) {}
            }
        """

        settingsFile """
            includeBuild("build-library")
        """

        expect:
        isolatedProjectsRun "help"
    }

    @Ignore
    def "nested plugin builds"() {
        given:
        settingsFile("build-library/settings.gradle.kts", """
            pluginManagement {
                includeBuild("../build-plugins")
            }
            include(":a")
        """)
        buildFile("build-library/a/build.gradle.kts", """
            plugins {
                id("shared")
            }
        """)
        settingsFile("build-plugins/settings.gradle.kts", """
            pluginManagement {
                includeBuild("../build-nested-plugins")
            }
            include(":a")
        """)
        buildFile("build-plugins/a/build.gradle.kts", """
            plugins {
                id("nested")
            }
        """)
        buildFile("build-plugins/build.gradle.kts", """
            plugins {
                `groovy-gradle-plugin`
            }
            gradlePlugin {
                plugins {
                    create("shared") {
                        id = "shared"
                        implementationClass = "SharedPlugin"
                    }
                }
            }
        """)
        file("build-plugins/src/main/groovy/SharedPlugin.groovy") << """
            import ${Project.name}
            import ${Plugin.name}

            public abstract class SharedPlugin implements Plugin<Project> {

                @Override
                void apply(Project project) {}
            }
        """
        file("build-nested-plugins/src/main/groovy/NestedPlugin.groovy") << """
            import ${Project.name}
            import ${Plugin.name}

            public abstract class NestedPlugin implements Plugin<Project> {
                @Override
                void apply(Project project) {}
            }
        """
        buildFile("build-nested-plugins/build.gradle.kts", """
            plugins {
                `groovy-gradle-plugin`
            }
            gradlePlugin {
                plugins {
                    create("nested") {
                        id = "nested"
                        implementationClass = "NestedPlugin"
                    }
                }

            }
        """)
        settingsFile """
            includeBuild("build-library")
        """

        expect:
        isolatedProjectsRun "help"
    }

    // TODO Test -x behavior
}
