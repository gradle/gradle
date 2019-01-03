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


import groovy.transform.CompileStatic
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.TaskAction
import org.openmbee.junit.JUnitMarshalling
import org.openmbee.junit.model.JUnitTestCase
import org.openmbee.junit.model.JUnitTestSuite

@CompileStatic
@CacheableTask
class BuildScanPerformanceTest extends ReportGenerationPerformanceTest {
    private static final String TC_URL = "https://builds.gradle.org/viewLog.html?buildId="

    @Override
    String getResultStoreClass() {
        return "org.gradle.performance.results.BuildScanResultsStore"
    }

    @Override
    String getChannel() {
        return "commits"
    }

    @Override
    File getReportDir() {
        return new File(project.getBuildDir(), "reports/performance")
    }

    @TaskAction
    @Override
    void executeTests() {
        try {
            super.executeTests()
        } finally {
            generatePerformanceReport()
        }
    }

    @Override
    protected List<ScenarioBuildResultData> generateResultsForReport() {
        Collection<File> xmls = reports.junitXml.destination.listFiles().findAll { it.path.endsWith(".xml") }
        List<JUnitTestSuite> testSuites = xmls.collect { JUnitMarshalling.unmarshalTestSuite(new FileInputStream(it)) }
        return testSuites.collect { extractResultFromTestSuite(it) }.flatten() as List<ScenarioBuildResultData>
    }

    private List<ScenarioBuildResultData> extractResultFromTestSuite(JUnitTestSuite testSuite) {
        List<JUnitTestCase> testCases = testSuite.testCases ?: []
        return testCases.collect {
            new ScenarioBuildResultData(scenarioName: it.name,
                webUrl: TC_URL + buildId,
                status: (it.errors || it.failures) ? "FAILURE" : "SUCCESS",
                testFailure: collectFailures(testSuite))
        }
    }
}
