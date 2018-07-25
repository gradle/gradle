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
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.testing.jacoco.plugins.fixtures.JacocoCoverage
import spock.lang.IgnoreIf
import spock.lang.Issue

@TargetCoverage({ JacocoCoverage.DEFAULT_COVERAGE })
class JacocoPluginMultiVersionIntegrationTest extends JacocoMultiVersionIntegrationTest {

    private static final String REPORTING_BASE = "${Project.DEFAULT_BUILD_DIR_NAME}/${ReportingExtension.DEFAULT_REPORTS_DIR_NAME}"
    private static final String REPORT_HTML_DEFAULT_PATH = "${REPORTING_BASE}/jacoco/test/html/index.html"
    private static final String REPORT_XML_DEFAULT_PATH = "${REPORTING_BASE}/jacoco/test/jacocoTestReport.xml"
    private static final String REPORT_CSV_DEFAULT_REPORT = "${REPORTING_BASE}/jacoco/test/jacocoTestReport.csv"

    def setup() {
        javaProjectUnderTest.writeSourceFiles()
    }

    void generatesHtmlReportOnlyAsDefault() {
        when:
        succeeds('test', 'jacocoTestReport')

        then:
        file(REPORTING_BASE).listFiles().collect { it.name } as Set == ["jacoco", "tests"] as Set
        file(REPORT_HTML_DEFAULT_PATH).exists()
        file("${REPORTING_BASE}/jacoco/test").listFiles().collect { it.name } == ["html"]
        file("${REPORTING_BASE}/jacoco/test/html/org.gradle/Class1.java.html").exists()
        htmlReport().totalCoverage() == 100
    }

    void canConfigureReportsInJacocoTestReport() {
        given:
        buildFile << """
            jacocoTestReport {
                reports {
                    xml.enabled true
                    csv.enabled true
                    html.destination file("\${buildDir}/jacocoHtml")
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

    void respectsReportingBaseDir() {
        given:
        buildFile << """
            jacocoTestReport {
                reports.xml.enabled = true
                reports.csv.enabled = true
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

    void canConfigureReportDirectory() {
        given:
        def customReportDirectory = "customJacocoReportDir"
        buildFile << """
            jacocoTestReport {
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
        htmlReport("build/${customReportDirectory}/test/html").totalCoverage() == 100
        file("build/${customReportDirectory}/test/jacocoTestReport.xml").exists()
        file("build/${customReportDirectory}/test/jacocoTestReport.csv").exists()
    }

    void jacocoTestReportIsSkippedIfNoCoverageDataAvailable() {
        when:
        def executionResult = succeeds('jacocoTestReport')
        then:
        executionResult.assertTaskSkipped(':jacocoTestReport')
    }

    @IgnoreIf({GradleContextualExecuter.parallel})
    void canUseCoverageDataFromPreviousRunForCoverageReport() {
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
        htmlReport().totalCoverage() == 100
    }

    @IgnoreIf({GradleContextualExecuter.parallel})
    void canMergeCoverageData() {
        given:
        file("src/otherMain/java/Thing.java") << """
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
            sourceSets {
                otherMain
                otherTest
            }
            sourceSets.otherTest.compileClasspath = configurations.testCompile + sourceSets.otherMain.output
            sourceSets.otherTest.runtimeClasspath = sourceSets.otherTest.compileClasspath + sourceSets.otherTest.output

            task otherTests(type: Test) {
                binResultsDir file("bin")
                testClassesDirs = sourceSets.otherTest.output.classesDirs
                classpath = sourceSets.otherTest.runtimeClasspath
            }

            task jacocoMerge(type: JacocoMerge) {
                executionData test, otherTests
            }

            task mergedReport(type: JacocoReport) {
                executionData jacocoMerge.destinationFile
                dependsOn jacocoMerge
                sourceDirectories = files(sourceSets.main.java.srcDirs, sourceSets.otherMain.java.srcDirs)
                classDirectories = files(sourceSets.main.output.classesDirs, sourceSets.otherMain.output.classesDirs)
            }
        """
        when:
        succeeds 'mergedReport'

        then:
        ":jacocoMerge" in nonSkippedTasks
        ":test" in nonSkippedTasks
        ":otherTests" in nonSkippedTasks
        file("build/jacoco/jacocoMerge.exec").exists()
        htmlReport("build/reports/jacoco/mergedReport/html").totalCoverage() == 71
    }

    @Issue("GRADLE-2917")
    void "configures default jacoco dependencies even if the configuration was resolved before"() {
        expect:
        //dependencies task forces resolution of the configurations
        succeeds "dependencies", "test", "jacocoTestReport"
    }

    @Issue("GRADLE-3498")
    void "can use different execution data"() {
        setup:
        buildFile << """
        test {
            jacoco {
                append = false
                destinationFile = file("\$buildDir/tmp/jacoco/jacocoTest.exec")
                classDumpDir = file("\$buildDir/tmp/jacoco/classpathdumps")
            }
        }

        jacocoTestReport {
            reports {
                xml.enabled false
                csv.enabled false
                html.destination file("\${buildDir}/reports/jacoco/integ")
            }
            executionData test
        }
        """.stripIndent()

        when:
        succeeds 'test', 'jacocoTestReport'

        then:
        ':jacocoTestReport' in nonSkippedTasks
    }

    def "skips report task if none of the execution data files does not exist"() {
        given:
        buildFile << """
            jacocoTestReport {
                executionData = files('unknown.exec', 'data/test.exec')
            }
        """

        when:
        succeeds 'test', 'jacocoTestReport'

        then:
        ':test' in nonSkippedTasks
        ':jacocoTestReport' in skippedTasks
    }

    def "fails report task if only some of the execution data files do not exist"() {
        given:
        def execFileName = 'unknown.exec'
        buildFile << """
            jacocoTestReport {
                executionData(files('$execFileName'))
            }
        """

        when:
        fails 'test', 'jacocoTestReport'

        then:
        ':test' in nonSkippedTasks
        ':jacocoTestReport' in executedTasks
        failure.assertHasCause("Unable to read execution data file ${new File(testDirectory, execFileName)}")
    }

    private JacocoReportFixture htmlReport(String basedir = "${REPORTING_BASE}/jacoco/test/html") {
        return new JacocoReportFixture(file(basedir))
    }
}
