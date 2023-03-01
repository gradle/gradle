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

import org.gradle.api.Project
import org.gradle.api.reporting.ReportingExtension
import org.gradle.integtests.fixtures.TargetCoverage
import org.gradle.testing.jacoco.plugins.fixtures.JacocoCoverage
import org.gradle.testing.jacoco.plugins.fixtures.JacocoReportFixture
import spock.lang.Issue

@TargetCoverage({ JacocoCoverage.supportedVersionsByJdk })
class JacocoPluginMultiVersionIntegrationTest extends JacocoMultiVersionIntegrationTest {

    private static final String REPORTING_BASE = "${Project.DEFAULT_BUILD_DIR_NAME}/${ReportingExtension.DEFAULT_REPORTS_DIR_NAME}"
    private static final String REPORT_HTML_DEFAULT_PATH = "${REPORTING_BASE}/jacoco/test/html/index.html"
    private static final String REPORT_XML_DEFAULT_PATH = "${REPORTING_BASE}/jacoco/test/jacocoTestReport.xml"
    private static final String REPORT_CSV_DEFAULT_REPORT = "${REPORTING_BASE}/jacoco/test/jacocoTestReport.csv"

    def setup() {
        javaProjectUnderTest.writeSourceFiles()
    }

    def "generates html report only as default"() {
        when:
        succeeds('test', 'jacocoTestReport')

        then:
        file(REPORTING_BASE).listFiles().collect { it.name } as Set == ["jacoco", "tests"] as Set
        file(REPORT_HTML_DEFAULT_PATH).exists()
        file("${REPORTING_BASE}/jacoco/test").listFiles().collect { it.name } == ["html"]
        file("${REPORTING_BASE}/jacoco/test/html/org.gradle/Class1.java.html").exists()
        htmlReport().totalCoverage() == 100
    }

    def "can configure reports in jacoco test report"() {
        given:
        buildFile << """
            jacocoTestReport {
                reports {
                    xml.required = true
                    csv.required = true
                    html.outputLocation.set(file("\${buildDir}/jacocoHtml"))
                }
            }
            """

        when:
        succeeds('test', 'jacocoTestReport')

        then:
        htmlReport("build/jacocoHtml").totalCoverage() == 100
        file(REPORT_XML_DEFAULT_PATH).exists()
        file(REPORT_CSV_DEFAULT_REPORT).exists()
    }

    def "respects reporting base dir"() {
        given:
        buildFile << """
            jacocoTestReport {
                reports.xml.required = true
                reports.csv.required = true
            }
            reporting{
                baseDir = "\$buildDir/customReports"
            }"""

        when:
        succeeds('test', 'jacocoTestReport')

        then:
        htmlReport("build/customReports/jacoco/test/html").totalCoverage() == 100
        file("build/customReports/jacoco/test/jacocoTestReport.xml").exists()
        file("build/customReports/jacoco/test/jacocoTestReport.csv").exists()
    }

    def "can configure report directory"() {
        given:
        def customReportDirectory = "customJacocoReportDir"
        buildFile << """
            jacocoTestReport {
                reports.xml.required = true
                reports.csv.required = true
            }
            jacoco {
                reportsDirectory = new File(buildDir, "$customReportDirectory")
            }
            """

        when:
        succeeds('test', 'jacocoTestReport')

        then:
        htmlReport("build/${customReportDirectory}/test/html").totalCoverage() == 100
        file("build/${customReportDirectory}/test/jacocoTestReport.xml").exists()
        file("build/${customReportDirectory}/test/jacocoTestReport.csv").exists()
    }

    def "jacoco test report is skipped if no coverage data available"() {
        when:
        def executionResult = succeeds('jacocoTestReport')
        then:
        executionResult.assertTaskSkipped(':jacocoTestReport')
    }

    def "can use coverage data from previous run for coverage report"() {
        when:
        succeeds('jacocoTestReport')

        then:
        skipped(":jacocoTestReport")
        !file(REPORT_HTML_DEFAULT_PATH).exists()

        when:
        succeeds('test')

        and:
        succeeds('jacocoTestReport')

        then:
        executed(":jacocoTestReport")
        htmlReport().totalCoverage() == 100
    }

    def "can merge coverage data"() {
        given:
        file("src/main/java/Thing.java") << """
public class Thing {
    Thing() { printMessage("hi"); }
    Thing(String msg) { printMessage(msg); }

    private void printMessage(String msg) {
        System.out.println(msg);
    }
}
"""
        file("src/otherTest/java/ThingTest.java") << """
public class ThingTest {
    @org.junit.Test public void someTest() { new Thing(); }
}
"""
        buildFile << """
    testing.suites {
        otherTest(JvmTestSuite) {
            useJUnit()
            dependencies {
                implementation project()
            }
        }
    }

    task mergedReport(type: JacocoReport) {
        dependsOn test, otherTest
        executionData.from(test.jacoco.destinationFile, otherTest.jacoco.destinationFile)
        sourceDirectories.from(sourceSets.main.java.sourceDirectories)
        classDirectories.from(sourceSets.main.output)
    }
"""
        when:
        succeeds  'mergedReport'

        then:
        executedAndNotSkipped(":test")
        executedAndNotSkipped(":otherTest")
        htmlReport("build/reports/jacoco/mergedReport/html").totalCoverage() == 71
    }

    @Issue("GRADLE-2917")
    def "configures default jacoco dependencies even if the configuration was resolved before"() {
        expect:
        //dependencies task forces resolution of the configurations
        succeeds "dependencies", "test", "jacocoTestReport"
    }

    @Issue("GRADLE-3498")
    def "can use different execution data"() {
        setup:
        buildFile << """
        test {
            jacoco {
                destinationFile = file("\$buildDir/tmp/jacoco/jacocoTest.exec")
                classDumpDir = file("\$buildDir/tmp/jacoco/classpathdumps")
            }
        }

        jacocoTestReport {
            reports {
                xml.required = false
                csv.required = false
                html.outputLocation.set(file("\${buildDir}/reports/jacoco/integ"))
            }
            executionData test
        }
        """.stripIndent()

        when:
        succeeds 'test', 'jacocoTestReport'

        then:
        executedAndNotSkipped ':jacocoTestReport'
    }

    def "skips report task if all of the execution data files do not exist"() {
        given:
        buildFile << """
            jacocoTestReport {
                executionData.from = files('unknown.exec', 'data/test.exec')
            }
        """

        when:
        succeeds 'test', 'jacocoTestReport'

        then:
        executedAndNotSkipped ':test'
        skipped ':jacocoTestReport'
    }

    def "does not fail report task if some of the execution data files do not exist"() {
        given:
        def execFileName = 'unknown.exec'
        buildFile << """
            jacocoTestReport {
                executionData(files('$execFileName'))
            }
        """

        when:
        succeeds 'test', 'jacocoTestReport'

        then:
        executedAndNotSkipped(':test')
        executed(':jacocoTestReport')
        htmlReport().exists()
    }

    def "coverage data is aggregated from many tests"() {
        javaProjectUnderTest.writeSourceFiles(2000)

        expect:
        succeeds 'test', 'jacocoTestReport', '--info'
        htmlReport().totalCoverage() == 100
        htmlReport().numberOfClasses() == 2000
    }

    private JacocoReportFixture htmlReport(String basedir = "${REPORTING_BASE}/jacoco/test/html") {
        return new JacocoReportFixture(file(basedir))
    }
}
