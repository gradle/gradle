/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.integtests.samples

import org.gradle.integtests.fixtures.AbstractIntegrationTest
import org.gradle.integtests.fixtures.DefaultTestExecutionResult
import org.gradle.integtests.fixtures.Sample
import org.gradle.test.fixtures.file.TestFile
import org.junit.Rule
import org.junit.Test


class SamplesJacocoIntegrationTest extends AbstractIntegrationTest {

    @Rule
    public final Sample sample = new Sample(testDirectoryProvider, 'testing/jacoco')

    @Test
    public void canRunQuickstartSample() {
        TestFile projectDir = sample.dir.file("quickstart")

        // Build and test projects
        executer.inDirectory(projectDir).withTasks('clean', 'jacocoTestReport').run()
        projectDir.file("build/jacoco/jacocoTest.exec").assertExists()
        projectDir.file("build/jacoco/classpathdumps").assertExists()
        projectDir.file("build/customJacocoReportDir/test/html/index.html").assertExists()
    }

    @Test
    public void canRunApplicationSample() {
        TestFile projectDir = sample.dir.file("application")

        // Build and test projects
        executer.inDirectory(projectDir).withTasks('clean', 'applicationCodeCoverageReport').run()

        projectDir.file("build/reports/jacoco/applicationCodeCoverageReport/applicationCodeCoverageReport.csv").assertExists()
        projectDir.file("build/reports/jacoco/applicationCodeCoverageReport/applicationCodeCoverageReport.xml").assertExists()
        projectDir.file("build/reports/jacoco/applicationCodeCoverageReport/html/index.html").assertExists()
        projectDir.file("build/jacoco/classpathdumps").assertExists()
        projectDir.file("build/customJacocoReportDir/test/html/index.html").assertExists()
    }
}