package org.gradle.kotlin.dsl

import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verify

import org.gradle.api.tasks.TaskContainer
import org.gradle.api.tasks.Upload

import org.junit.Test


class TaskContainerSpec {

    @Test
    fun `can configure by name and type using getByName`() {

        val task = mock<Upload>()
        val tasks = mock<TaskContainer>() {
            on { getByName("uploadArchives") } doReturn task
        }

        tasks.getByName<Upload>("uploadArchives") {
            isUploadDescriptor = true
        }

        verify(task).isUploadDescriptor = true
    }
}
