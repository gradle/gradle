/*
 * Copyright 2012 the original author or authors.
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
package org.gradle.build

import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.testing.Test;

class TestReportAggregator extends Copy {
    def projects

    File testResultsDir

    @OutputDirectory
    File testReportDir

    def TestReportAggregator() {
        dependsOn { testTasks }
        from { inputTestResultDirs }
        into { testResultsDir }
    }

    @TaskAction
    def aggregate() {
        def report = new org.gradle.api.internal.tasks.testing.junit.report.DefaultTestReport(testReportDir: testReportDir, testResultsDir: testResultsDir)
        report.generateReport()
    }

    def getTestTasks() {
        projects.collect { it.tasks.withType(Test) }.flatten()
    }

    def getInputTestResultDirs() {
        testTasks*.testResultsDir
    }

}
