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

@Issue(["https://github.com/gradle/gradle/issues/13121", "https://github.com/gradle/gradle/issues/39131"])
class ScriptServiceLookupIntegrationTest extends AbstractIntegrationSpec {

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

    def "a task resolves the services of the project that owns it"() {
        createDirs("a")
        settingsFile """
            include("a")
        """
        def pingTask = """
            tasks.register("ping") {
                def projectPath = project.path
                doLast {
                    println("layout for " + projectPath + ": " + service(ProjectLayout).projectDirectory.asFile.name)
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

    private static String getJvmPath() {
        return TextUtil.escapeString(Jvm.current().javaExecutable.absolutePath)
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
            "\nIt is available in settings scripts.")
        outputDoesNotContain("REACHED ACTION")
    }
}
