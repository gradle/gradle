/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.kotlin.dsl

import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.Copy

import org.gradle.kotlin.dsl.fixtures.TestWithTempFiles
import org.gradle.kotlin.dsl.fixtures.eval
import org.gradle.kotlin.dsl.fixtures.newProjectBuilderProject

import org.junit.Test

import kotlin.reflect.KClass


class PolymorphicContainersEvalTest : TestWithTempFiles() {

    companion object {

        val preExistingTasks = listOf(
            "foo" to DefaultTask::class,
            "bar" to Copy::class,
            "bat" to Copy::class,
            "pipistrelle" to Copy::class
        )

        val tasksConfigurationAssertions = listOf(
            TaskAssertion("foo", Task::class) {

            }
        )

        class TaskAssertion<T : Task>(
            val name: String,
            val type: KClass<T>,
            val assertion: T.() -> Unit
        )
    }

    private
    fun assertTasksConfiguration(name: String, script: String, configuration: Project.() -> Unit = {}) {
        newFolder(name).newProjectBuilderProject().run {

            preExistingTasks.forEach { (name, type) ->
                tasks.register(name, type.java).configure {
                    it.description = ""
                }
            }

            configuration()

            eval(script)

            // TODO assert
        }
    }

    @Test
    fun `polymorphic named domain object container api`() {

        assertTasksConfiguration("api", """

            tasks.getByName("foo")
            tasks.getByName("foo", Task::class)
            tasks.getByName<Task>("foo")

            tasks.getByName("bar") {
                description += "!"
            }
            tasks.getByName("bar", Copy::class) {
                description += "!"
            }
            tasks.getByName<Copy>("bar") {
                description += "!"
            }

            tasks.create("bazar")
            // TODO ::class taking overload generated is absent in this context
            tasks.create("cathedral", Copy::class.java)
            tasks.create<Copy>("cabin")

            tasks.create("castle") {
                description += "!"
            }
            // TODO ::class taking overload generated is absent in this context
            tasks.create("valley", Copy::class.java) {
                description += "!"
            }
            tasks.create<Copy>("hill") {
                description += "!"
            }

            tasks.named("bat")
            tasks.named("bat", Copy::class)
            tasks.named<Copy>("bat")

            tasks.named("pipistrelle") {
                description += "!"
            }
            tasks.named("pipistrelle", Copy::class) {
                description += "!"
            }
            tasks.named<Copy>("pipistrelle") {
                description += "!"
            }

            tasks.register("yate")
            // TODO ::class taking overload generated is absent in this context
            tasks.register("quartern", Copy::class.java)
            tasks.register<Copy>("veduta")

            tasks.register("vansire") {
                description += "!"
            }
            // TODO ::class taking overload generated is absent in this context
            tasks.register("koto", Copy::class.java) {
                description += "!"
            }
            tasks.register<Copy>("diptote") {
                description += "!"
            }

        """) {
        }
    }

    @Test
    fun `polymorphic named domain object container scope api`() {

        assertTasksConfiguration("scope-api", """
            tasks {
                getByName("foo")
                getByName("foo", Task::class)
                getByName<Task>("foo")
    
                getByName("bar") {
                    description += "!"
                }
                getByName("bar", Copy::class) {
                    description += "!"
                }
                getByName<Copy>("bar") {
                    description += "!"
                }
    
                create("bazar")
                /* TODO UNSUPPORTED
                create("cathedral", Copy::class.java)
                */
                create<Copy>("cabin")

                create("castle") {
                    description += "!"
                }
                /* TODO UNSUPPORTED
                create("valley", Copy::class) {
                    description += "!"
                }
                */
                create<Copy>("hill") {
                    description += "!"
                }
    
                named("bat")
                named("bat", Copy::class)
                named<Copy>("bat")
    
                named("pipistrelle") {
                    description += "!"
                }
                named("pipistrelle", Copy::class) {
                    description += "!"
                }
                named<Copy>("pipistrelle") {
                    description += "!"
                }
    
                register("yate")
                /* TODO UNSUPPORTED
                register("quartern", Copy::class)
                */
                register<Copy>("veduta")
    
                register("vansire") {
                    description += "!"
                }
                /* TODO UNSUPPORTED
                register("koto", Copy::class) {
                    description += "!"
                }
                */
                register<Copy>("diptote") {
                    description += "!"
                }
            }

        """) {
        }
    }

    @Test
    fun `polymorphic named domain object container delegated properties`() {

        assertTasksConfiguration("delegated-properties", """

            fun untyped() {

                val foo by tasks.getting
                val bar by tasks.getting {
                    description += "!"
                }

                val bazar by tasks.creating
                val castle by tasks.creating {
                    description += "!"
                }

                val bat by tasks.existing
                val pipistrelle by tasks.existing {
                    description += "!"
                }

                val yate by tasks.registering
                val vansire by tasks.registering {
                    description += "!"
                }
            }

            fun typed() {

                val foo by tasks.getting(Task::class)
                val bar by tasks.getting(Copy::class) {
                    description += "!"
                }

                val cathedral by tasks.creating(Copy::class)
                val hill by tasks.creating(Copy::class) {
                    description += "!"
                }

                val bat by tasks.existing(Copy::class)
                val pipistrelle by tasks.existing(Copy::class) {
                    description += "!"
                }

                val veduta by tasks.registering(Copy::class)
                val diptote by tasks.registering(Copy::class) {
                    description += "!"
                }
            }

            untyped()
            typed()

        """) {
        }
    }

    @Test
    fun `polymorphic named domain object container scope delegated properties`() {

        assertTasksConfiguration("scope-delegated-properties", """

            fun untyped() {

                tasks {
    
                    val foo by getting
                    val bar by getting {
                        description += "!"
                    }
    
                    val bazar by creating
                    val castle by creating {
                        description += "!"
                    }
    
                    val bat by existing
                    val pipistrelle by existing {
                        description += "!"
                    }
    
                    val yate by registering
                    val vansire by registering {
                        description += "!"
                    }
                }
            }

            fun typed() {
                tasks {
    
                    val foo by getting(Task::class)
                    val bar by getting(Copy::class) {
                        description += "!"
                    }
    
                    val cathedral by creating(Copy::class)
                    val hill by creating(Copy::class) {
                        description += "!"
                    }
    
                    val bat by existing(Copy::class)
                    val pipistrelle by existing(Copy::class) {
                        description += "!"
                    }
    
                    val veduta by registering(Copy::class)
                    val diptote by registering(Copy::class) {
                        description += "!"
                    }
                }
            }

            untyped()
            typed()

        """) {
        }
    }

    @Test
    fun `polymorphic named domain object container scope string invoke`() {

        assertTasksConfiguration("scope-string invoke", """

            tasks {

                val foo  = "foo"()
                "bar" {
                    description += "!"
                }

                val bat = "bat"(Task::class)
                "pipistrelle"(Copy::class) {
                    description += "!"
                }
            }

        """) {
        }
    }
}
