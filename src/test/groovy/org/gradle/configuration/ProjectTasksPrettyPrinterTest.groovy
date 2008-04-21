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

/**
 * @author Hans Dockter
 */
class ProjectTasksPrettyPrinterTest extends GroovyTestCase {
    void testGetPrettyText() {
        String expectedProject1String = 'project1'
        String expectedProject2String = 'project2'
        String expectedTask11String = 'task11'
        Set task11DependsOn = ['task111, task112'] as SortedSet
        String expectedTask12String = 'task12'
        String expectedTask21String = 'task21'

        Project project1 = [toString: {expectedProject1String}] as Project
        Project project2 = [toString: {expectedProject2String}] as Project
        Task task11 = [getPath: {expectedTask11String}, getDependsOn: {task11DependsOn}] as Task
        Task task12 = [getPath: {expectedTask12String}, getDependsOn: {[] as SortedSet}] as Task
        Task task21 = [getPath: {expectedTask21String}, getDependsOn: {[] as SortedSet}] as Task

        Map tasks = [(project1): [task11, task12], (project2): [task21]]
        // We can't use triple quoted strings for cresting the expected value, as they use always /n as
        // line separator. In contrast to writeLine, which uses the platform specific line separator. This
        // would(has) lead to failing tests under Windows.
        StringWriter stringWriter = new StringWriter()
        String separator = '*' * 50
        new PlatformLineWriter(stringWriter).withWriter { it.write("""$separator
Project: $project1
++Task: $task11.path: $task11.dependsOn
++Task: $task12.path: $task12.dependsOn
$separator
Project: $project2
++Task: $task21.path: $task21.dependsOn
""")
        }
        assertEquals(stringWriter.toString(), new ProjectTasksPrettyPrinter().getPrettyText(tasks))
    }
}
