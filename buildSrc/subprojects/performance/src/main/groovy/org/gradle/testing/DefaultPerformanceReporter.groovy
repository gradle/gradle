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
import org.apache.commons.io.FileUtils
import org.gradle.api.Action
import org.gradle.api.internal.ProcessOperations
import org.gradle.process.JavaExecSpec
import org.openmbee.junit.JUnitMarshalling
import org.openmbee.junit.model.JUnitFailure
import org.openmbee.junit.model.JUnitTestCase
import org.openmbee.junit.model.JUnitTestSuite

import javax.inject.Inject
import java.nio.charset.Charset

/**
 * A reported which generates HTML performance report based on the JUnit XML.
 */
@CompileStatic
class DefaultPerformanceReporter implements PerformanceReporter {
    private static final String TC_URL = "https://builds.gradle.org/viewLog.html?buildId="

    File resultsJson

    String reportGeneratorClass

    ProcessOperations processOperations

    String projectName

    String githubToken

    @Inject
    DefaultPerformanceReporter(ProcessOperations processOperations) {
        this.processOperations = processOperations
    }

    @Override
    void report(PerformanceTest performanceTest) {
        generateResultsJson(performanceTest)

        processOperations.javaexec(new Action<JavaExecSpec>() {
            void execute(JavaExecSpec spec) {
                spec.setMain(reportGeneratorClass)
                spec.args(performanceTest.reportDir.path, resultsJson.path, projectName)
                spec.systemProperties(performanceTest.databaseParameters)
                spec.systemProperty("org.gradle.performance.execution.channel", performanceTest.channel)
                spec.systemProperty("org.gradle.performance.execution.branch", performanceTest.branchName)
                spec.systemProperty("githubToken", githubToken)
                spec.setClasspath(performanceTest.classpath)
            }
        })
    }

    protected void generateResultsJson(PerformanceTest performanceTest) {
        Collection<File> xmls = performanceTest.reports.junitXml.destination.listFiles().findAll { it.path.endsWith(".xml") }
        List<ScenarioBuildResultData> resultData = xmls
            .collect { JUnitMarshalling.unmarshalTestSuite(new FileInputStream(it)) }
            .collect { extractResultFromTestSuite(it, performanceTest) }
            .flatten() as List<ScenarioBuildResultData>
        FileUtils.write(resultsJson, JsonOutput.toJson(resultData), Charset.defaultCharset())
    }

    @TypeChecked(TypeCheckingMode.SKIP)
    static String collectFailures(JUnitTestSuite testSuite) {
        List<JUnitTestCase> testCases = testSuite.testCases ?: []
        List<JUnitFailure> failures = testCases.collect { it.failures ?: [] }.flatten()
        return failures.collect { it.value }.join("\n")
    }

    private static List<ScenarioBuildResultData> extractResultFromTestSuite(JUnitTestSuite testSuite, PerformanceTest performanceTest) {
        List<JUnitTestCase> testCases = testSuite.testCases ?: []
        return testCases.findAll { !it.skipped }.collect {
            new ScenarioBuildResultData(scenarioName: it.name,
                webUrl: TC_URL + performanceTest.buildId,
                status: (it.errors || it.failures) ? "FAILURE" : "SUCCESS",
                testFailure: collectFailures(testSuite))
        }
    }
}
