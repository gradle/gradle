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

package org.gradle.performance.results

import groovy.json.JsonOutput
import org.gradle.performance.util.Git
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.jsoup.Jsoup
import org.junit.Rule
import spock.lang.Specification
import spock.lang.Subject
import org.jsoup.nodes.Document

class IndexPageGeneratorTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()

    @Subject
    IndexPageGenerator generator

    File resultsJson = tmpDir.file('results.json')

    String currentGitCommitId = Git.current().commitId

    String regressionOutput(double regressionPercentage) {
        """
${BaselineVersion.MACHINE_DATA_SEPARATOR}
${JsonOutput.toJson([regressionPercentage: regressionPercentage])}
${BaselineVersion.MACHINE_DATA_SEPARATOR}
        """
    }

    def 'can sort scenarios correctly'() {
        when:
        resultsJson.text = JsonOutput.toJson([
            new ScenarioBuildResultData(scenarioName: 'no regression', webUrl: 'no regression url', successful: true),
            new ScenarioBuildResultData(scenarioName: 'small regression', webUrl: 'small regression url', successful: false, result: regressionOutput(1.0)),
            new ScenarioBuildResultData(scenarioName: 'big regression', webUrl: 'big regression url', successful: false, result: regressionOutput(10.0)),
            new ScenarioBuildResultData(scenarioName: 'build failure', webUrl: 'build failure url', successful: false),
        ])
        generator = new IndexPageGenerator(resultsJson)

        then:
        generator.readBuildResultData(resultsJson).toList().collect { it.scenarioName } == ['build failure', 'big regression', 'small regression', 'no regression']
    }

    def 'can render index.html correctly'() {
        StringWriter sw = new StringWriter()
        resultsJson.text = JsonOutput.toJson([successBuild(), regressedBuild(), failedBuild()])

        given:
        generator = new IndexPageGenerator(resultsJson)

        when:
        generator.render(Mock(ResultsStore), sw)
        def document = Jsoup.parse(sw.toString())

        then:
        document.select("tr").size() == 7
        document.select("tr.danger").size() == 1
        document.select("tr.warning").size() == 3
        document.select("tr.success").size() == 2

        document.select('tr')[1].text().count('N/A') == 8
        document.select('tr')[1].html().contains('failedBuildUrl')

        ['rowspan="3"', 'regressedBuildUrl', 'regressedControlGroup', 'regressedExperimentGroup', 'regressedControlGroupMedian1',
         'regressedExperimentGroupMedian1', 'regressedControlGroupSe1', 'regressedExperimentGroupSe1', 'regressedConfidence1', '1.00%'].every { getNthRow(document, 2).contains(it) }

        ['regressedControlGroup', 'regressedExperimentGroup', 'regressedControlGroupMedian2',
         'regressedExperimentGroupMedian2', 'regressedControlGroupSe2', 'regressedExperimentGroupSe2', 'regressedConfidence2', '2.00%'].every { getNthRow(document, 3).contains(it) }

        ['regressedControlGroup', 'regressedExperimentGroup', 'regressedControlGroupMedian3',
         'regressedExperimentGroupMedian3', 'regressedControlGroupSe3', 'regressedExperimentGroupSe3', 'regressedConfidence3', '3.00%'].every { getNthRow(document, 4).contains(it) }

        ['rowspan="2"', 'successBuildUrl', 'successControlGroup', 'successExperimentGroup', 'successControlGroupMedian1',
         'successExperimentGroupMedian1', 'successControlGroupSe1', 'successExperimentGroupSe1', 'successConfidence1', '-1.00%',
        'original build', 'builds.gradle.org/viewLog.html?buildId=successBuildId'].every { getNthRow(document, 5).contains(it) }

        ['successControlGroup', 'successExperimentGroup', 'successControlGroupMedian2',
         'successExperimentGroupMedian2', 'successControlGroupSe2', 'successExperimentGroupSe2', 'successConfidence2', '-2.00%'].every { getNthRow(document, 6).contains(it) }

    }

    String getNthRow(Document document, int n) {
        document.select('tr')[n].html()
    }

    ScenarioBuildResultData successBuild() {
        return new ScenarioBuildResultData(
            scenarioName: 'successBuild',
            webUrl: 'successBuildUrl',
            successful: true,
            experimentData: [
                new ScenarioBuildResultData.ExperimentData(
                    buildId: 'successBuildId',
                    gitCommitId: 'anotherCommitId',
                    controlGroupName: 'successControlGroup',
                    experimentGroupName: 'successExperimentGroup',
                    controlGroupMedian: 'successControlGroupMedian1',
                    experimentGroupMedian: 'successExperimentGroupMedian1',
                    controlGroupStandardError: 'successControlGroupSe1',
                    experimentGroupStandardError: 'successExperimentGroupSe1',
                    confidence: 'successConfidence1',
                    regressionPercentage: -1.0
                ),
                new ScenarioBuildResultData.ExperimentData(
                    buildId: 'successBuildId',
                    gitCommitId: 'anotherCommitId',
                    controlGroupName: 'successControlGroup',
                    experimentGroupName: 'successExperimentGroup',
                    controlGroupMedian: 'successControlGroupMedian2',
                    experimentGroupMedian: 'successExperimentGroupMedian2',
                    controlGroupStandardError: 'successControlGroupSe2',
                    experimentGroupStandardError: 'successExperimentGroupSe2',
                    confidence: 'successConfidence2',
                    regressionPercentage: -2.0
                )
            ]
        )
    }

    ScenarioBuildResultData regressedBuild() {
        return new ScenarioBuildResultData(
            scenarioName: 'regressedBuild',
            webUrl: 'regressedBuildUrl',
            successful: false,
            experimentData: [
                new ScenarioBuildResultData.ExperimentData(
                    buildId: 'regressedBuildId',
                    gitCommitId: currentGitCommitId,
                    controlGroupName: 'regressedControlGroup',
                    experimentGroupName: 'regressedExperimentGroup',
                    controlGroupMedian: 'regressedControlGroupMedian1',
                    experimentGroupMedian: 'regressedExperimentGroupMedian1',
                    controlGroupStandardError: 'regressedControlGroupSe1',
                    experimentGroupStandardError: 'regressedExperimentGroupSe1',
                    confidence: 'regressedConfidence1',
                    regressionPercentage: 1.0
                ),
                new ScenarioBuildResultData.ExperimentData(
                    buildId: 'regressedBuildId',
                    gitCommitId: currentGitCommitId,
                    controlGroupName: 'regressedControlGroup',
                    experimentGroupName: 'regressedExperimentGroup',
                    controlGroupMedian: 'regressedControlGroupMedian2',
                    experimentGroupMedian: 'regressedExperimentGroupMedian2',
                    controlGroupStandardError: 'regressedControlGroupSe2',
                    experimentGroupStandardError: 'regressedExperimentGroupSe2',
                    confidence: 'regressedConfidence2',
                    regressionPercentage: 2.0
                ),
                new ScenarioBuildResultData.ExperimentData(
                    buildId: 'regressedBuildId',
                    gitCommitId: currentGitCommitId,
                    controlGroupName: 'regressedControlGroup',
                    experimentGroupName: 'regressedExperimentGroup',
                    controlGroupMedian: 'regressedControlGroupMedian3',
                    experimentGroupMedian: 'regressedExperimentGroupMedian3',
                    controlGroupStandardError: 'regressedControlGroupSe3',
                    experimentGroupStandardError: 'regressedExperimentGroupSe3',
                    confidence: 'regressedConfidence3',
                    regressionPercentage: 3.0
                )
            ]
        )
    }

    ScenarioBuildResultData failedBuild() {
        return new ScenarioBuildResultData(
            scenarioName: 'failedBuild',
            webUrl: 'failedBuildUrl',
            successful: false,
            experimentData: []
        )
    }
}
