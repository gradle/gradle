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

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.gradle.api.Project
import org.gradle.api.tasks.TaskContainer
import org.gradle.gradlebuild.test.integrationtests.splitIntoBuckets
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.io.File


class BuildTypesPluginTest {
    private
    val project = mockk<Project>()

    private
    val taskContainer = mockk<TaskContainer>()

    private
    val buildType = BuildType("BT").apply {
        tasks("BT0", "my:BT1")
    }

    @Before
    fun setUp() {
        every { project.tasks } returns taskContainer
        every { project.project } returns project
        every { project.findProperty(any()) } returns ""
        every { project.name } returns "project"
        every { project.findProject(any()) } returns null
        every { taskContainer.findByPath(any()) } returns null
    }

    @ParameterizedTest
    @CsvSource(value = [
        "100, 2",
        "100, 3",
        "100, 4",
        "100, 5",
        "100, 6",
        "100, 7",
        "100, 8",
        "100, 9",
        "100, 10"
    ])
    fun `can split files into buckets`(fileCount: Int, numberOfSplits: Int) {
        val files = (1..fileCount).map { File("$it") }
        val buckets = splitIntoBuckets(files, numberOfSplits)

        Assertions.assertEquals(numberOfSplits, buckets.size)
        Assertions.assertEquals(files, buckets.flatten())
    }

    @Test
    fun `given empty subproject, it inserts all BuildType tasks without validation`() {

        // given:
        val subproject = ""
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
        val taskList = mutableListOf("TL0", "TL1")

        // when:
        project.insertBuildTypeTasksInto(taskList, 1, buildType, subproject)

        // then:
        assertThat(
            taskList,
            equalTo(listOf("TL0", "TL1")))

        // and:
        verify { project.findProject(subproject) }
    }

    @Test
    fun `given non-empty subproject, it prepends the subproject path to the BuildType tasks`() {

        // given:
        val subproject = "sub"
        val taskList = mutableListOf("TL0", "TL1")
        every { taskContainer.findByPath("sub:BT0") } returns mockk()
        every { taskContainer.findByPath("sub:my:BT1") } returns mockk()
        every { project.findProject(subproject) } returns mockk()

        // when:
        project.insertBuildTypeTasksInto(taskList, 1, buildType, subproject)

        // then:
        assertThat(
            taskList,
            equalTo(listOf("TL0", "sub:BT0", "sub:my:BT1", "TL1")))

        // and:
        verify { taskContainer.findByPath("sub:BT0") }
        verify { taskContainer.findByPath("sub:my:BT1") }
    }

    @Test
    fun `given non-empty subproject, it skips tasks which cannot be found`() {

        // given:
        val subproject = "sub"
        val taskList = mutableListOf("TL0", "TL1")
        every { taskContainer.findByPath("sub:my:BT1") } returns mockk()
        every { project.findProject(subproject) } returns mockk()

        // when:
        project.insertBuildTypeTasksInto(taskList, 1, buildType, subproject)

        // then:
        assertThat(
            taskList,
            equalTo(listOf("TL0", "sub:my:BT1", "TL1")))

        // and:
        verify { taskContainer.findByPath("sub:BT0") }
        verify { taskContainer.findByPath("sub:my:BT1") }
    }
}
