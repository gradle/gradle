/*
 * Copyright 2018 the original author or authors.
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
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.process.JavaExecSpec
import org.openmbee.junit.model.JUnitFailure
import org.openmbee.junit.model.JUnitTestCase
import org.openmbee.junit.model.JUnitTestSuite

@CompileStatic
abstract class ReportGenerationPerformanceTest extends PerformanceTest {
    @Internal
    String buildId

    @OutputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    File reportDir

    protected abstract List<ScenarioBuildResultData> generateResultsForReport()

    protected void generatePerformanceReport() {
        File resultJson = generateResultJson()
        project.delete(reportDir)
        try {
            project.javaexec(new Action<JavaExecSpec>() {
                void execute(JavaExecSpec spec) {
                    spec.setMain("org.gradle.performance.results.ReportGenerator")
                    spec.args(resultStoreClass, reportDir.path, resultJson.path)
                    spec.systemProperties(databaseParameters)
                    spec.systemProperty("org.gradle.performance.execution.channel", channel)
                    spec.setClasspath(ReportGenerationPerformanceTest.this.getClasspath())
                }
            })
        } finally {
            project.delete(resultJson)
        }
    }

    private File generateResultJson() {
        File resultJson = File.createTempFile('performanceTest', 'results.json')
        resultJson.text = JsonOutput.toJson(generateResultsForReport())
        return resultJson
    }

    @TypeChecked(TypeCheckingMode.SKIP)
    protected String collectFailures(JUnitTestSuite testSuite) {
        List<JUnitTestCase> testCases = testSuite.testCases ?: []
        List<JUnitFailure> failures = testCases.collect { it.failures ?: [] }.flatten()
        return failures.collect { it.value }.join("\n")
    }

    // Modify this class with care, see class org.gradle.performance.results.ScenarioBuildResultData
    static class ScenarioBuildResultData {
        String teamCityBuildId
        String scenarioName
        String webUrl
        String testFailure
        // SUCCESS/FAILURE/UNKNOWN
        String status
    }
}
