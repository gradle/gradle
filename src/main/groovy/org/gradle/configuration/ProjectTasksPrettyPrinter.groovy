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

import org.gradle.api.Project
import org.gradle.api.Task

/**
 * @author Hans Dockter
 */
class ProjectTasksPrettyPrinter {
    String getPrettyText(Map tasks) {
        StringWriter stringWriter = new StringWriter()
        new BufferedWriter(stringWriter).withWriter {Writer writer ->
            tasks.keySet().each {Project project ->
                writer.writeLine('*' * 50)
                writer.writeLine("Project: $project")
                tasks[project].each {Task task ->
                    writer.writeLine("++Task: $task.path: $task.dependsOn")
                }
            }
        }
        stringWriter.toString()
    }
}
