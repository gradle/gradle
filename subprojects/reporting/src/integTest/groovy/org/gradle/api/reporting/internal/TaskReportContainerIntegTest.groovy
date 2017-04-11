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

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import spock.lang.IgnoreIf

class TaskReportContainerIntegTest extends AbstractIntegrationSpec {

    def task = ":createReports"

    def setup() {
        buildFile << """
            import org.gradle.api.reporting.*
            import org.gradle.api.reporting.internal.*
            
            ext.value = "bar"

            class TestTaskReportContainer extends TaskReportContainer<Report> {
                TestTaskReportContainer(Task task) {
                    super(Report, task)
                    add(TaskGeneratedReport, "file1", Report.OutputType.FILE, task)
                    add(TaskGeneratedReport, "file2", Report.OutputType.FILE, task)
                    add(TaskGeneratedReport, "dir1", Report.OutputType.DIRECTORY, task)
                    add(TaskGeneratedReport, "dir2", Report.OutputType.DIRECTORY, task)
                }
            }

            class TestTask extends DefaultTask {
                @Nested
                TaskReportContainer reports = project.services.get(org.gradle.internal.reflect.Instantiator).newInstance(TestTaskReportContainer, this)

                @TaskAction
                def doStuff() {
                    reports.enabled.each {
                         if (it.outputType == Report.OutputType.FILE) {
                             assert it.destination.parentFile.exists() && it.destination.parentFile.directory
                             it.destination << project.value
                         } else {
                             assert it.destination.exists() && it.destination.directory
                             new File(it.destination, "file1") << project.value
                             new File(it.destination, "file2") << project.value
                         }
                    }
                }
            }

            task createReports(type: TestTask) { task ->
                inputs.property "foo", { project.value }
                reports.all {
                    it.enabled true
                    destination it.outputType == Report.OutputType.DIRECTORY ? file(it.name) : file("\$it.name/file")
                }
            }
        """
    }

    @IgnoreIf({GradleContextualExecuter.parallel})
    def "task up to date when no reporting configuration change"() {
        expect:
        succeeds(task) && task in nonSkippedTasks

        and:
        succeeds(task) && task in skippedTasks
    }

    @IgnoreIf({GradleContextualExecuter.parallel})
    def "task not up to date when enabled set changes"() {
        expect:
        succeeds(task) && task in nonSkippedTasks

        when:
        buildFile << """
            createReports.reports.file1.enabled false
        """

        then:
        succeeds(task) && task in nonSkippedTasks
    }

    @IgnoreIf({GradleContextualExecuter.parallel})
    def "task not up to date when enabled set changes but output files stays the same"() {
        given:
        buildFile << """
            createReports.reports.configure {
                [dir1, dir2, file2]*.enabled false
            }
        """

        expect:
        succeeds(task) && task in nonSkippedTasks

        and:
        succeeds(task) && task in skippedTasks

        when:
        buildFile << """
            createReports.reports.configure {
                file1.enabled false
                file2.enabled false
                file2.destination file1.destination
            }
        """

        then:
        succeeds(task) && task in nonSkippedTasks
    }
}
