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

@Issue("https://github.com/gradle/gradle/issues/39131")
class IsolatedProjectsServiceLookupIntegrationTest extends AbstractIsolatedProjectsIntegrationTest {

    def "can capture a service in the build script of the owning project and use it in a task action"() {
        settingsFile """
            include("a")
        """
        file("a/thing.txt").text = "content"
        buildFile("a/build.gradle", """
            def fs = service(FileSystemOperations)
            tasks.register("cleanThing") {
                doLast {
                    fs.delete {
                        delete("thing.txt")
                    }
                }
            }
        """)

        when:
        isolatedProjectsRun(":a:cleanThing")

        then:
        fixture.assertStateStored {
            projectsConfigured(":", ":a")
        }
        and:
        !file("a/thing.txt").exists()
    }

    def "can look up a service in a task action of the owning project"() {
        settingsFile """
            include("a")
        """
        file("a/thing.txt").text = "content"
        buildFile("a/build.gradle", """
            tasks.register("cleanThing") {
                doLast {
                    service(FileSystemOperations).delete {
                        delete("thing.txt")
                    }
                }
            }
        """)

        when:
        isolatedProjectsRun(":a:cleanThing")

        then:
        fixture.assertStateStored {
            projectsConfigured(":", ":a")
        }
        and:
        !file("a/thing.txt").exists()
    }

    def "can look up a service at configuration time of the owning project"() {
        settingsFile """
            include("a")
        """
        buildFile("a/build.gradle", """
            def dirName = service(ProjectLayout).projectDirectory.asFile.name
            tasks.register("show") {
                doLast {
                    println("project dir name: " + dirName)
                }
            }
        """)

        when:
        isolatedProjectsRun(":a:show")

        then:
        fixture.assertStateStored {
            projectsConfigured(":", ":a")
        }
        and:
        outputContains("project dir name: a")
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
        and:
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
        and:
        outputContains(":a settings dir: " + testDirectory.name)
    }

    def "there is no service lookup on another project and trying to use one is reported"() {
        createDirs("a")
        settingsFile """
            include("a")
        """
        buildFile """
            try {
                project(':a').service(ObjectFactory)
            } catch (MissingMethodException ignored) {
                // When violations are only collected, the call goes on and finds no such method
            }
        """

        when:
        isolatedProjectsFailsUsing(mode, "help")

        then:
        fixture.assertIsolatedProjectsProblems(mode) {
            projectsConfigured(":", ":a")
            problem("Build file 'build.gradle': line 3: Project ':' cannot access 'service' extension on another project ':a'")
        }

        where:
        mode << ALL_MODES
    }

    def "looking up a service inside a subprojects block is reported"() {
        createDirs("a")
        settingsFile """
            include("a")
        """
        buildFile """
            subprojects {
                // The block delegates to the subproject first, and asking it for `service` is a cross-project access
                service(ProjectLayout)
            }
        """

        when:
        isolatedProjectsFailsUsing(mode, "help")

        then:
        fixture.assertIsolatedProjectsProblems(mode) {
            projectsConfigured(":", ":a")
            problem("Build file 'build.gradle': line 4: Project ':' cannot access 'service' extension on subprojects")
        }

        where:
        mode << ALL_MODES
    }

    def "looking up a service on a task of another project is reported as cross-project task access"() {
        createDirs("a")
        settingsFile """
            include("a")
        """
        buildFile """
            project(':a').tasks.register('x').get().service(ObjectFactory)
        """

        when:
        isolatedProjectsFailsUsing(mode, "help")

        then:
        fixture.assertIsolatedProjectsProblems(mode) {
            projectsConfigured(":", ":a")
            problem("Build file 'build.gradle': line 2: Project ':' cannot access 'Project.tasks' functionality on another project ':a'")
        }

        where:
        mode << ALL_MODES
    }
}
