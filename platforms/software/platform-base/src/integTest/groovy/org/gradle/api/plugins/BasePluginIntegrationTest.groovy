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
            plugins {
                id("base")
            }
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
            plugins {
                id("base")
            }

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
        executer.expectDocumentedDeprecationWarning("""Configuration default already exists with permitted usage(s):
\tConsumable - this configuration can be selected by another project as a dependency
\tResolvable - this configuration can be resolved by this project to a set of files
\tDeclarable - this configuration can have dependencies added to it
Yet Gradle expected to create it with the usage(s):
\tConsumable - this configuration can be selected by another project as a dependency
Gradle will mutate the usage of configuration default to match the expected usage. This may cause unexpected behavior. Creating configurations with reserved names has been deprecated. This will fail with an error in Gradle 9.0. Do not create a configuration with the name default. For more information, please refer to https://docs.gradle.org/current/userguide/authoring_maintainable_build_scripts.html#sec:dont_anticipate_configuration_creation in the Gradle documentation.""")
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
            plugins {
                id("base")
            }

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

    def "artifacts on the 'default' configuration are built by assemble task"() {
        buildFile << """
            plugins {
                id("base")
            }

            task jar1(type: Jar) {}

            configurations {
                named("default") {
                    outgoing.artifact(tasks.jar1)
                }
            }
        """

        expect:
        succeeds("assemble")

        executedAndNotSkipped(":jar1")
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
        succeeds("assemble")

        executedAndNotSkipped(":jar1")
        notExecuted(":jar2")
    }

    def "adding candidates to the DefaultArtifactPublicationSet is deprecated"() {
        // The Kotlin plugin does this
        // See https://github.com/JetBrains/kotlin/blob/54da79fbc4034054c724b6be89cf6f4aca225fe5/libraries/tools/kotlin-gradle-plugin/src/common/kotlin/org/jetbrains/kotlin/gradle/targets/native/configureBinaryFrameworks.kt#L91-L100

        buildFile << """
            plugins {
                id("base")
            }

            ${buildFileTask}

            tasks.register("foo", BuildFileTask) {
                outputFile = project.layout.buildDirectory.file("foo.txt")
            }

            def conf = configurations.create("conf")
            conf.setVisible(false)

            def artifact = artifacts.add(conf.name, tasks.foo.outputFile.asFile)
            extensions.getByType(org.gradle.api.internal.plugins.DefaultArtifactPublicationSet).addCandidate(artifact)
        """

        when:
        executer.expectDocumentedDeprecationWarning("The DefaultArtifactPublicationSet.addCandidate(PublishArtifact) method has been deprecated. This is scheduled to be removed in Gradle 9.0. DefaultArtifactPublicationSet is deprecated and will be removed in Gradle 9.0. To ensure the 'assemble' task builds the artifact, use tasks.assemble.dependsOn(artifact). Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_8.html#deprecate_automatically_assembled_artifacts")
        succeeds("assemble")

        then:
        executedAndNotSkipped(":foo")
        file("build/foo.txt").text == "Hello, World!"
    }

    def "can build artifacts with assemble task by adding dependency manually"() {
        // The Kotlin should do this instead
        // See https://github.com/JetBrains/kotlin/blob/54da79fbc4034054c724b6be89cf6f4aca225fe5/libraries/tools/kotlin-gradle-plugin/src/common/kotlin/org/jetbrains/kotlin/gradle/targets/native/configureBinaryFrameworks.kt#L91-L100

        buildFile << """
            plugins {
                id("base")
            }

            ${buildFileTask}

            tasks.register("foo", BuildFileTask) {
                outputFile = project.layout.buildDirectory.file("foo.txt")
            }

            def conf = configurations.create("conf")
            conf.setVisible(false)

            def artifact = artifacts.add(conf.name, tasks.foo.outputFile.asFile)
            tasks.assemble.dependsOn(artifact)
        """

        when:
        succeeds("assemble")

        then:
        executedAndNotSkipped(":foo")
        file("build/foo.txt").text == "Hello, World!"
    }

    def "adding artifacts to the archives configuration is deprecated"() {
        buildFile << """
            plugins {
                id("base")
            }

            ${buildFileTask}

            tasks.register("foo", BuildFileTask) {
                outputFile = project.layout.buildDirectory.file("foo.txt")
            }

            artifacts {
                archives(tasks.foo.outputFile)
            }
        """

        when:
        executer.expectDocumentedDeprecationWarning("""Calling configuration method 'getArtifacts()' is deprecated for configuration 'archives', which has permitted usage(s):
\tConsumable - this configuration can be selected by another project as a dependency (but this behavior is marked deprecated)
This method is only meant to be called on configurations which allow the (non-deprecated) usage(s): 'Consumable'. This behavior has been deprecated. This behavior is scheduled to be removed in Gradle 9.0. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_8.html#deprecated_configuration_usage""")
        succeeds("assemble")

        then:
        executedAndNotSkipped(":foo")
        file("build/foo.txt").text == "Hello, World!"
    }

    def "consuming the archives configuration is deprecated"() {

        settingsFile << """
            include("consumer")
        """

        buildFile << """
            plugins {
                id("base")
            }

            ${getBuildFileTask()}

            tasks.register("foo", BuildFileTask) {
                outputFile = project.layout.buildDirectory.file("foo.txt")
            }

            configurations {
                archives {
                    attributes.attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category, "foo"))
                }
            }

            artifacts {
                archives(tasks.foo.outputFile)
            }
        """

        file("consumer/build.gradle") << """

            configurations {
                dependencyScope("deps")
                resolvable("res") {
                    extendsFrom(deps)
                    attributes {
                        attributes.attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category, "foo"))
                    }
                }
            }

            task resolve {
                def files = configurations.res.incoming.files
                dependsOn(files)
                doLast {
                    assert files*.name == ["foo.txt"]
                }
            }

            dependencies {
                deps(project(":"))
            }

            configurations {
                compileClasspath.extendsFrom(archives)
            }
        """

        when:
        executer.expectDocumentedDeprecationWarning("""Calling configuration method 'getArtifacts()' is deprecated for configuration 'archives', which has permitted usage(s):
\tConsumable - this configuration can be selected by another project as a dependency (but this behavior is marked deprecated)
This method is only meant to be called on configurations which allow the (non-deprecated) usage(s): 'Consumable'. This behavior has been deprecated. This behavior is scheduled to be removed in Gradle 9.0. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_8.html#deprecated_configuration_usage""")
        // Once when resolving build dependencies, once when resolving graph
        2.times { executer.expectDocumentedDeprecationWarning("The archives configuration has been deprecated for consumption. This will fail with an error in Gradle 9.0. For more information, please refer to https://docs.gradle.org/current/userguide/declaring_dependencies.html#sec:deprecated-configurations in the Gradle documentation.") }
        succeeds(":consumer:resolve")

        then:
        executedAndNotSkipped(":foo", ":consumer:resolve")
        file("build/foo.txt").text == "Hello, World!"
    }

    private static String getBuildFileTask() {
        """
            abstract class BuildFileTask extends DefaultTask {

                @OutputFile
                abstract RegularFileProperty getOutputFile()

                @TaskAction
                void execute() {
                    getOutputFile().get().asFile.text = "Hello, World!"
                }

            }
        """
    }
}
