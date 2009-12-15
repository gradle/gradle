/*
 * Copyright 2009 the original author or authors.
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
package org.gradle;

import org.codehaus.groovy.runtime.StackTraceUtils;
import org.gradle.api.GradleException;
import org.gradle.api.LocationAwareException;
import org.gradle.api.logging.LogLevel;
import org.gradle.initialization.DefaultCommandLine2StartParameterConverter;
import org.gradle.util.GUtil;
import org.gradle.execution.TaskSelectionException;
import org.slf4j.Logger;

import java.util.Formatter;

/**
 * A {@link BuildListener} which reports the build exception, if any.
 */
public class BuildExceptionReporter extends BuildAdapter {
    private enum ExceptionStyle {
        None, Sanitized, Full
    }

    private final Logger logger;
    private final StartParameter startParameter;

    public BuildExceptionReporter(Logger logger, StartParameter startParameter) {
        this.logger = logger;
        this.startParameter = startParameter;
    }

    public void buildFinished(BuildResult result) {
        Throwable failure = result.getFailure();
        if (failure == null) {
            return;
        }

        FailureDetails details = new FailureDetails(failure);
        if (failure instanceof GradleException) {
            reportBuildFailure((GradleException) failure, details);
        } else {
            reportInternalError(details);
        }

        write(details);
    }

    protected void write(FailureDetails details) {
        Formatter formatter = new Formatter();
        formatter.format("%nFAILURE: %s", details.summary.toString().trim());

        String location = details.location.toString().trim();
        if (location.length() > 0) {
            formatter.format("%n%n* Where:%n%s", location);
        }

        String failureDetails = details.details.toString().trim();
        if (failureDetails.length() > 0) {
            formatter.format("%n%n* What went wrong:%n%s", failureDetails);
        }

        String resolution = details.resolution.toString().trim();
        if (resolution.length() > 0) {
            formatter.format("%n%n* Try:%n%s", resolution);
        }
        switch (details.exception) {
            case None:
                logger.error(formatter.toString());
                break;
            case Sanitized:
                formatter.format("%n%n* Exception is:");
                logger.error(formatter.toString(), StackTraceUtils.deepSanitize(details.failure));
                break;
            case Full:
                formatter.format("%n%n* Exception is:");
                logger.error(formatter.toString(), details.failure);
                break;
        }
    }

    public void reportInternalError(FailureDetails details) {
        details.summary.format("Build aborted because of an internal error.");
        details.details.format("Build aborted because of an unexpected internal error. Please file an issue at: www.gradle.org.");
        details.resolution.format("Run with -%s option to get additional debug info.",
                DefaultCommandLine2StartParameterConverter.DEBUG);
        details.exception = ExceptionStyle.Full;
    }

    private void reportBuildFailure(GradleException failure, FailureDetails details) {
        boolean stacktrace = startParameter != null
                && (startParameter.getShowStacktrace() != StartParameter.ShowStacktrace.INTERNAL_EXCEPTIONS
                        || startParameter.getLogLevel() == LogLevel.DEBUG);
        if (stacktrace) {
            details.exception = ExceptionStyle.Sanitized;
        }
        boolean fullStacktrace = startParameter != null
                && (startParameter.getShowStacktrace() == StartParameter.ShowStacktrace.ALWAYS_FULL);
        if (fullStacktrace) {
            details.exception = ExceptionStyle.Full;
        }

        if (failure instanceof TaskSelectionException) {
            formatTaskSelectionFailure((TaskSelectionException) failure, details);
        } else {
            formatGenericFailure(failure, stacktrace, fullStacktrace, details);
        }
    }

    private void formatTaskSelectionFailure(TaskSelectionException failure, FailureDetails details) {
        assert failure.getCause() == null;
        details.summary.format("Could not determine which tasks to execute.");
        details.details.format("%s", getMessage(failure));
        details.resolution.format("Run with -%s to get a list of available tasks.", DefaultCommandLine2StartParameterConverter.TASKS);
    }

    private void formatGenericFailure(GradleException failure, boolean stacktrace, boolean fullStacktrace,
                                      FailureDetails details) {
        details.summary.format("Build failed with an exception.");
        if (!fullStacktrace) {
            if (!stacktrace) {
                details.resolution.format("Run with -%s or -%s option to get more details. ",
                        DefaultCommandLine2StartParameterConverter.STACKTRACE,
                        DefaultCommandLine2StartParameterConverter.DEBUG);
            }
            details.resolution.format("Run with -%s option to get the full (very verbose) stacktrace.",
                    DefaultCommandLine2StartParameterConverter.FULL_STACKTRACE);
        }

        if (failure instanceof LocationAwareException) {
            LocationAwareException scriptException = (LocationAwareException) failure;
            details.location.format("%s", scriptException.getLocation());
            details.details.format("%s", scriptException.getOriginalMessage());
            for (Throwable cause : scriptException.getReportableCauses()) {
                details.details.format("%nCause: %s", getMessage(cause));
            }
        } else {
            details.details.format("%s", getMessage(failure));
        }
    }

    private String getMessage(Throwable throwable) {
        String message = throwable.getMessage();
        if (GUtil.isTrue(message)) {
            return message;
        }
        return String.format("%s (no error message)", throwable.getClass().getName());
    }

    private static class FailureDetails {
        private ExceptionStyle exception = ExceptionStyle.None;
        private final Formatter summary = new Formatter();
        private final Formatter details = new Formatter();
        private final Formatter location = new Formatter();
        private final Formatter resolution = new Formatter();
        private final Throwable failure;

        public FailureDetails(Throwable failure) {
            this.failure = failure;
        }
    }
}
