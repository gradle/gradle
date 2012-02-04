/*
 * Copyright 2009 the original author or authors.
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

package org.gradle.api.reporting

import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.internal.Instantiator
import org.gradle.api.reporting.internal.TaskGeneratedReport
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

class TaskReportContainerTest extends Specification {

    final Project project = ProjectBuilder.builder().build()
    final Task task = project.task("testTask")

    static class TestReportContainer extends TaskReportContainer<Report> {
        TestReportContainer(Task task, Closure c) {
            super(Report, task)

            c.delegate = new Object() {
                Report file(String name) {
                    add(TaskGeneratedReport, name, Report.OutputType.FILE, task)
                }
                Report dir(String name) {
                    add(TaskGeneratedReport, name, Report.OutputType.DIRECTORY, task)
                }
            }

            c()
        }
    }

    def container

    DefaultReportContainer createContainer(Closure c) {
        container = project.services.get(Instantiator).newInstance(TestReportContainer, task, c)
        container.all {
            it.enabled true
            destination it.name
        }
        container
    }

    List<File> getOutputFiles(task = task) {
        task.outputs.files.files.toList().sort()
    }

    List<String> getInputPropertyValue() {
        task.inputs.properties[container.taskInputPropertyName] as List<String>
    }

    def "tasks inputs and outputs are wired correctly"() {
        when:
        createContainer {
            dir("b")
            file("a")
        }

        then:
        outputFiles == [container.a.destination, container.b.destination]
        inputPropertyValue == ["a", "b"]

        when:
        container.b.enabled false

        then:
        outputFiles == [container.a.destination]
        inputPropertyValue == ["a"]

        when:
        container*.enabled false

        then:
        outputFiles == []
        inputPropertyValue == []

        when:
        container.a.enabled true

        then:
        outputFiles == [container.a.destination]
        inputPropertyValue == ["a"]
    }
    
}
