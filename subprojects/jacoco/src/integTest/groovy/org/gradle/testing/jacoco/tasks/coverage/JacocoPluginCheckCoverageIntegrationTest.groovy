/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.testing.jacoco.tasks.coverage

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.testing.jacoco.testutils.TestData

class JacocoPluginCheckCoverageIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        buildFile << """
            apply plugin: "java"
            apply plugin: "jacoco"

            repositories {
                mavenCentral()
            }
            dependencies {
                testCompile 'junit:junit:4.12'
            }
        """
        TestData.createTestFiles(this)
    }

    void checkCoverageTaskRequiresXmlReport() {
        when:
        buildFile << """
            jacoco {
                threshold 0.0
            }
        """
        then:
        fails('test', 'jacocoTestReportCheckCoverage', '--info')
        errorOutput.contains("Task jacocoTestReportCheckCoverage requires XML report in task jacocoTestReport")
    }

    void checkCoverageSucceedsWhenCoverageSufficient() {
        when:
        buildFile << """
            jacoco {
                threshold 0.0
            }
            jacocoTestReport.reports.xml.enabled true
        """
        then:
        succeeds('test', 'jacocoTestReportCheckCoverage')
    }

    void checkCoverageFailsWhenCoverageInsufficient() {
        when:
        buildFile << """
            jacoco {
                threshold 1.0
            }
            jacocoTestReport.reports.xml.enabled true
        """

        then:
        fails('test', 'jacocoTestReportCheckCoverage')
    }

    void checkTaskDependsOnCheckCoverageTask() {
        when:
        buildFile << """
            jacoco {
                threshold 1.0
            }
            jacocoTestReport.reports.xml.enabled true
        """

        then:
        fails('check')
    }
}
