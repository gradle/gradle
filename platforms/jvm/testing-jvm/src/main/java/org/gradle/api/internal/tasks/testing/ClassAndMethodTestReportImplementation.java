/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.api.internal.tasks.testing;

import org.gradle.api.NonNullApi;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.tasks.testing.junit.result.AggregateTestResultsProvider;
import org.gradle.api.internal.tasks.testing.junit.result.BinaryResultBackedTestResultsProvider;
import org.gradle.api.internal.tasks.testing.junit.result.TestResultsProvider;
import org.gradle.api.internal.tasks.testing.report.HtmlTestReport;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.BuildOperationRunner;

import java.io.File;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import static org.gradle.internal.concurrent.CompositeStoppable.stoppable;
import static org.gradle.util.internal.CollectionUtils.collect;

@NonNullApi
public class ClassAndMethodTestReportImplementation implements TestReportImplementation {
    private static TestResultsProvider createAggregateProvider(FileCollection resultDirs) {
        List<TestResultsProvider> resultsProviders = new LinkedList<TestResultsProvider>();
        try {
            if (resultDirs.getFiles().size() == 1) {
                return new BinaryResultBackedTestResultsProvider(resultDirs.getSingleFile());
            } else {
                return new AggregateTestResultsProvider(collect(resultDirs, resultsProviders, BinaryResultBackedTestResultsProvider::new));
            }
        } catch (RuntimeException e) {
            stoppable(resultsProviders).stop();
            throw e;
        }
    }

    private final TestResultsProvider provider;

    public ClassAndMethodTestReportImplementation(FileCollection resultDirs) {
        this.provider = createAggregateProvider(resultDirs);
    }

    @Override
    public boolean hasResults() {
        return provider.isHasResults();
    }

    @Override
    public void generateReport(BuildOperationRunner operationRunner, BuildOperationExecutor operationExecutor, File outputDir) {
        HtmlTestReport testReport = new HtmlTestReport(operationRunner, operationExecutor);
        testReport.generateReport(provider, outputDir);
    }

    @Override
    public void close() throws IOException {
        provider.close();
    }
}
