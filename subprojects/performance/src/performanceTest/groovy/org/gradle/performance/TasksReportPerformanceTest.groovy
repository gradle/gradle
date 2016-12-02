/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.performance

import org.gradle.performance.categories.BasicPerformanceTest
import org.junit.experimental.categories.Category
import spock.lang.Unroll

import static org.gradle.performance.TasksReportPerformanceTest.TasksReport.BASIC_TASKS_REPORT
import static org.gradle.performance.TasksReportPerformanceTest.TasksReport.DETAILED_TASKS_REPORT

@Category(BasicPerformanceTest)
class TasksReportPerformanceTest extends AbstractCrossVersionPerformanceTest {

    private final static String SMALL_TEST_PROJECT = 'small'
    private final static String MULTI_TEST_PROJECT = 'multi'

    @Unroll("Project '#testProject' #reportType.description")
    def "tasks report"() {
        given:
        runner.testId = "$reportType.description $testProject"
        runner.testProject = testProject
        runner.tasksToRun = reportType.tasks
        runner.gradleOpts = ['-Xms128m', '-Xmx128m']

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()

        where:
        testProject        | reportType
        SMALL_TEST_PROJECT | BASIC_TASKS_REPORT
        SMALL_TEST_PROJECT | DETAILED_TASKS_REPORT
        MULTI_TEST_PROJECT | BASIC_TASKS_REPORT
        MULTI_TEST_PROJECT | DETAILED_TASKS_REPORT
    }

    private static class TasksReport {

        public final static TasksReport BASIC_TASKS_REPORT = new TasksReport(['tasks'], 'basic tasks report')
        public final static TasksReport DETAILED_TASKS_REPORT = new TasksReport(['tasks', '--all'], 'detailed tasks report')
        private final List<String> tasks
        private final String description

        TasksReport(List<String> tasks, String description) {
            this.tasks = tasks
            this.description = description
        }

        List<String> getTasks() {
            tasks
        }

        String getDescription() {
            description
        }
    }
}
