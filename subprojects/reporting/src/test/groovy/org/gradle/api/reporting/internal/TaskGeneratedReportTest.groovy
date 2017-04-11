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



package org.gradle.api.reporting.internal

import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.reporting.Report
import org.gradle.api.tasks.Copy
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

class TaskGeneratedReportTest extends Specification {

    def "can resolve destination"() {
        given:
        Project project = ProjectBuilder.builder().build()
        Task task = project.task("task", type: Copy)
        TaskGeneratedReport report = new TaskGeneratedReport("report", Report.OutputType.FILE, task)
        File destinationFile = project.file("foo")

        when:
        report.destination = destinationFile

        then:
        report.destination == destinationFile
    }

}
