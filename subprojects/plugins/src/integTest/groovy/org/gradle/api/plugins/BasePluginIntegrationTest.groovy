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

    def "can define 'default' and 'archives' configurations prior to applying plugin"() {
        buildFile << """
            configurations {
                create("default")
                archives
            }
            apply plugin: 'base'
        """

        expect:
        executer.expectDocumentedDeprecationWarning("The configuration default was created explicitly. This configuration name is reserved for creation by Gradle. This behavior has been deprecated. This behavior is scheduled to be removed in Gradle 9.0. Do not create a configuration with the name default. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_8.html#configurations_allowed_usage")
        executer.expectDocumentedDeprecationWarning("Gradle will mutate the usage of configuration default to match the expected usage. This may cause unexpected behavior. Creating configurations with reserved names has been deprecated. This will fail with an error in Gradle 9.0. Do not create a configuration with the name default. For more information, please refer to https://docs.gradle.org/current/userguide/authoring_maintainable_build_scripts.html#sec:dont_anticipate_configuration_creation in the Gradle documentation.")
        executer.expectDocumentedDeprecationWarning("The configuration archives was created explicitly. This configuration name is reserved for creation by Gradle. This behavior has been deprecated. This behavior is scheduled to be removed in Gradle 9.0. Do not create a configuration with the name archives. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_8.html#configurations_allowed_usage")
        executer.expectDocumentedDeprecationWarning("""Configuration archives already exists with permitted usage(s):
\tConsumable - this configuration can be selected by another project as a dependency
\tResolvable - this configuration can be resolved by this project to a set of files
\tDeclarable - this configuration can have dependencies added to it
Yet Gradle expected to create it with the usage(s):
\tConsumable - this configuration can be selected by another project as a dependency (but this behavior is marked deprecated)
Gradle will mutate the usage of configuration archives to match the expected usage. This may cause unexpected behavior. Creating configurations with reserved names has been deprecated. This will fail with an error in Gradle 9.0. Do not create a configuration with the name archives. For more information, please refer to https://docs.gradle.org/current/userguide/authoring_maintainable_build_scripts.html#sec:dont_anticipate_configuration_creation in the Gradle documentation.""")
        succeeds "help"
    }

    def "can override archiveBaseName in custom Jar task"() {
        buildFile """
            apply plugin: 'base'
            class MyJar extends Jar {
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
        executer.expectDocumentedDeprecationWarning("task ':jar1' for configuration 'default' is automatically built by the 'assemble' task. Building configuration artifacts automatically in this manner has been deprecated. Starting with Gradle 9.0, the 'assemble' task will no longer build this artifact automatically. Set the gradle property 'org.gradle.preview.explicit-assemble=true' to opt into the new behavior and silence this warning. To continue building this artifact when running 'assemble', manually define the task dependency with 'tasks.assemble.dependsOn(Object)' Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_8.html#deprecated_archives_configuration")
        executer.expectDocumentedDeprecationWarning("task ':jar2' is automatically built by the 'assemble' task since it was added to the 'archives' configuration. Building 'archives' configuration artifacts automatically in this manner has been deprecated. Starting with Gradle 9.0, the 'assemble' task will no longer build this artifact automatically. Set the gradle property 'org.gradle.preview.explicit-assemble=true' to opt into the new behavior and silence this warning. To continue building this artifact when running 'assemble', manually define the task dependency with 'tasks.assemble.dependsOn(Object)' Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_8.html#deprecated_archives_configuration")
        succeeds("assemble")

        executedAndNotSkipped(":jar1", ":jar2")
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

    def "artifacts on legacy configurations are built by default if visible"() {
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
        executer.expectDocumentedDeprecationWarning("task ':jar1' for configuration 'foo' is automatically built by the 'assemble' task. Building configuration artifacts automatically in this manner has been deprecated. Starting with Gradle 9.0, the 'assemble' task will no longer build this artifact automatically. Set the gradle property 'org.gradle.preview.explicit-assemble=true' to opt into the new behavior and silence this warning. To continue building this artifact when running 'assemble', manually define the task dependency with 'tasks.assemble.dependsOn(Object)' Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_8.html#deprecated_archives_configuration")
        succeeds("assemble")

        executedAndNotSkipped(":jar1")
        notExecuted(":jar2")
    }

    // This is very confusing behavior and not necessarily desired.
    def "builds the first visible configuration that registers a jar artifact"() {
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
                    visible = true
                    outgoing.artifact(tasks.jar2)
                }
            }
        """

        expect:
        executer.expectDocumentedDeprecationWarning("task ':jar1' for configuration 'foo' is automatically built by the 'assemble' task. Building configuration artifacts automatically in this manner has been deprecated. Starting with Gradle 9.0, the 'assemble' task will no longer build this artifact automatically. Set the gradle property 'org.gradle.preview.explicit-assemble=true' to opt into the new behavior and silence this warning. To continue building this artifact when running 'assemble', manually define the task dependency with 'tasks.assemble.dependsOn(Object)' Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_8.html#deprecated_archives_configuration")
        succeeds("assemble")

        executedAndNotSkipped(":jar1")
        notExecuted(":jar2")
    }
}
