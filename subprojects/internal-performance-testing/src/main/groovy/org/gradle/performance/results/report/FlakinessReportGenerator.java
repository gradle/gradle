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

package org.gradle.performance.results.report;

import org.gradle.performance.results.CrossVersionResultsStore;
import org.gradle.performance.results.FileRenderer;
import org.gradle.performance.results.PerformanceFlakinessDataProvider;
import org.gradle.performance.results.ResultsStore;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Set;

public class FlakinessReportGenerator extends AbstractReportGenerator<CrossVersionResultsStore> {
    public static void main(String[] args) {
        new FlakinessReportGenerator().generateReport(args);
    }

    @Override
    protected PerformanceExecutionDataProvider getExecutionDataProvider(ResultsStore store, List<File> resultJsons, Set<String> teamCityBuildIds) {
        return new FlakinessDetectionPerformanceExecutionDataProvider(store, resultJsons, teamCityBuildIds);
    }

    @Override
    protected void renderIndexPage(PerformanceFlakinessDataProvider flakinessDataProvider, PerformanceExecutionDataProvider executionDataProvider, File output) throws IOException {
        new FileRenderer().render(null, new FlakinessIndexPageGenerator(flakinessDataProvider, executionDataProvider), output);
    }

    @Override
    protected void collectFailures(PerformanceFlakinessDataProvider flakinessDataProvider, PerformanceExecutionDataProvider executionDataProvider, FailureCollector failureCollector) {
    }
}
