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

package org.gradle.api

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.internal.jvm.Jvm
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.TestExecutionPreconditions
import org.gradle.util.internal.TextUtil
import spock.lang.Issue

@Issue(["https://github.com/gradle/gradle/issues/13121", "https://github.com/gradle/gradle/issues/39131"])
class ServiceLookupIntegrationTest extends AbstractIntegrationSpec {

    def "all documented services can be looked up in a build script"() {
        buildFile """
            def services = [
                service(ObjectFactory),
                service(ProviderFactory),
                service(FileSystemOperations),
                service(ArchiveOperations),
                service(ProjectLayout),
            ]
            println("resolved services: " + services.count { it != null })
        """

        expect:
        succeeds("help")
        outputContains("resolved services: 5")
    }

    def "all documented services can be looked up in a settings script"() {
        settingsFile """
            def services = [
                service(ObjectFactory),
                service(ProviderFactory),
                service(FileSystemOperations),
                service(ArchiveOperations),
                service(BuildLayout),
            ]
            println("resolved services: " + services.count { it != null })

            println("settings dir name: " + service(BuildLayout).settingsDirectory.asFile.name)
        """

        expect:
        succeeds("help")
        outputContains("resolved services: 5")
        outputContains("settings dir name: " + testDirectory.name)
    }

    def "can capture #serviceType at configuration time and use it in a task action"() {
        file("doomed.txt").text = "content"
        file("zip-src/entry.txt").text = "entry"
        file("zip-src").zipTo(file("stuff.zip"))
        buildFile """
            tasks.register("useObjectFactory") {
                def captured = service(ObjectFactory)
                doLast {
                    def p = captured.property(String)
                    p.set("captured-value")
                    println("out: " + p.get())
                }
            }
            tasks.register("useProviderFactory") {
                def captured = service(ProviderFactory)
                doLast {
                    println("out: " + captured.provider { "provided-value" }.get())
                }
            }
            tasks.register("useFileSystemOperations") {
                def captured = service(FileSystemOperations)
                def targetFile = file("doomed.txt")
                doLast {
                    captured.delete { delete(targetFile) }
                    println("out: exists=" + targetFile.exists())
                }
            }
            tasks.register("useArchiveOperations") {
                def captured = service(ArchiveOperations)
                doLast {
                    println("out: " + captured.zipTree("stuff.zip").files*.name.sort())
                }
            }
            tasks.register("useProjectLayout") {
                def captured = service(ProjectLayout)
                doLast {
                    println("out: " + captured.projectDirectory.asFile.name)
                }
            }
        """

        when:
        succeeds("use$serviceType")

        then:
        outputContains(expectedOutput.replace("@PROJECT_DIR_NAME@", testDirectory.name))

        where:
        serviceType            | expectedOutput
        "ObjectFactory"        | "out: captured-value"
        "ProviderFactory"      | "out: provided-value"
        "FileSystemOperations" | "out: exists=false"
        "ArchiveOperations"    | "out: [entry.txt]"
        "ProjectLayout"        | "out: @PROJECT_DIR_NAME@"
    }

    def "a service can be looked up from a task action"() {
        file("thing.txt").text = "content"
        buildFile """
            tasks.register("cleanThing") {
                doLast {
                    service(FileSystemOperations).delete {
                        delete("thing.txt")
                    }
                }
            }
        """

        when:
        succeeds("cleanThing")

        then:
        !file("thing.txt").exists()
    }

    def "a service can be looked up from a closure nested inside a task action"() {
        buildFile """
            tasks.register("nested") {
                doLast {
                    def makeProperty = { service(ObjectFactory).property(String) }
                    def p = makeProperty()
                    p.set("nested-value")
                    println("out: " + p.get())
                }
            }
        """

        when:
        succeeds("nested")

        then:
        outputContains("out: nested-value")
    }

    def "ExecOperations can be looked up from a task action"() {
        buildFile """
            tasks.register("run") {
                doLast {
                    def result = service(ExecOperations).exec { commandLine("${jvmPath}", "-version") }
                    println("out: " + result.exitValue)
                }
            }
        """

        when:
        succeeds("run")

        then:
        outputContains("out: 0")
    }

    private static String getJvmPath() {
        return TextUtil.escapeString(Jvm.current().javaExecutable.absolutePath)
    }

    def "FileSystemOperations from a project script resolves paths relative to the project directory"() {
        settingsFile """
            include("sub")
        """
        file("local.txt").text = "root"
        file("sub/local.txt").text = "sub"
        buildFile("sub/build.gradle", """
            tasks.register("cleanLocal") {
                doLast {
                    service(FileSystemOperations).delete {
                        delete("local.txt")
                    }
                }
            }
        """)

        when:
        succeeds(":sub:cleanLocal")

        then:
        !file("sub/local.txt").exists()
        file("local.txt").exists()
    }

    def "each project resolves its own services"() {
        settingsFile """
            include("a")
        """
        def pingTask = """
            def captured = service(ProjectLayout)
            tasks.register("ping") {
                def projectPath = project.path
                doLast {
                    println("layout for " + projectPath + ": " + captured.projectDirectory.asFile.name)
                }
            }
        """
        buildFile pingTask
        buildFile("a/build.gradle", pingTask)

        when:
        succeeds("ping")

        then:
        outputContains("layout for :: " + testDirectory.name)
        outputContains("layout for :a: a")
    }

