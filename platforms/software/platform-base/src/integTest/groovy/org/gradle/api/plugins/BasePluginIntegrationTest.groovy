/*
 * Copyright 2012 the original author or authors.
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
package org.gradle.api.plugins

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions

import static org.hamcrest.CoreMatchers.containsString

class BasePluginIntegrationTest extends AbstractIntegrationSpec {

    @Requires(UnitTestPreconditions.MandatoryFileLockOnOpen)
    def "clean failure message indicates file"() {
        given:
        buildFile << """
            apply plugin: 'base'
        """

        and:
        def channel = new RandomAccessFile(file("build/newFile").createFile(), "rw").channel
        def lock = channel.lock()

        when:
        fails "clean"

        then:
        failure.assertThatCause(containsString("Unable to delete directory '${file('build')}'"))
        failure.assertThatCause(containsString("Failed to delete some children. This might happen because a process has files open or has its working directory set in the target directory."))
        failure.assertThatCause(containsString(file("build/newFile").absolutePath))

        cleanup:
        lock?.release()
        channel?.close()
    }

    def "cannot define 'build' and 'check' tasks when applying plugin"() {
        buildFile << """
            apply plugin: 'base'

            task $taskName {
                doLast {
                    println "CUSTOM"
                }
            }
"""
        when:
        fails "build"

        then:
        failure.assertHasCause "Cannot add task '$taskName' as a task with that name already exists."
        where:
        taskName << ['build', 'check']
    }

    def "can not define configuration #conf using #creationCall prior to applying plugin"() {
        buildFile << """
            configurations {
                $creationCall
            }
            apply plugin: 'base'
        """

        when:
        fails "help"

        then:
        failure.assertHasDescription("A problem occurred evaluating root project '${buildFile.parentFile.name}'.")
        failureHasCause("""Cannot add a configuration with name '$conf' as a configuration with that name already exists.""")

        where:
        conf        | creationCall
        "default"   | "create('default')"
        "archives"  | "archives"
        "archives"  | "create('archives')"
    }

    def "can override archiveBaseName in custom Jar task"() {
        buildFile """
            apply plugin: 'base'
            abstract class MyJar extends Jar {
                MyJar() {
                    super()
                    archiveBaseName.set("myjar")
                }
            }
            task myJar(type: MyJar) {
                doLast { task ->
                    assert task.archiveBaseName.get() == "myjar"
                }
            }
        """
        expect:
        succeeds("myJar")
    }

    def "artifacts on archives and base configurations are built by assemble task"() {
        buildFile << """
            plugins {
                id("base")
            }

            task jar1(type: Jar) {}
            task jar2(type: Jar) {}

            configurations {
                named("default") {
                    outgoing.artifact(tasks.jar1)
                }
                archives {
                    outgoing.artifact(tasks.jar2)
                }
            }
        """

        expect:
        succeeds("assemble")

        executedAndNotSkipped(":jar2")
    }

    def "artifacts on role-locked configurations are not built by the assemble task by default"() {
        buildFile << """
            plugins {
                id("base")
            }

            task jar1(type: Jar) {}
            task jar2(type: Jar) {}
            task jar3(type: Jar) {}

            configurations {
                consumable("con") {
                    outgoing.artifact(tasks.jar1)
                }
                resolvable("res") {
                    outgoing.artifact(tasks.jar2)
                }
                dependencyScope("dep") {
                    outgoing.artifact(tasks.jar3)
                }
            }
        """

        expect:
        succeeds("assemble")

        notExecuted(":jar1", ":jar2", ":jar3")
    }

    def "artifacts on legacy configurations are not built by default even if visible is set"() {
        buildFile << """
            plugins {
                id("base")
            }

            task jar1(type: Jar) {}
            task jar2(type: Jar) {}

            configurations {
                foo {
                    visible = true
                    outgoing.artifact(tasks.jar1)
                }
                bar {
                    visible = false
                    outgoing.artifact(tasks.jar2)
                }
            }
        """

        expect:
        succeeds("assemble")

        and:
        notExecuted(":jar1", ":jar2")
    }
}
