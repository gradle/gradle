package org.gradle.kotlin.dsl

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskContainer

import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verify

import org.junit.Test


class TaskContainerExtensionsTest {

    @Test
    fun `can create tasks with injected constructor arguments`() {

        val tasks = mock<TaskContainer>()

        tasks.create<DefaultTask>("my", "foo", "bar")

        verify(tasks).create("my", DefaultTask::class.java, "foo", "bar")
    }
}