    @Requires(value = TestExecutionPreconditions.NotIsolatedProjects, reason = "configuring another project is reported by Isolated Projects, see IsolatedProjectsServiceLookupIntegrationTest")
    def "inside a subprojects block the lookup resolves the services of the subproject"() {
        createDirs("a")
        settingsFile """
            include("a")
        """
        buildFile """
            subprojects {
                println("layout for " + path + ": " + service(ProjectLayout).projectDirectory.asFile.name)
            }
        """

        when:
        succeeds("help")

        then:
        outputContains("layout for :a: a")
    }

    def "a script plugin applied to a project resolves services of that project"() {
        createDirs("a")
        settingsFile """
            include("a")
        """
        file("plugin.gradle") << """
            println("project dir: " + service(ProjectLayout).projectDirectory.asFile.name)
        """
        buildFile("a/build.gradle", """
            apply from: '../plugin.gradle'
        """)

        when:
        succeeds("help")

        then:
        outputContains("project dir: a")
    }

    def "a script plugin applied to settings resolves services of the settings"() {
        file("plugin.gradle") << """
            println("settings dir: " + service(BuildLayout).settingsDirectory.asFile.name)
        """
        settingsFile """
            apply from: 'plugin.gradle'
        """

        when:
        succeeds("help")

        then:
        outputContains("settings dir: " + testDirectory.name)
    }

    def "service lookup is not available on the Gradle object"() {
        settingsFile """
            try {
                gradle.service(ObjectFactory)
            } catch (MissingMethodException e) {
                println("gradle in settings: no service method")
            }
        """
        buildFile """
            try {
                gradle.service(ObjectFactory)
            } catch (MissingMethodException e) {
                println("gradle in build script: no service method")
            }
        """

        when:
        succeeds("help")

        then:
        outputContains("gradle in settings: no service method")
        outputContains("gradle in build script: no service method")
    }

    def "looking up a project-only service from a settings script fails with a helpful message"() {
        settingsFile """
            service(ProjectLayout)
        """

        when:
        fails("help")

        then:
        failure.assertHasCause("org.gradle.api.file.ProjectLayout is not available in settings scripts and settings plugins." +
            "\nIt is available in project scripts and project plugins and tasks.")
    }

    def "looking up a settings-only service from a build script fails with a helpful message"() {
        buildFile """
            service(BuildLayout)
        """

        when:
        fails("help")

        then:
        failure.assertHasCause("org.gradle.api.file.BuildLayout is not available in project scripts and project plugins." +
            "\nIt is available in settings scripts and settings plugins.")
    }

    def "looking up an execution-only service from a build script fails with a helpful message"() {
        buildFile """
            service(ExecOperations)
        """

        when:
        fails("help")

        then:
        failure.assertHasCause("org.gradle.process.ExecOperations is not available in project scripts and project plugins.\nIt is available in tasks.")
    }

    def "looking up a settings-only service through a task closure resolves to the task and is rejected at configuration time"() {
        settingsFile """
            gradle.rootProject {
                tasks.register("useLayout") {
                    // `service` here resolves to the task, which does not expose the settings-only BuildLayout
                    def captured = service(BuildLayout)
                    doLast {
                        println("REACHED ACTION")
                    }
                }
            }
        """

        when:
        fails(":useLayout")

        then:
        failure.assertHasCause("Could not create task ':useLayout'.")
        failure.assertHasCause("org.gradle.api.file.BuildLayout is not available in tasks." +
            "\nIt is available in settings scripts and settings plugins.")
        outputDoesNotContain("REACHED ACTION")
    }

    def "looking up an internal service fails and enumerates the available services"() {
        buildFile """
            service(org.gradle.api.internal.project.ProjectInternal)
        """

        when:
        fails("help")

        then:
        failure.assertHasCause("org.gradle.api.internal.project.ProjectInternal is not a service that is available for lookup with service(). " +
            "The following services are available in project scripts and project plugins:\n" +
            " - org.gradle.api.file.ArchiveOperations\n" +
            " - org.gradle.api.file.FileSystemOperations\n" +
            " - org.gradle.api.model.ObjectFactory\n" +
            " - org.gradle.api.file.ProjectLayout\n" +
            " - org.gradle.api.provider.ProviderFactory")
    }

    def "a user type that implements a scope marker but is not a Gradle service is still rejected at runtime"() {
        buildFile """
            abstract class NotAService implements org.gradle.api.services.ProjectService {}

            service(NotAService)
        """

        when:
        fails("help")

        then:
        failure.assertHasCause("NotAService is not a service that is available for lookup with service().")
    }

    def "looking up a shared build service fails with a pointer to the build service APIs"() {
        buildFile """
            abstract class CounterService implements org.gradle.api.services.BuildService<org.gradle.api.services.BuildServiceParameters.None> {
            }

            service(CounterService)
        """

        when:
        fails("help")

        then:
        failure.assertHasCause("CounterService is a shared build service, which cannot be obtained with service(). " +
            "Register it with gradle.sharedServices.registerIfAbsent() and access it " +
            "via a property annotated with @ServiceReference, or via the provider returned from registration.")
    }

    def "looking up a null service type fails with a helpful message"() {
        buildFile """
            service(null)
        """

        when:
        fails("help")

        then:
        failure.assertHasCause("The service type given to service() must not be null.")
    }
}
