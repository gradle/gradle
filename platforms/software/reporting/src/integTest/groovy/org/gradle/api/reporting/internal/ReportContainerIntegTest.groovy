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

import org.gradle.api.Describable
import org.gradle.api.model.ObjectFactory
import org.gradle.api.reporting.Report
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.internal.Describables
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions

import javax.inject.Inject

class ReportContainerIntegTest extends AbstractIntegrationSpec {

    def task = ":createReports"

    def setup() {
        // Do not define the task in the build script, so that changes to the build script do not invalidate the task implementation
        file("buildSrc/src/main/groovy/TestReportContainer.groovy") << """
            import ${Report.class.name}
            import ${ObjectFactory.class.name}
            import ${Describable.class.name}
            import ${DefaultSingleFileReport.class.name}
            import ${SingleDirectoryReport.class.name}
            import ${DelegatingReportContainer.class.name}
            import ${DefaultReportContainer.class.name}

            import ${Inject.class.name}

            class TestReportContainer extends DelegatingReportContainer<Report> {

                @Inject
                TestReportContainer(ObjectFactory objectFactory, Describable owner) {
                    super(DefaultReportContainer.create(objectFactory, Report, factory -> [
                        factory.instantiateReport(DefaultSingleFileReport, "file1", owner),
                        factory.instantiateReport(DefaultSingleFileReport, "file2", owner),
                        factory.instantiateReport(SingleDirectoryReport, "dir1", owner, null),
                        factory.instantiateReport(SingleDirectoryReport, "dir2", owner, null)
                    ]))
                }

            }
        """
        file("buildSrc/src/main/groovy/TestTask.groovy") << """
            import org.gradle.api.*
            import org.gradle.api.provider.*
            import org.gradle.api.tasks.*
            import org.gradle.api.reporting.*
            import org.gradle.api.reporting.internal.*
            import ${Describables.class.name}

            import ${Inject.class.name}

            abstract class TestTask extends DefaultTask {

                @Nested
                TestReportContainer reports

                @Inject
                public TestTask() {
                    this.reports = getProject().getObjects().newInstance(TestReportContainer, Describables.quoted("Task", getIdentityPath()))
                }

                @Input
                abstract Property<String> getValue()

                @TaskAction
                def doStuff() {
                    reports.enabled.each {
                         if (it.outputType == Report.OutputType.FILE) {
                             assert it.outputLocation.asFile.get().parentFile.exists() && it.outputLocation.asFile.get().parentFile.directory
                             it.outputLocation.asFile.get() << value.get()
                         } else {
                             assert it.outputLocation.asFile.get().exists() && it.outputLocation.asFile.get().directory
                             new File(it.outputLocation.asFile.get(), "file1") << value.get()
                             new File(it.outputLocation.asFile.get(), "file2") << value.get()
                         }
                    }
                }
            }

        """
        buildFile << """
            ext.value = "bar"

            task createReports(type: TestTask) { task ->
                value = provider { project.value }
                reports.all {
                    it.required = true
                    outputLocation.set(it.outputType == Report.OutputType.DIRECTORY ? file(it.name) : file("\$it.name/file"))
                }
            }
        """
    }

    @Requires(IntegTestPreconditions.NotParallelExecutor)
    def "task up to date when no reporting configuration change"() {
        expect:
        succeeds(task)
        executedAndNotSkipped(task)

        and:
        succeeds(task)
        skipped(task)
    }

    @Requires(IntegTestPreconditions.NotParallelExecutor)
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
    @ToBeFixedForConfigurationCache(because = "https://github.com/gradle/gradle/issues/6619") // file1.outputLocation doesn't carry task dependency and cannot be serialized by CC when used as value
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
