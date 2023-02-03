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

package org.gradle.testing.jacoco.plugins

import org.gradle.integtests.fixtures.AbstractTaskRelocationIntegrationTest
import org.gradle.testing.jacoco.plugins.fixtures.JavaProjectUnderTest

class JacocoReportRelocationIntegrationTest extends AbstractTaskRelocationIntegrationTest {

    private final JavaProjectUnderTest javaProjectUnderTest = new JavaProjectUnderTest(testDirectory)

    @Override
    protected String getTaskName() {
        return ":jacocoTestReport"
    }

    @Override
    protected void setupProjectInOriginalLocation() {
        javaProjectUnderTest.writeBuildScript().writeSourceFiles()

        buildFile << """
            sourceSets.test.java.destinationDirectory.set(file("build/classes/test"))
        """

        succeeds "test"
    }

    @Override
    protected void moveFilesAround() {
        buildFile << """
            sourceSets.test.java.destinationDirectory.set(file("build/test-classes"))
            jacocoTestReport.executionData.from = files("build/jacoco.exec")
        """
        file("build/classes/test").assertIsDir().renameTo(file("build/test-classes"))
        file("build/jacoco/test.exec").assertIsFile().renameTo(file("build/jacoco.exec"))
    }

    @Override
    protected void removeResults() {
        file("build/reports/jacoco").assertIsDir().deleteDir()
    }

    @Override
    protected extractResults() {
        file("build/reports/jacoco/test/html/index.html").text
    }
}
