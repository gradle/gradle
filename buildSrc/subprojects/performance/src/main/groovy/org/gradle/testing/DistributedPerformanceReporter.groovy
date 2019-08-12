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

import com.google.common.annotations.VisibleForTesting
import groovy.json.JsonSlurper
import groovy.transform.TypeChecked
import groovy.transform.TypeCheckingMode
import org.gradle.api.internal.tasks.testing.junit.result.TestClassResult
import org.gradle.api.internal.tasks.testing.junit.result.TestResultSerializer

import java.util.concurrent.atomic.AtomicLong

class DistributedPerformanceReporter extends PerformanceReporter {
    DistributedPerformanceReporter(PerformanceTest performanceTest) {
        super(performanceTest)
    }

    private DistributedPerformanceTest getDistributedPerformanceTest() {
        return (DistributedPerformanceTest) performanceTest
    }

    @Override
    void report() {
        if (isGeneratingScenarioList()) {
            // do nothing
        } else if (!distributedPerformanceTest.isRerun()) {
            // first run, only write report when it succeeds
            if (allWorkerBuildsAreSuccessful()) {
                super.report()
            } else {
                writeBinaryResults()
                generateResultsJson()
            }
        } else {
            super.report()
        }
    }

    boolean isGeneratingScenarioList() {
        return distributedPerformanceTest.finishedBuilds.isEmpty()
    }

    boolean allWorkerBuildsAreSuccessful() {
        return distributedPerformanceTest.finishedBuilds.values().every { it.successful }
    }

    /**
     * This is for tagging plugin. See https://github.com/gradle/ci-health/blob/3e30ea146f594ee54a4efe4384f933534b40739c/gradle-build-tag-plugin/src/main/groovy/org/gradle/ci/tagging/plugin/TagSingleBuildPlugin.groovy
     */
    @VisibleForTesting
    void writeBinaryResults() {
        AtomicLong counter = new AtomicLong()
        Map<String, List<DistributedPerformanceTest.ScenarioResult>> classNameToScenarioNames = distributedPerformanceTest.finishedBuilds.values().findAll { it.testClassFullName != null }.groupBy {
            it.testClassFullName
        }
        List<TestClassResult> classResults = classNameToScenarioNames.entrySet().collect { Map.Entry<String, List<DistributedPerformanceTest.ScenarioResult>> entry ->
            TestClassResult classResult = new TestClassResult(counter.incrementAndGet(), entry.key, 0L)
            entry.value.each { DistributedPerformanceTest.ScenarioResult scenarioResult ->
                classResult.add(scenarioResult.toMethodResult(counter))
            }
            classResult
        }

        new TestResultSerializer(distributedPerformanceTest.binResultsDir).write(classResults)
    }

    @Override
    protected List<ScenarioBuildResultData> generateResultsForReport() {
        if (!distributedPerformanceTest.isRerun()) {
            return getResultsFromCurrentRun()
        } else {
            return getResultsFromCurrentRun() + getResultsFromoPreviousRun()
        }
    }

    private List<ScenarioBuildResultData> getResultsFromoPreviousRun() {
        return resultsJson.isFile() ? ((List<Map>) new JsonSlurper().parseText(resultsJson.text)).collect { new ScenarioBuildResultData(it) } : []
    }

    @TypeChecked(TypeCheckingMode.SKIP)
    private List<ScenarioBuildResultData> getResultsFromCurrentRun() {
        return distributedPerformanceTest.finishedBuilds.collect { workerBuildId, scenarioResult ->
            new ScenarioBuildResultData(
                teamCityBuildId: workerBuildId,
                scenarioName: distributedPerformanceTest.scheduledBuilds.get(workerBuildId).id,
                scenarioClass: scenarioResult.testClassFullName,
                webUrl: scenarioResult.buildResponse.webUrl,
                status: scenarioResult.buildResponse.status,
                agentName: scenarioResult.buildResponse.agent.name,
                agentUrl: scenarioResult.buildResponse.agent.webUrl,
                testFailure: scenarioResult.failureText)
        }
    }
}
