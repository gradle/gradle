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

package org.gradle.api.tasks

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.DirectoryBuildCacheFixture
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.internal.reflect.validation.ValidationMessageChecker
import spock.lang.Requires

class BuildResultLoggerIntegrationTest extends AbstractIntegrationSpec implements DirectoryBuildCacheFixture, ValidationMessageChecker {
    def setup() {

        file("input.txt") << "data"
        buildFile << """
            task adHocTask {
                outputs.cacheIf { true }
                def outputFile = file("\$buildDir/output.txt")
                inputs.file(file("input.txt"))
                outputs.file(outputFile)
                doLast {
                    outputFile.parentFile.mkdirs()
                    outputFile.text = file("input.txt").text
                }
            }

            task executedTask {
                doLast {
                    println "Hello world"
                }
            }

            task noActions(dependsOn: executedTask) {}
        """
    }

    def "task outcome statistics are reported"() {
        when:
        run "adHocTask", "executedTask"

        then:
        result.assertTasksNotSkipped(":adHocTask", ":executedTask")
        result.assertHasPostBuildOutput "2 actionable tasks: 2 executed"

        when:
        run "adHocTask", "executedTask"

        then:
        result.assertTaskSkipped(":adHocTask")
        result.assertTasksNotSkipped(":executedTask")
        result.assertHasPostBuildOutput "2 actionable tasks: 1 executed, 1 up-to-date"
    }

    def "cached task outcome statistics are reported"() {
        when:
        withBuildCache().run "adHocTask", "executedTask"

        then:
        result.assertTasksNotSkipped(":adHocTask", ":executedTask")
        result.assertHasPostBuildOutput "2 actionable tasks: 2 executed"

        when:
        file("build").deleteDir()
        withBuildCache().run "adHocTask", "executedTask"

        then:
        result.assertTasksSkipped(":adHocTask")
        result.assertTasksNotSkipped(":executedTask")
        result.assertHasPostBuildOutput "2 actionable tasks: 1 executed, 1 from cache"
    }

    def "tasks with no actions are not counted in stats"() {
        when:
        run "noActions"

        then:
        result.assertTasksNotSkipped(":noActions", ":executedTask")
        result.assertHasPostBuildOutput "1 actionable task: 1 executed"
    }

    def "skipped tasks are not counted"() {
        given:
        executer.withArguments "-x", "executedTask"

        when:
        run "noActions"

        then:
        // No stats are reported because no tasks had any actions
        result.assertTasksNotSkipped(":noActions")
        result.assertNotOutput("actionable tasks")
    }

    @ToBeFixedForConfigurationCache(because = "buildSrc tasks are not executed when loaded from cache")
    def "reports tasks from buildSrc"() {
        file("buildSrc/src/main/java/Thing.java") << """
            public class Thing {
            }
        """

        when:
        run "adHocTask"

        then:
        result.assertTasksNotSkipped(":buildSrc:compileJava", ":buildSrc:jar", ":buildSrc:classes", ":adHocTask")
        result.assertHasPostBuildOutput("3 actionable tasks: 3 executed")

        when:
        run "adHocTask"

        then:
        result.assertHasPostBuildOutput("3 actionable tasks: 3 up-to-date")
    }

    def "reports tasks from included builds"() {
        settingsFile << """
            includeBuild "child"
        """
        file("child/build.gradle") << """
            task executedTask {
                doLast {
                    // Do something
                }
            }
        """
        buildFile << """
            executedTask.dependsOn(gradle.includedBuild("child").task(":executedTask"))
        """

        when:
        run("executedTask")

        then:
        result.assertTasksNotSkipped(":child:executedTask", ":executedTask")
        result.assertHasPostBuildOutput "2 actionable tasks: 2 executed"
    }

    @ToBeFixedForConfigurationCache(because = "build logic tasks are not executed when loaded from cache")
    def "reports tasks from included builds that provide project plugins"() {
        settingsFile << """
            includeBuild("plugins")
        """
        file("plugins/src/main/java/PluginImpl.java") << """
            import ${Project.name};
            import ${Plugin.name};

            public class PluginImpl implements Plugin<Project> {
                public void apply(Project project) {
                    project.getTasks().register("executedTask", t -> {
                        t.doLast(t2 -> {
                            // Do something
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
        run("executedTask")

        then:
        result.assertTasksNotSkipped(":plugins:compileJava", ":plugins:pluginDescriptors", ":plugins:processResources", ":plugins:classes", ":plugins:jar", ":executedTask")
        result.assertHasPostBuildOutput "5 actionable tasks: 5 executed"

        when:
        run("executedTask")

        then:
        result.assertHasPostBuildOutput "5 actionable tasks: 1 executed, 4 up-to-date"
    }

    @Requires({ GradleContextualExecuter.embedded })
    // this test only works in embedded mode because of the use of validation test fixtures
    def "work validation warnings are mentioned in summary"() {
        buildFile << """
            import org.gradle.integtests.fixtures.validation.ValidationProblem

            class InvalidTask extends DefaultTask {
                @ValidationProblem String dummy

                @TaskAction void execute() {
                    // Do nothing
                }
            }

            tasks.register("invalid", InvalidTask)
        """

        expectThatExecutionOptimizationDisabledWarningIsDisplayed(executer, dummyValidationProblem(), 'id', 'section')

        when:
        run "invalid"

        then:
        outputContains "Execution optimizations have been disabled for 1 invalid unit(s) of work during this build to ensure correctness."
        outputContains "Please consult deprecation warnings for more details."
    }
}
