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

package org.gradle.plugins.buildtypes

import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verify

import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.TaskContainer

import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat

import org.junit.Test


class BuildTypesPluginTest {

    private
    val buildType = BuildType("BT").apply {
        tasks("BT0", "my:BT1")
    }

    @Test
    fun `given empty subproject, it inserts all BuildType tasks without validation`() {

        // given:
        val subproject = ""
        val project = mock<Project>()
        val taskList = mutableListOf("TL0", "TL1")

        // when:
        project.insertBuildTypeTasksInto(taskList, 1, buildType, subproject)

        // then:
        assertThat(
            taskList,
            equalTo(listOf("TL0", "BT0", "my:BT1", "TL1")))
    }

    @Test
    fun `given non-empty subproject, it skips all tasks when project cannot be found`() {

        // given:
        val subproject = "sub"
        val project = mock<Project>()
        val taskList = mutableListOf("TL0", "TL1")

        // when:
        project.insertBuildTypeTasksInto(taskList, 1, buildType, subproject)

        // then:
        assertThat(
            taskList,
            equalTo(listOf("TL0", "TL1")))

        // and:
        verify(project).findProject(subproject)
    }

    @Test
    fun `given non-empty subproject, it prepends the subproject path to the BuildType tasks`() {

        // given:
        val subproject = "sub"
        val taskContainer = mock<TaskContainer>(name = "tasks") {
            on { findByPath("sub:BT0") } doReturn mock<Task>()
            on { findByPath("sub:my:BT1") } doReturn mock<Task>()
        }
        val project = mock<Project>(name = "project") {
            on { findProject(subproject) } doReturn mock<Project>()
            on { tasks } doReturn taskContainer
        }
        val taskList = mutableListOf("TL0", "TL1")

        // when:
        project.insertBuildTypeTasksInto(taskList, 1, buildType, subproject)

        // then:
        assertThat(
            taskList,
            equalTo(listOf("TL0", "sub:BT0", "sub:my:BT1", "TL1")))

        // and:
        verify(taskContainer).findByPath("sub:BT0")
        verify(taskContainer).findByPath("sub:my:BT1")
    }

    @Test
    fun `given non-empty subproject, it skips tasks which cannot be found`() {

        // given:
        val subproject = "sub"
        val taskContainer = mock<TaskContainer>(name = "tasks") {
            on { findByPath("sub:my:BT1") } doReturn mock<Task>()
        }
        val project = mock<Project>(name = "project") {
            on { findProject(subproject) } doReturn mock<Project>()
            on { tasks } doReturn taskContainer
        }
        val taskList = mutableListOf("TL0", "TL1")

        // when:
        project.insertBuildTypeTasksInto(taskList, 1, buildType, subproject)

        // then:
        assertThat(
            taskList,
            equalTo(listOf("TL0", "sub:my:BT1", "TL1")))

        // and:
        verify(taskContainer).findByPath("sub:BT0")
        verify(taskContainer).findByPath("sub:my:BT1")
    }
}
