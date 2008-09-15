/*
 * Copyright 2007 the original author or authors.
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
 
package org.gradle.configuration

import groovy.io.PlatformLineWriter
import org.gradle.api.Project
import org.gradle.api.Task
import static org.junit.Assert.*
import org.junit.Test
import org.gradle.api.tasks.TaskDependency;

/**
 * @author Hans Dockter
 */
class ProjectTasksPrettyPrinterTest {
    @Test public void testGetPrettyText() {
        String expectedProject1String = ':project1'
        String expectedProject2String = ':project2'
        String expectedTask11String = ':task11'
        Set task11DependsOn = [':task111', ':task112'] as Set
        TaskDependency task11Dependency = [getDependencies: {task11DependsOn}] as TaskDependency
        String expectedTask12String = ':task12'
        String expectedTask21String = ':task21'
        TaskDependency emptyDependency = [getDependencies: {[] as Set}] as TaskDependency

        Project project1 = [getPath: {expectedProject1String}, compareTo: {-1}] as Project
        Project project2 = [getPath: {expectedProject2String}, compareTo: {1}] as Project
        Task task11 = [getPath: {expectedTask11String}, getTaskDependencies: {task11Dependency}, compareTo: {-1}] as Task
        Task task12 = [getPath: {expectedTask12String}, getTaskDependencies: {emptyDependency}, compareTo: {1}] as Task
        Task task21 = [getPath: {expectedTask21String}, getTaskDependencies: {emptyDependency}] as Task

        Map tasks = [(project1): new LinkedHashSet([task11, task12]), (project2): [task21] as Set]
        // We can't use triple quoted strings for cresting the expected value, as they use always /n as
        // line separator. In contrast to writeLine, which uses the platform specific line separator. This
        // would(has) lead to failing tests under Windows.
        StringWriter stringWriter = new StringWriter()
        String separator = '*' * 50
        new PlatformLineWriter(stringWriter).withWriter { it.write("""
$separator
Project :project1
  Task :task11 [:task111, :task112]
  Task :task12 []

$separator
Project :project2
  Task :task21 []
""")
        }
        assertEquals(stringWriter.toString(), new ProjectTasksPrettyPrinter().getPrettyText(tasks))
    }
}
