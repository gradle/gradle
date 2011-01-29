/*
 * Copyright 2010 the original author or authors.
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
import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.LocationAwareException;
import org.gradle.api.logging.LogLevel;
import org.gradle.configuration.ImplicitTasksConfigurer;
import org.gradle.execution.TaskSelectionException;
import org.gradle.initialization.BuildClientMetaData;
import org.gradle.initialization.DefaultCommandLineConverter;
import org.gradle.logging.StyledTextOutput;
import org.gradle.logging.StyledTextOutputFactory;
import org.gradle.logging.internal.AbstractStyledTextOutput;
import org.gradle.logging.internal.LoggingCommandLineConverter;
import org.gradle.util.GUtil;

import java.util.ArrayList;
import java.util.List;

import static org.gradle.logging.StyledTextOutput.Style.Failure;
import static org.gradle.logging.StyledTextOutput.Style.UserInput;

/**
 * A {@link BuildListener} which reports the build exception, if any.
 */
public class BuildExceptionReporter extends BuildAdapter {
    private enum ExceptionStyle {
        NONE, SANITIZED, FULL;
    }

    private final StyledTextOutputFactory textOutputFactory;
    private final StartParameter startParameter;
    private final BuildClientMetaData clientMetaData;

    public BuildExceptionReporter(StyledTextOutputFactory textOutputFactory, StartParameter startParameter, BuildClientMetaData clientMetaData) {
        this.textOutputFactory = textOutputFactory;
        this.startParameter = startParameter;
        this.clientMetaData = clientMetaData;
    }

    public void buildFinished(BuildResult result) {
        Throwable failure = result.getFailure();
        if (failure == null) {
            return;
        }

        reportException(failure);
    }

    public void reportException(Throwable failure) {
        FailureDetails details = new FailureDetails(failure);
        if (failure instanceof GradleException) {
            reportBuildFailure((GradleException) failure, details);
        } else {
            reportInternalError(details);
        }

        write(details);
    }

    protected void write(FailureDetails details) {
        StyledTextOutput output = textOutputFactory.create(BuildExceptionReporter.class, LogLevel.ERROR);

        output.println();
        output.withStyle(Failure).text("FAILURE: ");
        details.summary.replay(output.withStyle(Failure));

        if (details.location.hasContent) {
            output.println().println();
            output.println("* Where:");
            details.location.replay(output);
        }

        if (details.details.hasContent) {
            output.println().println();
            output.println("* What went wrong:");
            details.details.replay(output);
        }

        if (details.resolution.hasContent) {
            output.println().println();
            output.println("* Try:");
            details.resolution.replay(output);
        }

        Throwable exception = null;
        switch (details.exceptionStyle) {
            case NONE:
                break;
            case SANITIZED:
                exception = StackTraceUtils.deepSanitize(details.failure);
                break;
            case FULL:
                exception = details.failure;
                break;
        }

        if (exception != null) {
            output.println().println();
            output.println("* Exception is:");
            output.exception(exception);
        }

        output.println();
    }

    public void reportInternalError(FailureDetails details) {
        details.summary.text("Build aborted because of an internal error.");
        details.details.text("Build aborted because of an unexpected internal error. Please file an issue at: http://www.gradle.org.");

        if (startParameter.getLogLevel() != LogLevel.DEBUG) {
            details.resolution.text("Run with ");
            details.resolution.withStyle(UserInput).format("--%s", LoggingCommandLineConverter.DEBUG_LONG);
            details.resolution.text(" option to get additional debug info.");
            details.exceptionStyle = ExceptionStyle.FULL;
        }
    }

    private void reportBuildFailure(GradleException failure, FailureDetails details) {
        if (startParameter.getShowStacktrace() == StartParameter.ShowStacktrace.ALWAYS || startParameter.getLogLevel() == LogLevel.DEBUG) {
            details.exceptionStyle = ExceptionStyle.SANITIZED;
        }
        if (startParameter.getShowStacktrace() == StartParameter.ShowStacktrace.ALWAYS_FULL) {
            details.exceptionStyle = ExceptionStyle.FULL;
        }

        if (failure instanceof TaskSelectionException) {
            formatTaskSelectionFailure((TaskSelectionException) failure, details);
        } else {
            formatGenericFailure(failure, details);
        }
    }

