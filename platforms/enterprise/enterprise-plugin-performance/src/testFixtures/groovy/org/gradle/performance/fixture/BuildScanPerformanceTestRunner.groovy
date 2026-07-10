/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.performance.fixture

import groovy.transform.CompileStatic
import org.gradle.integtests.fixtures.executer.IntegrationTestBuildContext
import org.gradle.internal.jvm.Jvm
import org.gradle.internal.os.OperatingSystem
import org.gradle.performance.results.CrossBuildPerformanceResults
import org.gradle.performance.results.DataReporter
import org.gradle.performance.util.Git
import org.gradle.util.GradleVersion

@CompileStatic
class BuildScanPerformanceTestRunner extends AbstractCrossBuildPerformanceTestRunner<CrossBuildPerformanceResults> {
    private final String pluginCommitSha

    BuildScanPerformanceTestRunner(GradleBuildExperimentRunner experimentRunner, DataReporter<CrossBuildPerformanceResults> dataReporter, String pluginCommitSha, IntegrationTestBuildContext buildContext) {
        super(experimentRunner, dataReporter, buildContext)
        this.pluginCommitSha = pluginCommitSha
        this.testGroup = "build scan plugin"

    }

    @Override
    CrossBuildPerformanceResults newResult() {
        new CrossBuildPerformanceResults(
            testClass: testClassName,
            testId: testId,
            testProject: testProject,
            testGroup: testGroup,
            jvm: Jvm.current().toString(),
            host: InetAddress.getLocalHost().getHostName(),
            operatingSystem: OperatingSystem.current().toString(),
            versionUnderTest: GradleVersion.current().getVersion(),
            vcsBranch: Git.current().branchName,
            vcsCommits: [Git.current().commitId, pluginCommitSha],
            startTime: clock.getCurrentTime(),
            channel: determineChannel(),
            teamCityBuildId: determineTeamCityBuildId()
        )
    }
}
