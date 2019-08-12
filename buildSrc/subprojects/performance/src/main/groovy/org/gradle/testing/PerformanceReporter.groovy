/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.testing

import groovy.json.JsonOutput
import groovy.transform.CompileStatic
import groovy.transform.TypeChecked
import groovy.transform.TypeCheckingMode
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.process.JavaExecSpec
import org.openmbee.junit.JUnitMarshalling
import org.openmbee.junit.model.JUnitFailure
import org.openmbee.junit.model.JUnitTestCase
import org.openmbee.junit.model.JUnitTestSuite

/**
 * A reported which generates HTML performance report based on the JUnit XML.
 */
@CompileStatic
class PerformanceReporter {
    private static final String TC_URL = "https://builds.gradle.org/viewLog.html?buildId="

    File resultsJson

    String reportGeneratorClass = "org.gradle.performance.results.DefaultReportGenerator"

    PerformanceTest performanceTest

    // Disabled by default, we don't need it in worker build - unless it's AdHoc performance test
    boolean enabled = false

    PerformanceReporter(PerformanceTest performanceTest) {
        this.performanceTest = performanceTest
    }

    void report() {
        if (!enabled) {
            println("Skipping reporting because current reported is disabled")
        }
        Project project = performanceTest.project
        generateResultsJson()

        performanceTest.project.javaexec(new Action<JavaExecSpec>() {
            void execute(JavaExecSpec spec) {
                spec.setMain(reportGeneratorClass)
                spec.args(performanceTest.reportDir.path, resultsJson.path, project.getName())
                spec.systemProperties(performanceTest.databaseParameters)
                spec.systemProperty("org.gradle.performance.execution.channel", performanceTest.channel)
                spec.systemProperty("org.gradle.performance.execution.branch", performanceTest.branchName)
                spec.systemProperty("githubToken", project.findProperty("githubToken"))
                spec.setClasspath(performanceTest.classpath)
            }
        })
    }

    protected void generateResultsJson() {
        resultsJson.createNewFile()
        resultsJson.text = JsonOutput.toJson(generateResultsForReport())
    }

    protected List<ScenarioBuildResultData> generateResultsForReport() {
        Collection<File> xmls = performanceTest.reports.junitXml.destination.listFiles().findAll { it.path.endsWith(".xml") }
        List<JUnitTestSuite> testSuites = xmls.collect { JUnitMarshalling.unmarshalTestSuite(new FileInputStream(it)) }
        return testSuites.collect { extractResultFromTestSuite(it) }.flatten() as List<ScenarioBuildResultData>
    }


    @TypeChecked(TypeCheckingMode.SKIP)
    protected static String collectFailures(JUnitTestSuite testSuite) {
        List<JUnitTestCase> testCases = testSuite.testCases ?: []
        List<JUnitFailure> failures = testCases.collect { it.failures ?: [] }.flatten()
        return failures.collect { it.value }.join("\n")
    }

    private List<ScenarioBuildResultData> extractResultFromTestSuite(JUnitTestSuite testSuite) {
        List<JUnitTestCase> testCases = testSuite.testCases ?: []
        return testCases.findAll { !it.skipped }.collect {
            new ScenarioBuildResultData(scenarioName: it.name,
                webUrl: TC_URL + performanceTest.buildId,
                status: (it.errors || it.failures) ? "FAILURE" : "SUCCESS",
                testFailure: collectFailures(testSuite))
        }
    }
}
