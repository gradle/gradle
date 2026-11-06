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

package org.gradle.api.internal.tasks.testing.results;

import org.apache.commons.io.FileUtils;
import org.gradle.api.internal.tasks.testing.report.generic.AggregateTestReportRenderer;
import org.gradle.internal.logging.ConsoleRenderer;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;
import org.gradle.internal.time.Time;
import org.gradle.internal.time.Timer;
import org.gradle.problems.buildtree.ProblemReporter;
import org.gradle.reporting.HtmlReportRenderer;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Collects summaries of test executions and generates a report at the end of the build that summarizes and links to their reports.
 */
@NullMarked
@ServiceScope(Scope.BuildTree.class)
public class AggregateTestEventReporter implements ProblemReporter, TestExecutionResultsListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(AggregateTestEventReporter.class);

    // Mutable state
    private final Queue<Execution> executions = new ConcurrentLinkedQueue<>();

    @Override
    public String getId() {
        return "aggregate-test-results";
    }

    @Override
    public void executionResultsAvailable(Execution execution) {
        executions.add(execution);
    }

    @Override
    public void report(File reportDir, ProblemConsumer validationFailures) {
        Path reportLocation = reportDir.toPath().resolve("reports").resolve("aggregate-test-results");

        // The styles and scripts are only written if missing, so delete any existing files
        // to overwrite copies from older Gradle versions.
        if (Files.exists(reportLocation)) {
            try {
                FileUtils.deleteDirectory(reportLocation.toFile());
            } catch (IOException e) {
                LOGGER.debug("Failed to delete stale aggregate report directory", e);
            }
        }

        if (!executions.isEmpty()) {
            List<Execution> executionList = new ArrayList<>(executions);
            Path reportIndexFile = generateTestReport(reportLocation, executionList);

            // Print report to console only if there are failures and if we have multiple results to aggregate.
            if (executionList.stream().filter(Execution::failed).count() > 1) {
                emitReport(reportIndexFile);
            }
        }
    }

    /**
     * Generates an aggregate test report at the given directory.
     *
     * @return The path to the index file that should be reported to the user.
     */
    private static Path generateTestReport(Path reportDirectory, List<Execution> executions) {
        Timer clock = Time.startTimer();
        Path indexFile = reportDirectory.resolve("index.html");
        new HtmlReportRenderer().renderSinglePage(executions, new AggregateTestReportRenderer(reportDirectory), indexFile.toFile());
        LOGGER.info("Finished generating aggregate test report ({}) into: {}", clock.getElapsed(), reportDirectory);
        return indexFile;
    }

    /**
     * Emits the report to the user.
     *
     * @param reportIndexFile The path to report to the user as a link.
     */
    private static void emitReport(Path reportIndexFile) {
        String url = new ConsoleRenderer().asClickableFileUrl(reportIndexFile.toFile());

        // TODO: Integrate with Problems API report or some new "build dashboard 2.0"
        // We should avoid printing more than one link at the end of the build.
        LOGGER.warn("Aggregate test results: {}", url);
    }
}
