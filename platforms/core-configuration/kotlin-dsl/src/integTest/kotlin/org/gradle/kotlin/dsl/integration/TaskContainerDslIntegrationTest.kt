/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.kotlin.dsl.integration

import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.Copy
import org.gradle.internal.SystemProperties
import org.gradle.kotlin.dsl.*
import org.gradle.kotlin.dsl.fixtures.AbstractKotlinIntegrationTest
import org.gradle.kotlin.dsl.fixtures.DslTestFixture
import org.gradle.kotlin.dsl.fixtures.runWithProjectBuilderProject
import org.gradle.kotlin.dsl.fixtures.testInstallationGradleApiExtensionsClasspathFor
import org.gradle.kotlin.dsl.fixtures.testRuntimeClassPath
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.nullValue
import org.hamcrest.Matcher
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.reflect.KClass


class TaskContainerDslIntegrationTest : AbstractKotlinIntegrationTest() {

    private
    val dslTestFixture: DslTestFixture by lazy {
        DslTestFixture(projectRoot)
    }

    @Test
    @Requires(IntegTestPreconditions.NotEmbeddedExecutor::class)
    fun `polymorphic named domain object container api`() {

        testTaskContainerVia(
            "api",
            script = """

            val t1: Task = tasks.getByName("foo")
            val t2: Task = tasks.getByName("foo", Task::class)
            val t3: Task = tasks.getByName<Task>("foo")

            val t4: Task = tasks.getByName("bar") {
                description += "A"
            }
            val t5: Copy = tasks.getByName("bar", Copy::class) {
                description += "B"
                destinationDir = file("out")
            }
            val t6: Copy = tasks.getByName<Copy>("bar") {
                description += "C"
                destinationDir = file("out")
            }

            val t6: Task = tasks.create("bazar")
            val t7: Copy = tasks.create("cathedral", Copy::class)
            val t8: Copy = tasks.create<Copy>("cabin")

            val t9: Task = tasks.create("castle") {
                description += "!"
            }
            val t10: Copy = tasks.create("valley", Copy::class) {
                description += "!"
                destinationDir = file("out")
            }
            val t11: Copy = tasks.create<Copy>("hill") {
                description += "!"
                destinationDir = file("out")
            }

            val t12: TaskProvider<Task> = tasks.named("bat")
            val t13: TaskProvider<Copy> = tasks.named("bat", Copy::class)
            val t14: TaskProvider<Copy> = tasks.named<Copy>("bat")

            val t15: TaskProvider<Task> = tasks.named("pipistrelle") {
                description += "A"
            }
            val t16: TaskProvider<Copy> = tasks.named("pipistrelle", Copy::class) {
                description += "B"
                destinationDir = file("out")
            }
            val t17: TaskProvider<Copy> = tasks.named<Copy>("pipistrelle") {
                description += "C"
                destinationDir = file("out")
            }

            val t18: TaskProvider<Task> = tasks.register("yate")
            val t19: TaskProvider<Copy> = tasks.register("quartern", Copy::class)
            val t20: TaskProvider<Copy> = tasks.register<Copy>("veduta")

            val t21: TaskProvider<Task> = tasks.register("vansire") {
                description += "!"
            }
            val t22: TaskProvider<Copy> = tasks.register("koto", Copy::class) {
                description += "!"
                destinationDir = file("out")
            }
            val t23: TaskProvider<Copy> = tasks.register<Copy>("diptote") {
                description += "!"
                destinationDir = file("out")
            }
            """
        )
    }

