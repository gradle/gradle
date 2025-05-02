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
package org.gradle.internal.buildevents;

import org.gradle.BuildResult;
import org.gradle.api.logging.LogLevel;
import org.gradle.execution.WorkValidationWarningReporter;
import org.gradle.internal.deprecation.DeprecationLogger;
import org.gradle.internal.logging.format.DurationFormatter;
import org.gradle.internal.logging.text.StyledTextOutput;
import org.gradle.internal.logging.text.StyledTextOutputFactory;
import org.gradle.internal.time.Clock;

import java.util.Locale;

import static org.gradle.internal.logging.text.StyledTextOutput.Style.FailureHeader;
import static org.gradle.internal.logging.text.StyledTextOutput.Style.SuccessHeader;

/**
 * A {@link org.gradle.BuildListener} which logs the final result of the build.
 */
public class BuildResultLogger {

    private final StyledTextOutputFactory textOutputFactory;
    private final BuildStartedTime buildStartedTime;
    private final DurationFormatter durationFormatter;
    private final WorkValidationWarningReporter workValidationWarningReporter;
    private final Clock clock;

    public BuildResultLogger(
        StyledTextOutputFactory textOutputFactory,
        BuildStartedTime buildStartedTime,
        Clock clock,
        DurationFormatter durationFormatter,
        WorkValidationWarningReporter workValidationWarningReporter
    ) {
        this.textOutputFactory = textOutputFactory;
        this.buildStartedTime = buildStartedTime;
        this.clock = clock;
        this.durationFormatter = durationFormatter;
        this.workValidationWarningReporter = workValidationWarningReporter;
    }

    public void buildFinished(BuildResult result) {
        // Summary of deprecations is considered a part of the build summary
        DeprecationLogger.reportSuppressedDeprecations();

        // Summary of validation warnings during the build
        workValidationWarningReporter.reportWorkValidationWarningsAtEndOfBuild();

        boolean buildSucceeded = result.getFailure() == null;

        StyledTextOutput textOutput = textOutputFactory.create(BuildResultLogger.class, buildSucceeded ? LogLevel.LIFECYCLE : LogLevel.ERROR);
        textOutput.println();
        String action = result.getAction().toUpperCase(Locale.ROOT);
        if (buildSucceeded) {
            textOutput.withStyle(SuccessHeader).text(action + " SUCCESSFUL");
        } else {
            textOutput.withStyle(FailureHeader).text(action + " FAILED");
        }

        long buildDurationMillis = clock.getCurrentTime() - buildStartedTime.getStartTime();
        textOutput.formatln(" in %s", durationFormatter.format(buildDurationMillis));
    }
}
