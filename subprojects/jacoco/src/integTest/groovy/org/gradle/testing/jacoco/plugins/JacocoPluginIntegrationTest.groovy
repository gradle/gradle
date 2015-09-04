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
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import spock.lang.IgnoreIf
import spock.lang.Issue

class JacocoPluginIntegrationTest extends AbstractIntegrationSpec {

    private static final String REPORTING_BASE = "${Project.DEFAULT_BUILD_DIR_NAME}/${ReportingExtension.DEFAULT_REPORTS_DIR_NAME}"
    private static final String REPORT_HTML_DEFAULT_PATH = "${REPORTING_BASE}/jacoco/test/html/index.html"
    private static final String REPORT_XML_DEFAULT_PATH = "${REPORTING_BASE}/jacoco/test/jacocoTestReport.xml"
    private static final String REPORT_CSV_DEFAULT_REPORT = "${REPORTING_BASE}/jacoco/test/jacocoTestReport.csv"

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
        createTestFiles()
    }

    def "dependencies report shows default jacoco dependencies"() {
        when: succeeds("dependencies", "--configuration", "jacocoAgent")
        then: output.contains "org.jacoco:org.jacoco.agent:"

        when: succeeds("dependencies", "--configuration", "jacocoAnt")
        then: output.contains "org.jacoco:org.jacoco.ant:"
    }

    void "allows configuring tool dependencies explicitly"() {
        when:
        buildFile << """
            dependencies {
                //downgrade version:
                jacocoAgent "org.jacoco:org.jacoco.agent:0.6.0.201210061924"
                jacocoAnt "org.jacoco:org.jacoco.ant:0.6.0.201210061924"
            }
        """

        succeeds("dependencies", "--configuration", "jacocoAgent")
        then: output.contains "org.jacoco:org.jacoco.agent:0.6.0.201210061924"

        when: succeeds("dependencies", "--configuration", "jacocoAnt")
        then: output.contains "org.jacoco:org.jacoco.ant:0.6.0.201210061924"
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
                    html.destination "\${buildDir}/jacocoHtml"
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

    @IgnoreIf({GradleContextualExecuter.parallel})
    void jacocoReportIsIncremental() {
        def reportResourceDir = file("${REPORTING_BASE}/jacoco/test/html/.resources")

        when:
        succeeds('test', 'jacocoTestReport')

        then:
        htmlReport().exists()
        reportResourceDir.exists()

        when:
        succeeds('jacocoTestReport')

        then:
        skippedTasks.contains(":jacocoTestReport")
        htmlReport().exists()
        reportResourceDir.exists()

        when:
        reportResourceDir.deleteDir()
        succeeds('test', 'jacocoTestReport')

        then:
        !skippedTasks.contains(":jacocoTestReport")
        htmlReport().exists()
        reportResourceDir.exists()
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
    Thing() { System.out.println("hi"); }
    Thing(String msg) { System.out.println(msg); }
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
                testSrcDirs = sourceSets.otherTest.java.srcDirs as List
                testClassesDir = sourceSets.otherTest.output.classesDir
                classpath = sourceSets.otherTest.runtimeClasspath
            }

            task jacocoMerge(type: JacocoMerge) {
                executionData test, otherTests
            }

            task mergedReport(type: JacocoReport) {
                executionData jacocoMerge.destinationFile
                dependsOn jacocoMerge
                sourceDirectories = files(sourceSets.main.java.srcDirs, sourceSets.otherMain.java.srcDirs)
                classDirectories = files(sourceSets.main.output.classesDir, sourceSets.otherMain.output.classesDir)
            }
        """
        when:
        succeeds 'mergedReport'

        then:
        ":jacocoMerge" in nonSkippedTasks
        ":test" in nonSkippedTasks
        ":otherTests" in nonSkippedTasks
        file("build/jacoco/jacocoMerge.exec").exists()
        htmlReport("build/reports/jacoco/mergedReport/html").totalCoverage() == 65
    }

    @Issue("GRADLE-2917")
    void "configures default jacoco dependencies even if the configuration was resolved before"() {
        expect:
        //dependencies task forces resolution of the configurations
        succeeds "dependencies", "test", "jacocoTestReport"
    }

    private JacocoReportFixture htmlReport(String basedir = "${REPORTING_BASE}/jacoco/test/html") {
        return new JacocoReportFixture(file(basedir))
    }

    private void createTestFiles() {
        file("src/main/java/org/gradle/Class1.java") <<
                "package org.gradle; public class Class1 { public boolean isFoo(Object arg) { return true; } }"
        file("src/test/java/org/gradle/Class1Test.java") <<
                "package org.gradle; import org.junit.Test; public class Class1Test { @Test public void someTest() { new Class1().isFoo(\"test\"); } }"
    }
}

