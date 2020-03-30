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


class BuildTypesPluginTest {
//    private
//    val project = mockk<Project>()
//
//    private
//    val taskContainer = mockk<TaskContainer>()
//
//    private
//    val sanityCheckBuildType = BuildType("sanityCheck").apply {
//        tasks("classes", ":docs:javadocAll")
//    }
//
//    private
//    val quickTestBuildType = BuildType("quickTest").apply {
//        tasks("test", "integTest", "crossVersionTest")
//    }
//
//    private
//    val buildTypeTaskProvider = mockk<TaskProvider<Task>>(relaxed = true)
//
//    private
//    val subproject1 = mockk<Project>()
//
//    private
//    val subproject2 = mockk<Project>()
//
//    private
//    val subproject1Tasks = mockk<TaskContainer>()
//
//    private
//    val subproject2Tasks = mockk<TaskContainer>()
//
//    @Before
//    fun setUp() {
//        val subprojects = setOf(subproject1, subproject2)
//        every { project.tasks } returns taskContainer
//        every { project.project } returns project
//        every { project.findProperty(any()) } returns ""
//        every { project.name } returns "project"
//        every { project.subprojects } returns subprojects
//        every { project.findProject("subproject1") } returns subproject1
//        every { project.findProject("subproject2") } returns subproject2
//        every { project.findProject("unknown") } returns null
//        every { subproject1.tasks } returns subproject1Tasks
//        every { subproject2.tasks } returns subproject2Tasks
//        // subproject1: test integTest crossVersionTest classes
//        every { subproject1Tasks.findByName(any()) } answers { mockTask(args[0] as String, ":subproject1:${args[0]}") }
//        // subproject2: test classes
//        every { subproject2Tasks.findByName(any()) } answers {
//            if (args[0] == "test" || args[0] == "classes") {
//                mockTask(args[0] as String, ":subproject2:${args[0]}")
//            } else {
//                null
//            }
//        }
//        every { taskContainer.findByPath(any()) } answers {
//            val path = args[0] as String
//            if (path in listOf("subproject1:test", "subproject1:integTest", "subproject1:crossVersionTest", "subproject1:classes", "subproject2:test", "subproject2:classes")) {
//                mockTask(path.substringAfter(':'), ":$path")
//            } else {
//                null
//            }
//        }
//    }
//
//    private
//    fun mockTask(name: String, path: String): Task {
//        val task = mockk<Task>()
//        every { task.name } returns name
//        every { task.path } returns path
//        return task
//    }
//
//    @ParameterizedTest
//    @CsvSource(value = [
//        "100, 2",
//        "100, 3",
//        "100, 4",
//        "100, 5",
//        "100, 6",
//        "100, 7",
//        "100, 8",
//        "100, 9",
//        "100, 10"
//    ])
//    fun `can split files into buckets`(fileCount: Int, numberOfSplits: Int) {
//        val files = (1..fileCount).map { File("$it") }
//        val buckets = splitIntoBuckets(files, numberOfSplits)
//
//        Assertions.assertEquals(numberOfSplits, buckets.size)
//        Assertions.assertEquals(files, buckets.flatten())
//    }
//
//    @Test
//    fun `given empty subproject, it inserts all existing subproject tasks`() {
//
//        // given:
//        val subproject = ""
//        val taskList = mutableListOf("TL0", "TL1")
//
//        // when:
//        project.insertBuildTypeTasksInto(taskList, buildTypeTaskProvider, quickTestBuildType, subproject)
//
//        // then:
//        assertThat(
//            taskList,
//            equalTo(listOf("TL0", ":subproject2:test", ":subproject1:test", ":subproject1:integTest", ":subproject1:crossVersionTest", "TL1")))
//    }
//
//
//    @Test
//    fun `given empty subproject, it inserts subproject BuildType tasks without validation`() {
//
//        // given:
//        val subproject = ""
//        val taskList = mutableListOf("TL0", "TL1")
//
//        // when:
//        project.insertBuildTypeTasksInto(taskList, buildTypeTaskProvider, sanityCheckBuildType, subproject)
//
//        // then:
//        assertThat(
//            taskList,
//            equalTo(listOf("TL0", ":subproject2:classes", ":subproject1:classes", ":docs:javadocAll", "TL1")))
//    }
//
//    @Test
//    fun `given non-empty subproject, it skips all tasks when project cannot be found`() {
//
//        // given:
//        val subproject = "unknown"
//        val taskList = mutableListOf("TL0", "TL1")
//
//        // when:
//        project.insertBuildTypeTasksInto(taskList, buildTypeTaskProvider, sanityCheckBuildType, subproject)
//
//        // then:
//        assertThat(
//            taskList,
//            equalTo(listOf("TL0", "TL1")))
//
//        // and:
//        verify { project.findProject(subproject) }
//    }
//
//    @Test
//    fun `given non-empty subproject, it prepends the subproject path to the BuildType tasks`() {
//
//        // given:
//        val subproject = "subproject1"
//        val taskList = mutableListOf("TL0", "TL1")
//
//        // when:
//        project.insertBuildTypeTasksInto(taskList, buildTypeTaskProvider, quickTestBuildType, subproject)
//
//        // then:
//        assertThat(
//            taskList,
//            equalTo(listOf("TL0", "subproject1:test", "subproject1:integTest", "subproject1:crossVersionTest", "TL1")))
//
//        // and:
//        verify { taskContainer.findByPath("subproject1:test") }
//        verify { taskContainer.findByPath("subproject1:integTest") }
//        verify { taskContainer.findByPath("subproject1:crossVersionTest") }
//    }
//
//    @Test
//    fun `given non-empty subproject, it skips tasks which cannot be found`() {
//
//        // given:
//        val subproject = "subproject2"
//        val taskList = mutableListOf("TL0", "TL1")
//        // when:
//        project.insertBuildTypeTasksInto(taskList, buildTypeTaskProvider, quickTestBuildType, subproject)
//
//        // then:
//        assertThat(
//            taskList,
//            equalTo(listOf("TL0", "subproject2:test", "TL1")))
//
//        // and:
//        verify { taskContainer.findByPath("subproject2:test") }
//        verify { taskContainer.findByPath("subproject2:integTest") }
//        verify { taskContainer.findByPath("subproject2:crossVersionTest") }
//    }
}
