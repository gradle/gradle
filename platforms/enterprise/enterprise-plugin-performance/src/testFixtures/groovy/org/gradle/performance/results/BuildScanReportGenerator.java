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

import org.gradle.performance.results.report.AbstractReportGenerator;
import org.gradle.performance.results.report.PerformanceExecutionDataProvider;
import org.gradle.performance.results.report.PerformanceFlakinessDataProvider;

public class BuildScanReportGenerator extends AbstractReportGenerator<BuildScanResultsStore> {
    public static void main(String[] args) {
        new BuildScanReportGenerator().generateReport(args);
    }

    @Override
    protected void collectFailures(PerformanceFlakinessDataProvider flakinessDataProvider, PerformanceExecutionDataProvider executionDataProvider, FailureCollector failureCollector) {
        executionDataProvider.getReportScenarios()
            .forEach(scenario -> {
                if (scenario.isBuildFailed()) {
                    failureCollector.scenarioFailed();
                } else if (scenario.isRegressed()) {
                    failureCollector.scenarioRegressed();
                }
            });
    }

}
