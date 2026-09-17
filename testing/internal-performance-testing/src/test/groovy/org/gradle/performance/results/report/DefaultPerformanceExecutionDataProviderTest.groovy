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

import org.gradle.performance.ResultSpecification
import org.gradle.performance.measure.Duration
import org.gradle.performance.measure.MeasuredOperation
import org.gradle.performance.results.MeasuredOperationList
import org.gradle.performance.results.PerformanceReportScenario
import org.gradle.performance.results.PerformanceReportScenarioHistoryExecution
import org.gradle.performance.results.PerformanceTestExecutionResult

class DefaultPerformanceExecutionDataProviderTest extends ResultSpecification {

    private static final String COMMIT = 'commit-under-test'

    def 'identifies this pipeline\'s executions by build id (CI) or commit (local) and derives the measured verdict from them'() {
        given:
        // The result JSON no longer carries a build id - only the scenario identity (+ status, used by cross-build).
        def teamCityExecution = new PerformanceTestExecutionResult(scenarioName: 'x', scenarioClass: 'org.example.C', testProject: 'p', status: 'SUCCESS')
        // The DB has a regressed row produced by this pipeline's bucket, plus a row from an unrelated build/commit.
        def pipelineRow = regressedExecution('114134082', COMMIT)
        def foreignRow = regressedExecution('114123152', 'other-commit')

        when:
        def scenario = new PerformanceReportScenario([teamCityExecution], [pipelineRow, foreignRow], false, pipelineBuildIds as Set, currentCommit)

        then:
        scenario.currentExecutions*.teamCityBuildId == expectedCurrentBuildIds
        scenario.regressedByMeasurement == expectedRegressed
        // fromCache: this pipeline is known (CI) but none of its builds produced a measurement -> the bucket result
        // was restored from the build cache, and any carried-over status must not be shown as this chain's outcome.
        scenario.fromCache == expectedFromCache

        where:
        desc                  | pipelineBuildIds | currentCommit | expectedCurrentBuildIds | expectedRegressed | expectedFromCache
        'CI, fresh run'       | ['114134082']    | 'ignored'     | ['114134082']           | true              | false
        'CI, build-cache hit' | ['999999']       | 'ignored'     | []                      | false             | true
        'local, by commit'    | []               | COMMIT        | ['114134082']           | true              | false
    }

    def 'cross-version verdict comes from the DB, while cross-build still uses the recorded status'() {
        given:
        def failed = new PerformanceTestExecutionResult(scenarioName: 'x', scenarioClass: 'org.example.C', testProject: 'p', status: 'FAILURE')
        def row = regressedExecution('build-1', COMMIT)

        when:
        def scenario = new PerformanceReportScenario([failed], [row], crossBuild, ['build-1'] as Set, COMMIT)

        then:
        scenario.regressed == statusBasedRegressed              // status-based signal (used by the cross-build report)
        scenario.regressedByMeasurement == measurementRegressed // DB confidence (used by the cross-version report)

        where:
        crossBuild | statusBasedRegressed | measurementRegressed
        false      | true                 | true   // cross-version: both agree here
        true       | true                 | false  // cross-build: only the status-based signal fires (DB model is guarded off)
    }

    def 'a flaky scenario that regresses once but recovers on retry does not fail the build'() {
        given:
        // On CI a scenario is retried once (testRetry.maxRetries = 1), so a flaky scenario produces two current
        // executions under the same bucket build id: one that regressed and one that recovered. The build must only
        // fail when the regression holds across ALL of this pipeline's measurements.
        def teamCityExecution = new PerformanceTestExecutionResult(scenarioName: 'x', scenarioClass: 'org.example.C', testProject: 'p', status: 'SUCCESS')
        def regressedAttempt = regressedExecution('build-1', COMMIT)
        def recoveredAttempt = improvedExecution('build-1', COMMIT)

        when:
        def scenario = new PerformanceReportScenario([teamCityExecution], [regressedAttempt, recoveredAttempt], false, ['build-1'] as Set, COMMIT)

        then:
        scenario.currentExecutions.size() == 2
        regressedAttempt.regressedSignificantly()
        !recoveredAttempt.regressedSignificantly()
        !scenario.regressedByMeasurement // not every current execution regressed, so the gate stays green
    }

    def 'a scenario that regresses across every retry fails the build'() {
        given:
        def teamCityExecution = new PerformanceTestExecutionResult(scenarioName: 'x', scenarioClass: 'org.example.C', testProject: 'p', status: 'SUCCESS')
        def firstAttempt = regressedExecution('build-1', COMMIT)
        def retryAttempt = regressedExecution('build-1', COMMIT)

        when:
        def scenario = new PerformanceReportScenario([teamCityExecution], [firstAttempt, retryAttempt], false, ['build-1'] as Set, COMMIT)

        then:
        scenario.currentExecutions.size() == 2
        scenario.regressedByMeasurement
    }

    def 'a small regression below the test-failure criterion does not fail the build, even at high confidence'() {
        given:
        def teamCityExecution = new PerformanceTestExecutionResult(scenarioName: 'x', scenarioClass: 'org.example.C', testProject: 'p', status: 'SUCCESS')
        // 5 ms slower with perfectly separated samples: statistically very confident, but below the 10 ms minimum
        // measurable difference the test assertion requires - so the test itself PASSED. The report must not fail the
        // build on it either; it is only a NEARLY-FAILED warning. (Gating on the >90%-confidence display heuristic is
        // exactly what wrongly failed trigger build 115184380 with 9 nearly-failed scenarios.)
        def nearlyFailed = new PerformanceReportScenarioHistoryExecution(
            new Date().getTime(), 'build-1', COMMIT, millisOperationList([1000] * 10), millisOperationList([1005] * 10))

        when:
        def scenario = new PerformanceReportScenario([teamCityExecution], [nearlyFailed], false, ['build-1'] as Set, COMMIT)

        then:
        nearlyFailed.confidentToSayWorse()     // the display heuristic fires (NEARLY-FAILED tag)
        !nearlyFailed.regressedSignificantly() // but not the test-failure criterion
        !scenario.regressedByMeasurement       // so the build gate stays green
    }

    private PerformanceReportScenarioHistoryExecution regressedExecution(String teamCityBuildId, String commitId) {
        // current markedly slower than baseline (1s -> 2s, +100%): fails the test-failure criterion outright
        return new PerformanceReportScenarioHistoryExecution(new Date().getTime(), teamCityBuildId, commitId, measuredOperationList([1, 1, 1]), measuredOperationList([2, 2, 2]))
    }

    private PerformanceReportScenarioHistoryExecution improvedExecution(String teamCityBuildId, String commitId) {
        // current markedly faster than baseline (2s -> 1s, -50%): the opposite verdict, e.g. a passing retry
        return new PerformanceReportScenarioHistoryExecution(new Date().getTime(), teamCityBuildId, commitId, measuredOperationList([2, 2, 2]), measuredOperationList([1, 1, 1]))
    }

    private MeasuredOperationList millisOperationList(List<Integer> millis) {
        MeasuredOperationList ret = new MeasuredOperationList()
        ret.addAll(millis.collect { new MeasuredOperation(totalTime: Duration.millis(it)) })
        return ret
    }
}
