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

package org.gradle.performance.results;

import org.apache.commons.lang3.StringUtils;
import org.gradle.ci.common.model.FlakyTest;
import org.gradle.ci.github.DefaultGitHubIssuesClient;
import org.gradle.ci.github.GitHubIssuesClient;
import org.gradle.ci.tagging.flaky.GitHubKnownIssuesProvider;
import org.gradle.ci.tagging.flaky.KnownFlakyTestProvider;

public interface PerformanceFlakinessAnalyzer {
    static PerformanceFlakinessAnalyzer create() {
        String githubToken = System.getProperty("githubToken");
        if (StringUtils.isBlank(githubToken)) {
            return NoOpPerformanceFlakinessAnalyzer.INSTANCE;
        } else {
            GitHubIssuesClient gitHubIssuesClient = new DefaultGitHubIssuesClient(githubToken);
            KnownFlakyTestProvider provider = new GitHubKnownIssuesProvider(gitHubIssuesClient);
            return new DefaultPerformanceFlakinessAnalyzer(gitHubIssuesClient, provider);
        }
    }

    void report(ScenarioBuildResultData flakyScenario);

    FlakyTest findKnownFlakyTest(ScenarioBuildResultData scenario);

    enum NoOpPerformanceFlakinessAnalyzer implements PerformanceFlakinessAnalyzer {
        INSTANCE;

        @Override
        public void report(ScenarioBuildResultData flakyScenario) {
        }

        @Override
        public FlakyTest findKnownFlakyTest(ScenarioBuildResultData scenario) {
            return null;
        }
    }
}
