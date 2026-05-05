package org.gradle.kotlin.dsl

import org.gradle.api.Action
import org.gradle.api.DefaultTask
import org.gradle.api.Task
import org.gradle.api.tasks.TaskContainer
import org.gradle.api.tasks.TaskProvider
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify


class TaskContainerExtensionsTest {

    @Suppress("DEPRECATION")
    @Test
    fun `can create tasks with injected constructor arguments`() {

        val task = mock<DefaultTask>()
        val tasks = mock<TaskContainer> {
            on { create("my", DefaultTask::class.java, "foo", "bar") } doReturn task
        }

        tasks.create<DefaultTask>("my", "foo", "bar")

        verify(tasks).create("my", DefaultTask::class.java, "foo", "bar")
    }

    @Test
    fun `can register tasks with injected constructor arguments`() {

        val taskProvider = mock<TaskProvider<Task>>()
        val tasks = mock<TaskContainer> {
            on { register("my", Task::class.java, "foo", "bar") } doReturn taskProvider
        }

        tasks.register<Task>("my", "foo", "bar")

        verify(tasks).register("my", Task::class.java, "foo", "bar")
    }
}


internal
fun <T : Task> mockTaskProviderFor(task: T): TaskProvider<T> = mock {
    on { get() } doReturn task
    on { configure(any()) } doAnswer {
        it.getArgument<Action<T>>(0).execute(task)
    }
}
