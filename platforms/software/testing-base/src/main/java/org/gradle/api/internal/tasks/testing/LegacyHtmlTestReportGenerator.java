/*
 * Copyright 2025 the original author or authors.
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

import org.gradle.api.internal.tasks.testing.junit.result.AggregateTestResultsProvider;
import org.gradle.api.internal.tasks.testing.junit.result.BinaryResultBackedTestResultsProvider;
import org.gradle.api.internal.tasks.testing.junit.result.TestResultsProvider;
import org.gradle.api.internal.tasks.testing.report.HtmlTestReport;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.BuildOperationRunner;
import org.jspecify.annotations.NullMarked;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedList;
import java.util.List;

import static org.gradle.internal.concurrent.CompositeStoppable.stoppable;
import static org.gradle.util.internal.CollectionUtils.collect;

/**
 * The implementation of {@code TestReport} from before the introduction of the new generic / non-JVM test report infrastructure.
 */
@NullMarked
public class LegacyHtmlTestReportGenerator implements TestReportGenerator {

    private static TestResultsProvider createAggregateProvider(List<Path> resultDirs) {
        if (resultDirs.size() == 1) {
            File singleFile = resultDirs.get(0).toFile();
            return new BinaryResultBackedTestResultsProvider(singleFile);
        } else {
            List<TestResultsProvider> resultsProviders = new LinkedList<>();
            try {
                return new AggregateTestResultsProvider(collect(resultDirs, resultsProviders, p -> new BinaryResultBackedTestResultsProvider(p.toFile())));
            } catch (RuntimeException e) {
                stoppable(resultsProviders).stop();
                throw e;
            }
        }
    }

    private final BuildOperationRunner buildOperationRunner;
    private final BuildOperationExecutor buildOperationExecutor;
    private final Path reportsDirectory;

    @Inject
    public LegacyHtmlTestReportGenerator(BuildOperationRunner buildOperationRunner, BuildOperationExecutor buildOperationExecutor, Path reportsDirectory) {
        this.buildOperationRunner = buildOperationRunner;
        this.buildOperationExecutor = buildOperationExecutor;
        this.reportsDirectory = reportsDirectory;
    }

    @Override
    public Path generate(List<Path> resultsDirectories) {
        try (TestResultsProvider provider = createAggregateProvider(resultsDirectories)) {
            HtmlTestReport testReport = new HtmlTestReport(buildOperationRunner, buildOperationExecutor);
            testReport.generateReport(provider, reportsDirectory.toFile());
            return reportsDirectory.resolve("index.html");
        } catch (IOException e) {
            throw new RuntimeException("Could not read test results", e);
        }
    }
}