    @Test
    @Requires(IntegTestPreconditions.NotEmbeddedExecutor::class)
    fun `polymorphic named domain object container scope api`() {

        testTaskContainerVia(
            "scope-api",
            script = """
            tasks {
                val t1: Task = getByName("foo")
                val t2: Task = getByName("foo", Task::class)
                val t3: Task = getByName<Task>("foo")

                val t4: Task = getByName("bar") {
                    description += "A"
                }
                val t5: Copy = getByName("bar", Copy::class) {
                    description += "B"
                    destinationDir = file("out")
                }
                val t6: Copy = getByName<Copy>("bar") {
                    description += "C"
                    destinationDir = file("out")
                }

                val t7: Task = create("bazar")
                val t8: Copy = create("cathedral", Copy::class)
                val t9: Copy = create<Copy>("cabin")

                val t10: Task = create("castle") {
                    description += "!"
                }
                val t11: Copy = tasks.create("valley", Copy::class) {
                    description += "!"
                    destinationDir = file("out")
                }
                val t12: Copy = create<Copy>("hill") {
                    description += "!"
                    destinationDir = file("out")
                }

                val t13: TaskProvider<Task> = named("bat")
                val t14: TaskProvider<Copy> = named("bat", Copy::class)
                val t15: TaskProvider<Copy> = named<Copy>("bat")

                val t16: TaskProvider<Task> = named("pipistrelle") {
                    description += "A"
                }
                val t17: TaskProvider<Copy> = named("pipistrelle", Copy::class) {
                    description += "B"
                    destinationDir = file("out")
                }
                val t18: TaskProvider<Copy> = named<Copy>("pipistrelle") {
                    description += "C"
                    destinationDir = file("out")
                }

                val t19: TaskProvider<Task> = register("yate")
                val t20: TaskProvider<Copy> = register("quartern", Copy::class)
                val t21: TaskProvider<Copy> = register<Copy>("veduta")

                val t22: TaskProvider<Task> = register("vansire") {
                    description += "!"
                }
                val t23: TaskProvider<Copy> = register("koto", Copy::class) {
                    description += "!"
                    destinationDir = file("out")
                }
                val t24: TaskProvider<Copy> = register<Copy>("diptote") {
                    description += "!"
                    destinationDir = file("out")
                }
            }
            """
        )
    }

    @Test
    @Requires(IntegTestPreconditions.NotEmbeddedExecutor::class)
    fun `polymorphic named domain object container delegated properties`() {

        testTaskContainerVia(
            "delegated-properties", before = beforeDelegatedProperties,
            script = """

            fun untyped() {

                val foo: Task by tasks.getting
                val bar: Task by tasks.getting {
                    description += "B"
                }

                val bazar: Task by tasks.creating
                val castle: Task by tasks.creating {
                    description += "!"
                }

                val bat: TaskProvider<Task> by tasks.existing
                val pipistrelle: TaskProvider<Task> by tasks.existing {
                    description += "B"
                }

                val yate: TaskProvider<Task> by tasks.registering
                val vansire: TaskProvider<Task> by tasks.registering {
                    description += "!"
                }
            }

            fun typed() {

                val foo: Task by tasks.getting(Task::class)
                val bar: Copy by tasks.getting(Copy::class) {
                    description += "C"
                    destinationDir = file("out")
                }

                val cathedral: Copy by tasks.creating(Copy::class)
                val hill: Copy by tasks.creating(Copy::class) {
                    description += "!"
                    destinationDir = file("out")
                }

                val bat: TaskProvider<Copy> by tasks.existing(Copy::class)
                val pipistrelle: TaskProvider<Copy> by tasks.existing(Copy::class) {
                    description += "C"
                    destinationDir = file("out")
                }

                val veduta: TaskProvider<Copy> by tasks.registering(Copy::class)
                val diptote: TaskProvider<Copy> by tasks.registering(Copy::class) {
                    description += "!"
                    destinationDir = file("out")
                }
            }

            untyped()
            typed()
            """
        )
    }

    @Test
    @Requires(IntegTestPreconditions.NotEmbeddedExecutor::class)
    fun `polymorphic named domain object container scope delegated properties`() {

        testTaskContainerVia(
            "scope-delegated-properties", before = beforeDelegatedProperties,
            script = """

            fun untyped() {

                tasks {

                    val foo: Task by getting
                    val bar: Task by getting {
                        description += "B"
                    }

                    val bazar: Task by creating
                    val castle: Task by creating {
                        description += "!"
                    }

                    val bat: TaskProvider<Task> by existing
                    val pipistrelle: TaskProvider<Task> by existing {
                        description += "B"
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
                        description += "C"
                        destinationDir = file("out")
                    }

                    val cathedral: Copy by creating(Copy::class)
                    val hill: Copy by creating(Copy::class) {
                        description += "!"
                        destinationDir = file("out")
                    }

                    val bat: TaskProvider<Copy> by existing(Copy::class)
                    val pipistrelle: TaskProvider<Copy> by existing(Copy::class) {
                        description += "C"
                        destinationDir = file("out")
                    }

                    val veduta: TaskProvider<Copy> by registering(Copy::class)
                    val diptote: TaskProvider<Copy> by registering(Copy::class) {
                        description += "!"
                        destinationDir = file("out")
                    }
                }
            }

            untyped()
            typed()
            """
        )
    }

