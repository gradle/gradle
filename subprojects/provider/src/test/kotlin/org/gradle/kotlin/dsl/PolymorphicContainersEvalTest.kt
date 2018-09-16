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

            val t1: Task = tasks.getByName("foo")
            val t2: Task = tasks.getByName("foo", Task::class)
            val t3: Task = tasks.getByName<Task>("foo")

            val t4: Task = tasks.getByName("bar") {
                description += "!"
            }
            val t5: Copy = tasks.getByName("bar", Copy::class) {
                description += "!"
            }
            val t6: Copy = tasks.getByName<Copy>("bar") {
                description += "!"
            }

            val t6: Task = tasks.create("bazar")
            // TODO ::class taking overload generated is absent in this context
            val t7: Copy = tasks.create("cathedral", Copy::class.java)
            val t8: Copy = tasks.create<Copy>("cabin")

            val t9: Task = tasks.create("castle") {
                description += "!"
            }
            // TODO ::class taking overload generated is absent in this context
            val t10: Copy = tasks.create("valley", Copy::class.java) {
                description += "!"
            }
            val t11: Copy = tasks.create<Copy>("hill") {
                description += "!"
            }

            val t12: TaskProvider<Task> = tasks.named("bat")
            val t13: TaskProvider<Copy> = tasks.named("bat", Copy::class)
            val t14: TaskProvider<Copy> = tasks.named<Copy>("bat")

            val t15: TaskProvider<Task> = tasks.named("pipistrelle") {
                description += "!"
            }
            val t16: TaskProvider<Copy> = tasks.named("pipistrelle", Copy::class) {
                description += "!"
            }
            // TODO wrong return type
            val t17: NamedDomainObjectProvider<Copy> = tasks.named<Copy>("pipistrelle") {
                description += "!"
            }

            val t18: TaskProvider<Task> = tasks.register("yate")
            // TODO ::class taking overload generated is absent in this context
            val t19: TaskProvider<Copy> = tasks.register("quartern", Copy::class.java)
            // TODO wrong return type
            val t20: NamedDomainObjectProvider<Copy> = tasks.register<Copy>("veduta")

            val t21: TaskProvider<Task> = tasks.register("vansire") {
                description += "!"
            }
            // TODO ::class taking overload generated is absent in this context
            val t22: TaskProvider<Copy> = tasks.register("koto", Copy::class.java) {
                description += "!"
            }
            // TODO wrong return type
            val t23: NamedDomainObjectProvider<Copy> = tasks.register<Copy>("diptote") {
                description += "!"
            }

        """) {
        }
    }

    @Test
    fun `polymorphic named domain object container scope api`() {

        assertTasksConfiguration("scope-api", """
            tasks {
                val t1: Task = getByName("foo")
                val t2: Task = getByName("foo", Task::class)
                val t3: Task = getByName<Task>("foo")
    
                val t4: Task = getByName("bar") {
                    description += "!"
                }
                val t5: Copy = getByName("bar", Copy::class) {
                    description += "!"
                }
                val t6: Copy = getByName<Copy>("bar") {
                    description += "!"
                }
    
                val t7: Task = create("bazar")
                // TODO ::class taking overload generated is absent in this context
                val t8: Copy = create("cathedral", Copy::class.java)
                val t9: Copy = create<Copy>("cabin")

                val t10: Task = create("castle") {
                    description += "!"
                }
                // TODO ::class taking overload generated is absent in this context
                val t11: Copy = tasks.create("valley", Copy::class.java) {
                    description += "!"
                }
                val t12: Copy = create<Copy>("hill") {
                    description += "!"
                }
    
                val t13: TaskProvider<Task> = named("bat")
                val t14: TaskProvider<Copy> = named("bat", Copy::class)
                val t15: TaskProvider<Copy> = named<Copy>("bat")
    
                val t16: TaskProvider<Task> = named("pipistrelle") {
                    description += "!"
                }
                val t17: TaskProvider<Copy> = named("pipistrelle", Copy::class) {
                    description += "!"
                }
                // TODO wrong return type
                val t18: NamedDomainObjectProvider<Copy> = named<Copy>("pipistrelle") {
                    description += "!"
                }
    
                val t19: TaskProvider<Task> = register("yate")
                // TODO ::class taking overload generated is absent in this context
                val t20: TaskProvider<Copy> = register("quartern", Copy::class.java)
                // TODO wrong return type
                val t21: NamedDomainObjectProvider<Copy> = register<Copy>("veduta")
    
                val t22: TaskProvider<Task> = register("vansire") {
                    description += "!"
                }
                // TODO ::class taking overload generated is absent in this context
                val t23: TaskProvider<Copy> = register("koto", Copy::class.java) {
                    description += "!"
                }
                // TODO wrong return type
                val t24: NamedDomainObjectProvider<Copy> = register<Copy>("diptote") {
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

                val foo: Task by tasks.getting
                val bar: Task by tasks.getting {
                    description += "!"
                }

                val bazar: Task by tasks.creating
                val castle: Task by tasks.creating {
                    description += "!"
                }

                val bat: TaskProvider<Task> by tasks.existing
                val pipistrelle: TaskProvider<Task> by tasks.existing {
                    description += "!"
                }

                val yate: TaskProvider<Task> by tasks.registering
                val vansire: TaskProvider<Task> by tasks.registering {
                    description += "!"
                }
            }

            fun typed() {

                val foo: Task by tasks.getting(Task::class)
                val bar: Copy by tasks.getting(Copy::class) {
                    description += "!"
                }

                val cathedral: Copy by tasks.creating(Copy::class)
                val hill: Copy by tasks.creating(Copy::class) {
                    description += "!"
                }

                val bat: TaskProvider<Copy> by tasks.existing(Copy::class)
                // TODO wrong return type
                val pipistrelle: NamedDomainObjectProvider<Copy> by tasks.existing(Copy::class) {
                    description += "!"
                }

                val veduta: TaskProvider<Copy> by tasks.registering(Copy::class)
                val diptote: TaskProvider<Copy> by tasks.registering(Copy::class) {
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
    
                    val foo: Task by getting
                    val bar: Task by getting {
                        description += "!"
                    }
    
                    val bazar: Task by creating
                    val castle: Task by creating {
                        description += "!"
                    }
    
                    val bat: TaskProvider<Task> by existing
                    val pipistrelle: TaskProvider<Task> by existing {
                        description += "!"
                    }
    
                    val yate: TaskProvider<Task> by registering
                    val vansire: TaskProvider<Task> by registering {
                        description += "!"
                    }
                }
            }

            fun typed() {
                tasks {
    
                    val foo: Task by getting(Task::class)
                    val bar: Copy by getting(Copy::class) {
                        description += "!"
                    }
    
                    val cathedral: Copy by creating(Copy::class)
                    val hill: Copy by creating(Copy::class) {
                        description += "!"
                    }
    
                    val bat: TaskProvider<Copy> by existing(Copy::class)
                    // TODO wrong return type
                    val pipistrelle: NamedDomainObjectProvider<Copy> by existing(Copy::class) {
                        description += "!"
                    }
    
                    val veduta: TaskProvider<Copy> by registering(Copy::class)
                    val diptote: TaskProvider<Copy> by registering(Copy::class) {
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

                val foo: TaskProvider<Task>  = "foo"()
                val bar: TaskProvider<Task> = "bar" {
                    description += "!"
                }

                val bat: TaskProvider<Task> = "bat"(Task::class)
                val pipistrelle: TaskProvider<Copy> = "pipistrelle"(Copy::class) {
                    description += "!"
                }
            }

        """) {
        }
    }
}
