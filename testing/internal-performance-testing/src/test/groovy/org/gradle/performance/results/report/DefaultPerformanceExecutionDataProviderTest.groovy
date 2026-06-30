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

package org.gradle.performance.results.report

import groovy.json.JsonOutput
import org.gradle.performance.ResultSpecification
import org.gradle.performance.results.MeasuredOperationList
import org.gradle.performance.results.PerformanceReportScenario
import org.gradle.performance.results.PerformanceReportScenarioHistoryExecution
import org.gradle.performance.results.PerformanceTestHistory
import org.gradle.performance.results.ResultsStore
import org.gradle.performance.results.PerformanceTestExecutionResult
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Subject

class DefaultPerformanceExecutionDataProviderTest extends ResultSpecification {
    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())

    @Subject
    DefaultPerformanceExecutionDataProvider provider

    File resultsJson = tmpDir.file('results.json')
    ResultsStore mockStore = Mock(ResultsStore)

    def setup() {
        resultsJson << '[]'
        provider = new DefaultPerformanceExecutionDataProvider(mockStore, [resultsJson], [] as Set)
    }

    def 'can sort scenarios correctly'() {
        when:
        List buildResults = [
            createLowConfidenceRegressedData(),
            createLowConfidenceImprovedData(),
            createHighConfidenceImprovedData(),
            createHighConfidenceRegressedData(),
            createFailedData()
        ]
        buildResults.sort(DefaultPerformanceExecutionDataProvider.SCENARIO_COMPARATOR)

        then:
        buildResults.collect { it.scenarioName } == ['failed', 'highConfidenceRegressed', 'lowConfidenceRegressed', 'lowConfidenceImproved', 'highConfidenceImproved']
    }

    def 'marks a scenario as carried over from cache when none of its executions ran in the current pipeline'() {
        given:
        def store = Mock(ResultsStore) {
            getTestResults(_, _, _, _ as List, _) >> Stub(PerformanceTestHistory) {
                getExecutions() >> []
            }
        }
        resultsJson.text = JsonOutput.toJson([[
            teamCityBuildId: jsonBuildId,
            scenarioName   : 'assemble',
            scenarioClass  : 'org.example.SomeTest',
            testProject    : 'largeMonolithicJavaProject',
            status         : 'FAILURE',
            webUrl         : "https://builds.gradle.org/viewLog.html?buildId=$jsonBuildId"
        ]])
        provider = new DefaultPerformanceExecutionDataProvider(store, [resultsJson], currentPipelineBuildIds as Set)

        expect:
        provider.reportScenarios.first().fromCache == expectedFromCache

        where:
        // A cache hit restores the bucket task output, whose JSON carries the *original* producing build's id.
        scenario       | jsonBuildId | currentPipelineBuildIds | expectedFromCache
        'cache hit'    | '114123152' | ['114134082']           | true   // stale build id, not one of this pipeline's buckets
        'fresh run'    | '114134082' | ['114134082']           | false  // produced by this pipeline
        'unknown (local)' | '114123152' | []                   | false  // no authoritative set -> never suppress
    }

    private PerformanceReportScenario createFailedData() {
        return new PerformanceReportScenario(
            [new PerformanceTestExecutionResult(scenarioName: 'failed', status: 'FAILURE')],
            [Mock(PerformanceReportScenarioHistoryExecution)],
            false,
            false
        )
    }

    private PerformanceReportScenario createHighConfidenceImprovedData() {
        // 95% confidence -50% difference
        return createResult('highConfidenceImproved', [2, 2, 2], [1, 1, 1])
    }

    private PerformanceReportScenario createLowConfidenceImprovedData() {
        // 68% confidence -50% difference
        return createResult('lowConfidenceImproved', [2], [1])
    }

    private PerformanceReportScenario createLowConfidenceRegressedData() {
        // 68% confidence 100% difference
        return createResult("lowConfidenceRegressed", [1], [2])
    }

    private PerformanceReportScenario createHighConfidenceRegressedData() {
        // 91% confidence 100% difference
        return createResult('highConfidenceRegressed', [1, 1, 1, 2], [2, 2, 2, 2])
    }

    private PerformanceReportScenario createResult(String name, List<Integer> baseVersionResult, List<Integer> currentVersionResult) {
        MeasuredOperationList baseVersion = measuredOperationList(baseVersionResult)
        MeasuredOperationList currentVersion = measuredOperationList(currentVersionResult)
        PerformanceReportScenarioHistoryExecution historyExecution = new PerformanceReportScenarioHistoryExecution(new Date().getTime(), 'teamCityBuild', '', baseVersion, currentVersion)
        PerformanceTestExecutionResult teamCityExecution = new PerformanceTestExecutionResult(scenarioName: name, status: 'SUCCESS', teamCityBuildId: 'teamCityBuild')
        return new PerformanceReportScenario([teamCityExecution], [historyExecution], false, false)
    }
}
