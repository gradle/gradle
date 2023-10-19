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
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions

class TaskReportContainerIntegTest extends AbstractIntegrationSpec {

    def task = ":createReports"

    def setup() {
        // Do not define the task in the build script, so that changes to the build script do not invalidate the task implementation
        file("buildSrc/src/main/groovy/TestTaskReportContainer.groovy") << """
            import org.gradle.api.*
            import org.gradle.api.reporting.*
            import org.gradle.api.reporting.internal.*
            import org.gradle.api.internal.CollectionCallbackActionDecorator

            class TestTaskReportContainer extends TaskReportContainer<Report> {
                TestTaskReportContainer(Task task) {
                    super(Report, task, CollectionCallbackActionDecorator.NOOP)
                    add(TaskGeneratedSingleFileReport, "file1", task)
                    add(TaskGeneratedSingleFileReport, "file2", task)
                    add(TaskGeneratedSingleDirectoryReport, "dir1", task, null)
                    add(TaskGeneratedSingleDirectoryReport, "dir2", task, null)
                }
            }
        """
        file("buildSrc/src/main/groovy/TestTask.groovy") << """
            import org.gradle.api.*
            import org.gradle.api.tasks.*
            import org.gradle.api.reporting.*
            import org.gradle.api.reporting.internal.*

            class TestTask extends DefaultTask {
                @Nested
                TaskReportContainer reports = project.services.get(org.gradle.internal.reflect.Instantiator).newInstance(TestTaskReportContainer, this)

                @TaskAction
                def doStuff() {
                    reports.enabled.each {
                         if (it.outputType == Report.OutputType.FILE) {
                             assert it.outputLocation.asFile.get().parentFile.exists() && it.outputLocation.asFile.get().parentFile.directory
                             it.outputLocation.asFile.get() << project.value
                         } else {
                             assert it.outputLocation.asFile.get().exists() && it.outputLocation.asFile.get().directory
                             new File(it.outputLocation.asFile.get(), "file1") << project.value
                             new File(it.outputLocation.asFile.get(), "file2") << project.value
                         }
                    }
                }
            }

        """
        buildFile << """
            ext.value = "bar"

            task createReports(type: TestTask) { task ->
                inputs.property "foo", { project.value }
                reports.all {
                    it.required = true
                    outputLocation.set(it.outputType == Report.OutputType.DIRECTORY ? file(it.name) : file("\$it.name/file"))
                }
            }
        """
    }

    @Requires(IntegTestPreconditions.NotParallelExecutor)
    @ToBeFixedForConfigurationCache(because = "Task.getProject() during execution")
    def "task up to date when no reporting configuration change"() {
        expect:
        succeeds(task)
        executedAndNotSkipped(task)

        and:
        succeeds(task)
        skipped(task)
    }

    @Requires(IntegTestPreconditions.NotParallelExecutor)
    @ToBeFixedForConfigurationCache
    def "task not up to date when enabled set changes"() {
        expect:
        succeeds(task)
        executedAndNotSkipped(task)

        when:
        buildFile << """
            createReports.reports.file1.required = false
        """

        then:
        succeeds(task)
        executedAndNotSkipped(task)
    }

    @Requires(IntegTestPreconditions.NotParallelExecutor)
    @ToBeFixedForConfigurationCache
    def "task not up to date when enabled set changes but output files stays the same"() {
        given:
        buildFile << """
            createReports.reports.configure {
                [dir1, dir2, file2]*.required = false
            }
        """

        expect:
        succeeds(task)
        executedAndNotSkipped(task)

        and:
        succeeds(task)
        skipped(task)

        when:
        buildFile << """
            createReports.reports.configure {
                file1.required = false
                file2.required = false
                file2.outputLocation.set(file1.outputLocation)
            }
        """

        then:
        succeeds(task)
        executedAndNotSkipped(task)
    }
}
