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

package org.gradle.testing.jacoco.plugins

import org.gradle.api.Project
import org.gradle.api.reporting.ReportingExtension
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.file.TestFile
import org.junit.Test

class JacocoPluginIntegrationTest extends AbstractIntegrationSpec {

    private static final String REPORT_XML_FILE_NAME = "jacocoTestReport.xml"
    private static final String REPORT_CSV_FILE_NAME = "jacocoTestReport.csv"

    private static final String REPORTING_BASE = "${Project.DEFAULT_BUILD_DIR_NAME}/${ReportingExtension.DEFAULT_REPORTS_DIR_NAME}"
    private static final String JACOCO_REPORTING_RELATIVE_PATH = "jacoco/test/"
    private static final String REPORT_HTML_DEFAULT_PATH = "${REPORTING_BASE}/${JACOCO_REPORTING_RELATIVE_PATH}/html/index.html"

    private static final String REPORT_XML_DEFAULT_PATH = "${REPORTING_BASE}/$JACOCO_REPORTING_RELATIVE_PATH/${REPORT_XML_FILE_NAME}"
    private static final String REPORT_CSV_DEFAULT_REPORT = "${REPORTING_BASE}/$JACOCO_REPORTING_RELATIVE_PATH/${REPORT_CSV_FILE_NAME}"

    def setup() {
        buildFile << """
            apply plugin: "java"
            apply plugin: "jacoco"

            repositories {
                mavenCentral()
            }
            dependencies {
                testCompile 'junit:junit:4.11'
            }
        """
        createTestFiles()
    }

    @Test
    public void generatesHtmlReportOnlyAsDefault() {
        when:
        succeeds('test', 'jacocoTestReport')
        then:
        file(REPORTING_BASE).listFiles().collect{it.name} as Set == ["jacoco", "tests"] as Set
        file(REPORT_HTML_DEFAULT_PATH).exists()
        file("${REPORTING_BASE}/${JACOCO_REPORTING_RELATIVE_PATH}").listFiles().collect{it.name} == ["html"]
    }

    @Test
    public void canConfigureReportsInJacocoTestReport() {
        given:
        buildFile << """
            jacocoTestReport{
                reports {
                    xml.enabled true
                    csv.enabled false
                    html.destination "\${buildDir}/jacocoHtml"
                }
            }
            """
        when:
        succeeds('test', 'jacocoTestReport')
        then:
        file("build/jacocoHtml/index.html").exists()
        file(REPORT_XML_DEFAULT_PATH).exists()
        !file(REPORT_CSV_DEFAULT_REPORT).exists()
    }

    @Test
    public void respectsReportingBaseDir() {
        given:
        buildFile << """
            jacocoTestReport{
                reports.xml.enabled = true
                reports.csv.enabled = true
            }
            reporting{
                baseDir = "\$buildDir/customReports"
            }"""
        when:
        succeeds('test', 'jacocoTestReport')
        then:
        file("build/customReports/$JACOCO_REPORTING_RELATIVE_PATH/html/index.html").exists()
        file("build/customReports/$JACOCO_REPORTING_RELATIVE_PATH/$REPORT_CSV_FILE_NAME").exists()
        file("build/customReports/$JACOCO_REPORTING_RELATIVE_PATH/$REPORT_CSV_FILE_NAME").exists()
    }

    @Test
    public void canConfigureReportDirectory() {
        given:
        def customReportDirectory = "customJacocoReportDir"
        buildFile << """
            jacocoTestReport{
                reports.xml.enabled = true
                reports.csv.enabled = true
            }
            jacoco {
                reportsDir = new File(buildDir, "$customReportDirectory")
            }
            """
        when:
        succeeds('test', 'jacocoTestReport')
        then:
        file("build/${customReportDirectory}/test/html/index.html").exists()
        file("build/${customReportDirectory}/test/${REPORT_XML_FILE_NAME}").exists()
        file("build/${customReportDirectory}/test/${REPORT_CSV_FILE_NAME}").exists()
    }

    @Test
    public void jacocoReportIsIncremental() {
        when:
        succeeds('test', 'jacocoTestReport')
        then:
        file(REPORT_HTML_DEFAULT_PATH).exists()

        when:
        succeeds('jacocoTestReport')
        then:
        skippedTasks.contains(":jacocoTestReport")
        file(REPORT_HTML_DEFAULT_PATH).exists()

        when:
        file("${REPORTING_BASE}/jacoco/test/html/.resources").deleteDir()
        succeeds('test', 'jacocoTestReport')
        then:
        !skippedTasks.contains(":jacocoTestReport")
        file(REPORT_HTML_DEFAULT_PATH).exists()
    }

    @Test
    public void jacocoTestReportIsSkippedIfNoCoverageDataAvailable() {
        when:
        def executionResult = succeeds('jacocoTestReport')
        then:
        executionResult.assertTaskSkipped(':jacocoTestReport')
    }

    @Test
    public void canUseCoverageDataFromPreviousRunForCoverageReport() {
        when:
        succeeds('jacocoTestReport')
        then:
        skippedTasks.contains(":jacocoTestReport")
        !file(REPORT_HTML_DEFAULT_PATH).exists()

        when:
        succeeds('test')
        and:

        succeeds('jacocoTestReport')
        then:
        executedTasks.contains(":jacocoTestReport")
        file(REPORT_HTML_DEFAULT_PATH).exists()
    }

    private void createTestFiles() {
        file("src/main/java/org/gradle/Class1.java") <<
                "package org.gradle; public class Class1 { public boolean isFoo(Object arg) { return true; } }"
        file("src/test/java/org/gradle/Class1Test.java") <<
                "package org.gradle; import org.junit.Test; public class Class1Test { @Test public void someTest() { new Class1().isFoo(\"test\"); } }"
    }
}

