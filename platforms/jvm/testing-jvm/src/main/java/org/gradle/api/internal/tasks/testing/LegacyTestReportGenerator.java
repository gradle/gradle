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
import org.gradle.api.UncheckedIOException;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.tasks.testing.junit.result.AggregateTestResultsProvider;
import org.gradle.api.internal.tasks.testing.junit.result.BinaryResultBackedTestResultsProvider;
import org.gradle.api.internal.tasks.testing.junit.result.TestResultsProvider;
import org.gradle.api.internal.tasks.testing.report.HtmlTestReport;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.BuildOperationRunner;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import static org.gradle.internal.concurrent.CompositeStoppable.stoppable;
import static org.gradle.util.internal.CollectionUtils.collect;

/**
 * The implementation of {@link org.gradle.api.tasks.testing.TestReport} from before the introduction of the new generic / non-JVM test report infrastructure.
 */
@NonNullApi
public class LegacyTestReportGenerator implements TestReportGenerator {
    private static Supplier<TestResultsProvider> createAggregateProvider(FileCollection resultDirs) {
        if (resultDirs.getFiles().size() == 1) {
            File singleFile = resultDirs.getSingleFile();
            return () -> new BinaryResultBackedTestResultsProvider(singleFile);
        } else {
            Set<File> dirs = resultDirs.getFiles();
            return () -> {
                List<TestResultsProvider> resultsProviders = new LinkedList<>();
                try {
                    return new AggregateTestResultsProvider(collect(dirs, resultsProviders, BinaryResultBackedTestResultsProvider::new));
                } catch (RuntimeException e) {
                    stoppable(resultsProviders).stop();
                    throw e;
                }
            };
        }
    }

    private final Supplier<TestResultsProvider> provider;

    public LegacyTestReportGenerator(FileCollection resultDirs) {
        this.provider = createAggregateProvider(resultDirs);
    }

    @Override
    public boolean hasResults() {
        try (TestResultsProvider provider = this.provider.get()) {
            return provider.isHasResults();
        } catch (IOException e) {
            throw new RuntimeException("Could not determine if there are test results", e);
        }
    }

    @Override
    public void generateReport(BuildOperationRunner operationRunner, BuildOperationExecutor operationExecutor, Path outputDir) {
        HtmlTestReport testReport = new HtmlTestReport(operationRunner, operationExecutor);
        try (TestResultsProvider provider = this.provider.get()) {
            testReport.generateReport(provider, outputDir.toFile());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
