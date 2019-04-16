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

package org.gradle.performance.results

import org.gradle.ci.common.model.FlakyTest
import org.gradle.ci.github.GitHubIssuesClient
import org.gradle.ci.tagging.flaky.KnownFlakyTestProvider
import org.kohsuke.github.GHIssue
import org.kohsuke.github.GHIssueState
import spock.lang.Specification
import spock.lang.Subject

import static org.gradle.ci.github.GitHubIssuesClient.CI_TRACKED_FLAKINESS_LABEL
import static DefaultPerformanceFlakinessAnalyzer.GITHUB_FIX_IT_LABEL
import static DefaultPerformanceFlakinessAnalyzer.GITHUB_IN_PERFORMANCE_LABEL

class DefaultPerformanceFlakinessAnalyzerTest extends Specification {
    GitHubIssuesClient issuesClient = Mock(GitHubIssuesClient)
    KnownFlakyTestProvider flakyTestProvider = Mock(KnownFlakyTestProvider)

    @Subject
    DefaultPerformanceFlakinessAnalyzer reporter = new DefaultPerformanceFlakinessAnalyzer(issuesClient, flakyTestProvider)

    ScenarioBuildResultData scenario = new ScenarioBuildResultData(
        scenarioName: 'myScenario',
        scenarioClass: 'my.AwesomeClass',
        webUrl: 'myUrl',
        agentName: 'myAgent',
        agentUrl: 'myAgentUrl',
        currentBuildExecutions: [
            new MockExecutionData(100, 1),
            new MockExecutionData(98, -1)
        ]
    )

    def 'known flaky issue gets commented, reopened and labeled as fix-it'() {
        given:
        GHIssue issue = Mock(GHIssue)
        1 * flakyTestProvider.knownInvalidFailures >> [new FlakyTest(name: 'my.AwesomeClass.otherScenario'), new FlakyTest(name: 'my.AwesomeClass.myScenario', issue: issue)]
        1 * issue.state >> GHIssueState.CLOSED
        1 * issue.labels >> []

        when:
        reporter.report(scenario)

        then:
        1 * issue.reopen()
        1 * issue.addLabels(GITHUB_FIX_IT_LABEL)
        1 * issue.comment("""
FROM-BOT

Coordinator url: https://builds.gradle.org/viewLog.html?buildId=${System.getenv("BUILD_ID")}
Worker url: myUrl
Agent: [myAgent](myAgentUrl)
Details:

| Iteration | Difference | Confidence |
|---|---|---|
|1|1.0 %|100.0%|
|2|-1.0 %|98.0%|
""")
    }

    def 'new issue is created if none found'() {
        given:
        GHIssue issue = Mock(GHIssue)
        1 * flakyTestProvider.knownInvalidFailures >> [new FlakyTest(name: 'otherScenario')]

        when:
        reporter.report(scenario)

        then:
        1 * issuesClient.createBuildToolInvalidFailureIssue('Flaky performance test: my.AwesomeClass.myScenario',
            """
FROM-BOT

TEST_NAME: my.AwesomeClass.myScenario

MESSAGE: we're slower than
"""
            , [CI_TRACKED_FLAKINESS_LABEL, GITHUB_IN_PERFORMANCE_LABEL]) >> issue
        1 * issue.comment("""
FROM-BOT

Coordinator url: https://builds.gradle.org/viewLog.html?buildId=${System.getenv("BUILD_ID")}
Worker url: myUrl
Agent: [myAgent](myAgentUrl)
Details:

| Iteration | Difference | Confidence |
|---|---|---|
|1|1.0 %|100.0%|
|2|-1.0 %|98.0%|
""")
    }

    class MockExecutionData extends ScenarioBuildResultData.ExecutionData {
        double confidencePercentage
        double differencePercentage

        MockExecutionData(double confidencePercentage, double differencePercentage) {
            super(System.currentTimeMillis(), "commitId", null, null)
            this.confidencePercentage = confidencePercentage
            this.differencePercentage = differencePercentage
        }

        @Override
        String getDifferenceDisplay() {
            return "${differencePercentage} %"
        }
    }
}
