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

package org.gradle.configuration.project

import org.gradle.integtests.fixtures.AbstractIntegrationSpec


class ProjectBuildIntegrationTest extends AbstractIntegrationSpec {

    def "can inject build view into project plugin"() {
        buildFile """
            abstract class SomePlugin implements Plugin<Project> {
                @Inject
                abstract ProjectBuild getProjectBuildAsService()

                void apply(Project p) {
                    if (projectBuildAsService != null) println("project build view is available")
                }
            }

            apply plugin: SomePlugin
        """

        when:
        run "help"

        then:
        outputContains("project build view is available")
    }

    def "can resolve a file relative to the root directory"() {
        file("config.txt") << "root-config"

        createDirs("sub")
        settingsFile """
            include(":sub")

            abstract class CustomTask extends DefaultTask {
                @InputFile @PathSensitive(PathSensitivity.RELATIVE)
                abstract RegularFileProperty getConfig();

                @TaskAction void run() {
                    println("Task '\$path' uses '\${config.get().asFile.text}'")
                }
            }

            gradle.lifecycle.beforeProject { project ->
                project.tasks.register("something", CustomTask) {
                    config = project.enclosingBuild.rootDirectory.file("config.txt")
                }
            }
        """

        when:
        run "something"
        then:
        outputContains("Task ':something' uses 'root-config'")
        outputContains("Task ':sub:something' uses 'root-config'")
    }

    def "root directory is within the included build"() {
        file("config.txt") << "root-config"
        file("included/config.txt") << "included-config"

        settingsFile """
            includeBuild("included")
        """

        buildFile "included/build.gradle", """
            println("Included build config: '\${project.enclosingBuild.rootDirectory.file("config.txt").asFile.text}'")
        """

        when:
        run "help"
        then:
        outputContains("Included build config: 'included-config'")
    }
}
