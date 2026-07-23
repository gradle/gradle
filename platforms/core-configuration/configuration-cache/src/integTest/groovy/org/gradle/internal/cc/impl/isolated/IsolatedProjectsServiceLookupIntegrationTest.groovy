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

package org.gradle.internal.cc.impl.isolated

import spock.lang.Issue

@Issue("https://github.com/gradle/gradle/issues/13121")
class IsolatedProjectsServiceLookupIntegrationTest extends AbstractIsolatedProjectsIntegrationTest {

    def "can look up a service in a task action of the owning project"() {
        settingsFile """
            include("a")
        """
        file("a/thing.txt").text = "content"
        file("a/build.gradle") << """
            tasks.register("cleanThing") {
                doLast {
                    service(FileSystemOperations).delete {
                        delete("thing.txt")
                    }
                }
            }
        """

        when:
        isolatedProjectsRun(":a:cleanThing")

        then:
        fixture.assertStateStored {
            projectsConfigured(":", ":a")
        }
        !file("a/thing.txt").exists()
    }

    def "can look up a service at configuration time of the owning project"() {
        createDirs("a")
        settingsFile """
            include("a")
        """
        file("a/build.gradle") << """
            def dirName = service(ProjectLayout).projectDirectory.asFile.name
            tasks.register("show") {
                doLast {
                    println("project dir name: " + dirName)
                }
            }
        """

        when:
        isolatedProjectsRun(":a:show")

        then:
        fixture.assertStateStored {
            projectsConfigured(":", ":a")
        }
        outputContains("project dir name: a")
    }

    def "reports a problem when a build script looks up a service on another project"() {
        createDirs("a", "b")
        settingsFile """
            include("a")
            include("b")
        """
        buildFile """
            project(':a').service(ObjectFactory)
        """

        when:
        isolatedProjectsFailsUsing(mode, "help")

        then:
        fixture.assertIsolatedProjectsProblems(mode) {
            projectsConfigured(":", ":a", ":b")
            problem("Build file 'build.gradle': line 2: Project ':' cannot access 'Project.service' functionality on another project ':a'")
        }

        where:
        mode << ALL_MODES
    }

    def "can look up a service in a settings script"() {
        settingsFile """
            def layout = service(BuildLayout)
            println("settings dir name: " + layout.settingsDirectory.asFile.name)
        """

        when:
        isolatedProjectsRun("help")

        then:
        fixture.assertStateStored {
            projectsConfigured(":")
        }
        outputContains("settings dir name: " + testDirectory.name)
    }

    def "can capture a settings-scoped service and use it from an isolated project action"() {
        createDirs("a")
        settingsFile """
            include("a")
            // Captured at settings scope and used from an isolated 'beforeProject' action,
            // which is serialized per project. The captured BuildLayout must survive that.
            def captured = service(BuildLayout)
            gradle.lifecycle.beforeProject { project ->
                println(project.path + " settings dir: " + captured.settingsDirectory.asFile.name)
            }
        """

        when:
        isolatedProjectsRun(":a:help")

        then:
        fixture.assertStateStored {
            projectsConfigured(":", ":a")
        }
        outputContains(":a settings dir: " + testDirectory.name)
    }
}