    private void formatTaskSelectionFailure(TaskSelectionException failure, FailureDetails details) {
        assert failure.getCause() == null;
        details.summary.text("Could not determine which tasks to execute.");
        details.details.text(getMessage(failure));
        details.resolution.text("Run ");
        clientMetaData.describeCommand(details.resolution.withStyle(UserInput), ImplicitTasksConfigurer.TASKS_TASK);
        details.resolution.text(" to get a list of available tasks.");
    }

    private void formatGenericFailure(GradleException failure, FailureDetails details) {
        details.summary.text("Build failed with an exception.");

        fillInFailureResolution(details);

        if (failure instanceof LocationAwareException) {
            LocationAwareException scriptException = (LocationAwareException) failure;
            if (scriptException.getLocation() != null) {
                details.location.text(scriptException.getLocation());
            }
            details.details.text(scriptException.getOriginalMessage());
            for (Throwable cause : scriptException.getReportableCauses()) {
                details.details.format("%nCause: %s", getMessage(cause));
            }
        } else {
            details.details.text(getMessage(failure));
        }
    }

    private void fillInFailureResolution(FailureDetails details) {
        if (details.exceptionStyle == ExceptionStyle.NONE) {
            details.resolution.text("Run with ");
            details.resolution.withStyle(UserInput).format("--%s", DefaultCommandLineConverter.STACKTRACE_LONG);
            details.resolution.text(" option to get the stack trace. ");
        }

        if (startParameter.getLogLevel() != LogLevel.DEBUG) {
            details.resolution.text("Run with ");
            if (startParameter.getLogLevel() != LogLevel.INFO) {
                details.resolution.withStyle(UserInput).format("--%s", LoggingCommandLineConverter.INFO_LONG);
                details.resolution.text(" or ");
            }
            details.resolution.withStyle(UserInput).format("--%s", LoggingCommandLineConverter.DEBUG_LONG);
            details.resolution.text(" option to get more log output.");
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
        final Throwable failure;
        final RecordingStyledTextOutput summary = new RecordingStyledTextOutput();
        final RecordingStyledTextOutput details = new RecordingStyledTextOutput();
        final RecordingStyledTextOutput location = new RecordingStyledTextOutput();
        final RecordingStyledTextOutput resolution = new RecordingStyledTextOutput();

        ExceptionStyle exceptionStyle = ExceptionStyle.NONE;

        public FailureDetails(Throwable failure) {
            this.failure = failure;
        }
    }

    private static class RecordingStyledTextOutput extends AbstractStyledTextOutput {
        private final List<Action<StyledTextOutput>> events = new ArrayList<Action<StyledTextOutput>>();
        private boolean hasContent;

        void replay(StyledTextOutput output) {
            for (Action<StyledTextOutput> event : events) {
                event.execute(output);
            }
            events.clear();
        }

        @Override
        protected void doStyleChange(final Style style) {
            if (!events.isEmpty() && (events.get(events.size() - 1) instanceof ChangeStyleAction)) {
                events.remove(events.size() - 1);
            }
            events.add(new ChangeStyleAction(style));
        }

        @Override
        protected void doAppend(final String text) {
            if (text.length() == 0) {
                return;
            }
            hasContent = true;
            events.add(new Action<StyledTextOutput>() {
                public void execute(StyledTextOutput styledTextOutput) {
                    styledTextOutput.text(text);
                }
            });
        }

        private static class ChangeStyleAction implements Action<StyledTextOutput> {
            private final Style style;

            public ChangeStyleAction(Style style) {
                this.style = style;
            }

            public void execute(StyledTextOutput styledTextOutput) {
                styledTextOutput.style(style);
            }
        }
    }
}