    private
    val beforeDelegatedProperties: Project.() -> Unit = {
        // For cases not exercised by delegated properties
        tasks["bar"].description += "A"
        tasks.create<Copy>("cabin")
        tasks.create<Copy>("valley").description += "!"
        tasks["pipistrelle"].description += "A"
        tasks.create<Copy>("quartern")
        tasks.create<Copy>("koto").description += "!"
    }

    @Test
    @Requires(IntegTestPreconditions.NotEmbeddedExecutor::class)
    fun `polymorphic named domain object container scope string invoke`() {

        testTaskContainerVia(
            "scope-string invoke",
            script = """

            tasks {

                val foo: TaskProvider<Task>  = "foo"()
                val bar: TaskProvider<Task> = "bar" {
                    description += "!"
                }

                val bat: TaskProvider<Task> = "bat"(Task::class)
                val pipistrelle: TaskProvider<Copy> = "pipistrelle"(Copy::class) {
                    description += "!"
                    destinationDir = file("out")
                }
            }
        """,
            tasksAssertions = listOf(
                taskAssertion("foo"),
                taskAssertion("bar", Task::class, equalTo("null!")),
                taskAssertion("bat"),
                taskAssertion("pipistrelle", Copy::class, equalTo("null!"))
            )
        )
    }

    private
    fun testTaskContainerVia(
        name: String,
        before: Project.() -> Unit = {},
        script: String,
        tasksAssertions: List<TaskAssertion> = tasksConfigurationAssertions
    ) {
        val projectDir = newDir(name)
        @Suppress("DEPRECATION") val tmpDir = File(SystemProperties.getInstance().javaIoTmpDir, "test-" + name + "-tmp")
        runWithProjectBuilderProject(projectDir) {
            preRegisteredTasks.forEach {
                tasks.register(it.name, it.type.java)
            }

            before()

            dslTestFixture.evalScript(
                script,
                target = this,
                scriptCompilationClassPath = testRuntimeClassPath + testInstallationGradleApiExtensionsClasspathFor(distribution.gradleHomeDir, tmpDir)
            )

            tasksAssertions.forEach { taskAssertion ->
                taskAssertion.run {
                    assertThat(
                        "${task.name}: ${task.type.simpleName}",
                        tasks.getByName(task.name, task.type).description,
                        descriptionMatcher
                    )
                }
            }
        }
    }

    companion object {

        class TaskNameAndType(
            val name: String,
            val type: KClass<out Task> = Task::class
        )

        class TaskAssertion(
            val task: TaskNameAndType,
            val descriptionMatcher: Matcher<in String?>
        )

        fun taskAssertion(
            name: String,
            type: KClass<out Task> = Task::class,
            descriptionMatcher: Matcher<in String?> = nullValue()
        ) =
            TaskAssertion(TaskNameAndType(name, type), descriptionMatcher)

        val preRegisteredTasks = listOf(
            TaskNameAndType("foo"),
            TaskNameAndType("bar", Copy::class),
            TaskNameAndType("bat", Copy::class),
            TaskNameAndType("pipistrelle", Copy::class)
        )

        val tasksConfigurationAssertions = listOf(
            taskAssertion("foo"),
            taskAssertion("bar", Copy::class, equalTo("nullABC")),
            taskAssertion("bazar"),
            taskAssertion("cathedral", Copy::class),
            taskAssertion("cabin", Copy::class),
            taskAssertion("castle", Task::class, equalTo("null!")),
            taskAssertion("valley", Copy::class, equalTo("null!")),
            taskAssertion("hill", Copy::class, equalTo("null!")),
            taskAssertion("bat"),
            taskAssertion("pipistrelle", Copy::class, equalTo("nullABC")),
            taskAssertion("yate"),
            taskAssertion("quartern", Copy::class),
            taskAssertion("veduta", Copy::class),
            taskAssertion("vansire", Task::class, equalTo("null!")),
            taskAssertion("koto", Copy::class, equalTo("null!")),
            taskAssertion("diptote", Copy::class, equalTo("null!"))
        )
    }
}
