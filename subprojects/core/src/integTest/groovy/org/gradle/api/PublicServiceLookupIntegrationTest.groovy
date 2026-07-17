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
import org.gradle.util.internal.TextUtil
import spock.lang.Issue

@Issue("https://github.com/gradle/gradle/issues/13121")
class PublicServiceLookupIntegrationTest extends AbstractIntegrationSpec {

    def "all documented services can be looked up in a build script"() {
        buildFile << """
            def services = [
                service(ObjectFactory),
                service(ProviderFactory),
                service(FileSystemOperations),
                service(ArchiveOperations),
                service(ExecOperations),
                service(ProjectLayout),
            ]
            println("resolved services: " + services.count { it != null })
        """

        expect:
        succeeds("help")
        outputContains("resolved services: 6")
    }

    def "can delete files with FileSystemOperations looked up inside a task action"() {
        file("thing.txt").text = "content"
        buildFile << """
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

    def "can capture #serviceType at configuration time and use it in a task action"() {
        file("doomed.txt").text = "content"
        file("zip-src/entry.txt").text = "entry"
        file("zip-src").zipTo(file("stuff.zip"))
        buildFile << """
            tasks.register("use") {
                def captured = service($serviceType)
                def targetFile = file("doomed.txt")
                doLast {
                    ${action.replace("JAVA_EXE", jvmPath)}
                }
            }
        """

        when:
        succeeds("use")

        then:
        outputContains(expectedOutput.replace("@PROJECT_DIR_NAME@", testDirectory.name))

        where:
        serviceType            | action                                                                                        | expectedOutput
        "ObjectFactory"        | 'def p = captured.property(String); p.set("captured-value"); println("out: " + p.get())'     | "out: captured-value"
        "ProviderFactory"      | 'println("out: " + captured.provider { "provided-value" }.get())'                            | "out: provided-value"
        "FileSystemOperations" | 'captured.delete { delete(targetFile) }; println("out: exists=" + targetFile.exists())'      | "out: exists=false"
        "ArchiveOperations"    | 'println("out: " + captured.zipTree("stuff.zip").files*.name.sort())'                        | "out: [entry.txt]"
        "ExecOperations"       | 'def r = captured.exec { commandLine("JAVA_EXE", "-version") }; println("out: " + r.exitValue)' | "out: 0"
        "ProjectLayout"        | 'println("out: " + captured.projectDirectory.asFile.name)'                                   | "out: @PROJECT_DIR_NAME@"
    }

    private static String getJvmPath() {
        return TextUtil.escapeString(Jvm.current().javaExecutable.absolutePath)
    }

    def "FileSystemOperations from a project script resolves paths relative to the project directory"() {
        createDirs("sub")
        settingsFile << """
            include("sub")
        """
        file("local.txt").text = "root"
        file("sub/local.txt").text = "sub"
        file("sub/build.gradle") << """
            tasks.register("cleanLocal") {
                doLast {
                    service(FileSystemOperations).delete {
                        delete("local.txt")
                    }
                }
            }
        """

        when:
        succeeds(":sub:cleanLocal")

        then:
        !file("sub/local.txt").exists()
        file("local.txt").exists()
    }

    def "services can be looked up in a settings script"() {
        settingsFile << """
            def layout = service(BuildLayout)
            println("settings dir name: " + layout.settingsDirectory.asFile.name)

            def property = service(ObjectFactory).property(String)
            property.set("from-settings")
            println("settings property: " + property.get())
        """

        expect:
        succeeds("help")
        outputContains("settings dir name: " + testDirectory.name)
        outputContains("settings property: from-settings")
    }

    def "services can be looked up in an init script"() {
        initScriptFile << """
            def property = service(ObjectFactory).property(String)
            property.set("from-init")
            println("init property: " + property.get())
            println("init exec ops: " + (service(ExecOperations) != null))
        """

        expect:
        args("-I", "init.gradle")
        succeeds("help")
        outputContains("init property: from-init")
        outputContains("init exec ops: true")
    }

    def "ProjectLayout can be looked up on a task at configuration time"() {
        buildFile << """
            tasks.register("showLayout") {
                def dirName = service(ProjectLayout).projectDirectory.asFile.name
                doLast {
                    println("project dir name: " + dirName)
                }
            }
        """

        expect:
        succeeds("showLayout")
        outputContains("project dir name: " + testDirectory.name)
    }

    def "looking up a project-only service from a settings script fails with a helpful message"() {
        settingsFile << """
            service(ProjectLayout)
        """

        when:
        fails("help")

        then:
        failure.assertHasCause("org.gradle.api.file.ProjectLayout is not available in settings scripts and plugins. It is available in project scripts and plugins and tasks.")
    }

    def "looking up an internal service fails and enumerates the available services"() {
        buildFile << """
            service(org.gradle.api.internal.project.ProjectInternal)
        """

        when:
        fails("help")

        then:
        failure.assertHasCause("org.gradle.api.internal.project.ProjectInternal is not a service that is available for lookup with service(). " +
            "The following services are available in project scripts and plugins: " +
            "org.gradle.api.model.ObjectFactory, org.gradle.api.provider.ProviderFactory, " +
            "org.gradle.api.file.FileSystemOperations, org.gradle.api.file.ArchiveOperations, " +
            "org.gradle.process.ExecOperations, org.gradle.api.file.ProjectLayout.")
    }

    def "looking up a shared build service fails with a pointer to the build service APIs"() {
        buildFile << """
            abstract class CounterService implements org.gradle.api.services.BuildService<org.gradle.api.services.BuildServiceParameters.None> {
            }

            service(CounterService)
        """

        when:
        fails("help")

        then:
        failure.assertHasCause("CounterService is a shared build service, which cannot be obtained with service().")
    }

    def "looking up a null service type fails with a helpful message"() {
        buildFile << """
            service(null)
        """

        when:
        fails("help")

        then:
        failure.assertHasCause("The service type given to service() must not be null.")
    }
}
