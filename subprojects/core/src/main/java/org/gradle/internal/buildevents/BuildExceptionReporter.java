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
import org.gradle.api.Action;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.logging.configuration.LoggingConfiguration;
import org.gradle.api.logging.configuration.ShowStacktrace;
import org.gradle.execution.MultipleBuildFailures;
import org.gradle.initialization.BuildClientMetaData;
import org.gradle.initialization.StartParameterBuildOptions;
import org.gradle.internal.enterprise.core.GradleEnterprisePluginManager;
import org.gradle.internal.exceptions.ContextAwareException;
import org.gradle.internal.exceptions.ExceptionContextVisitor;
import org.gradle.internal.exceptions.FailureResolutionAware;
import org.gradle.internal.exceptions.StyledException;
import org.gradle.internal.logging.LoggingConfigurationBuildOptions;
import org.gradle.internal.logging.text.BufferingStyledTextOutput;
import org.gradle.internal.logging.text.LinePrefixingStyledTextOutput;
import org.gradle.internal.logging.text.StyledTextOutput;
import org.gradle.internal.logging.text.StyledTextOutputFactory;
import org.gradle.util.internal.GUtil;

import java.util.List;
import java.util.function.Consumer;

import static org.gradle.internal.logging.text.StyledTextOutput.Style.Failure;
import static org.gradle.internal.logging.text.StyledTextOutput.Style.Info;
import static org.gradle.internal.logging.text.StyledTextOutput.Style.Normal;
import static org.gradle.internal.logging.text.StyledTextOutput.Style.UserInput;

/**
 * Reports the build exception, if any.
 */
public class BuildExceptionReporter implements Action<Throwable> {
    private enum ExceptionStyle {
        NONE, FULL
    }

    private final StyledTextOutputFactory textOutputFactory;
    private final LoggingConfiguration loggingConfiguration;
    private final BuildClientMetaData clientMetaData;
    private final GradleEnterprisePluginManager gradleEnterprisePluginManager;

    public BuildExceptionReporter(StyledTextOutputFactory textOutputFactory, LoggingConfiguration loggingConfiguration, BuildClientMetaData clientMetaData, GradleEnterprisePluginManager gradleEnterprisePluginManager) {
        this.textOutputFactory = textOutputFactory;
        this.loggingConfiguration = loggingConfiguration;
        this.clientMetaData = clientMetaData;
        this.gradleEnterprisePluginManager = gradleEnterprisePluginManager;
    }

    public BuildExceptionReporter(StyledTextOutputFactory textOutputFactory, LoggingConfiguration loggingConfiguration, BuildClientMetaData clientMetaData) {
        this(textOutputFactory, loggingConfiguration, clientMetaData, null);
    }

    public void buildFinished(BuildResult result) {
        Throwable failure = result.getFailure();
        if (failure == null) {
            return;
        }

        execute(failure);
    }

    @Override
    public void execute(Throwable failure) {
        if (failure instanceof MultipleBuildFailures) {
            renderMultipleBuildExceptions((MultipleBuildFailures) failure);
        } else {
            renderSingleBuildException(failure);
        }
    }

    private void renderMultipleBuildExceptions(MultipleBuildFailures failure) {
        String message = failure.getMessage();
        List<? extends Throwable> flattenedFailures = failure.getCauses();
        StyledTextOutput output = textOutputFactory.create(BuildExceptionReporter.class, LogLevel.ERROR);
        output.println();
        output.withStyle(Failure).format("FAILURE: %s", message);
        output.println();

        for (int i = 0; i < flattenedFailures.size(); i++) {
            Throwable cause = flattenedFailures.get(i);
            FailureDetails details = constructFailureDetails("Task", cause);

            output.println();
            output.withStyle(Failure).format("%s: ", i + 1);
            details.summary.writeTo(output.withStyle(Failure));
            output.println();
            output.text("-----------");

            writeFailureDetails(output, details);

            output.println("==============================================================================");
        }
        writeGeneralTips(output);
    }

    private void renderSingleBuildException(Throwable failure) {
        StyledTextOutput output = textOutputFactory.create(BuildExceptionReporter.class, LogLevel.ERROR);
        FailureDetails details = constructFailureDetails("Build", failure);

        output.println();
        output.withStyle(Failure).text("FAILURE: ");
        details.summary.writeTo(output.withStyle(Failure));
        output.println();

        writeFailureDetails(output, details);

        writeGeneralTips(output);
    }

    private ExceptionStyle getShowStackTraceOption() {
        if (loggingConfiguration.getShowStacktrace() != ShowStacktrace.INTERNAL_EXCEPTIONS) {
            return ExceptionStyle.FULL;
        } else {
            return ExceptionStyle.NONE;
        }
    }

    private FailureDetails constructFailureDetails(String granularity, Throwable failure) {
        FailureDetails details = new FailureDetails(failure, getShowStackTraceOption());
        details.summary.format("%s failed with an exception.", granularity);

        fillInFailureResolution(details);

        if (failure instanceof ContextAwareException) {
            ((ContextAwareException) failure).accept(new ExceptionFormattingVisitor(details));
        } else {
            details.appendDetails();
        }
        details.renderStackTrace();
        return details;
    }

    private static class ExceptionFormattingVisitor extends ExceptionContextVisitor {
        private final FailureDetails failureDetails;

        private int depth;

        private ExceptionFormattingVisitor(FailureDetails failureDetails) {
            this.failureDetails = failureDetails;
        }

        @Override
        protected void visitCause(Throwable cause) {
            failureDetails.failure = cause;
            failureDetails.appendDetails();
        }

        @Override
        protected void visitLocation(String location) {
            failureDetails.location.text(location);
        }

        @Override
        public void node(Throwable node) {
            LinePrefixingStyledTextOutput output = getLinePrefixingStyledTextOutput(failureDetails);
            renderStyledError(node, output);
        }

        @Override
        public void startChildren() {
            depth++;
        }

        @Override
        public void endChildren() {
            depth--;
        }

        private LinePrefixingStyledTextOutput getLinePrefixingStyledTextOutput(FailureDetails details) {
            details.details.format("%n");
            StringBuilder prefix = new StringBuilder();
            for (int i = 1; i < depth; i++) {
                prefix.append("   ");
            }
            details.details.text(prefix);
            prefix.append("  ");
            details.details.style(Info).text("> ").style(Normal);

            return new LinePrefixingStyledTextOutput(details.details, prefix, false);
        }
    }

    private void fillInFailureResolution(FailureDetails details) {
        BufferingStyledTextOutput resolution = details.resolution;
        ContextImpl context = new ContextImpl(resolution);
        if (details.failure instanceof FailureResolutionAware) {
            ((FailureResolutionAware) details.failure).appendResolutions(context);
        }
        if (details.exceptionStyle == ExceptionStyle.NONE) {
            context.appendResolution(output -> {
                resolution.text("Run with ");
                resolution.withStyle(UserInput).format("--%s", LoggingConfigurationBuildOptions.StacktraceOption.STACKTRACE_LONG_OPTION);
                resolution.text(" option to get the stack trace.");
            });
        }
        if (loggingConfiguration.getLogLevel() != LogLevel.DEBUG) {
            context.appendResolution(output -> {
                resolution.text("Run with ");
                if (loggingConfiguration.getLogLevel() != LogLevel.INFO) {
                    resolution.withStyle(UserInput).format("--%s", LoggingConfigurationBuildOptions.LogLevelOption.INFO_LONG_OPTION);
                    resolution.text(" or ");
                }
                resolution.withStyle(UserInput).format("--%s", LoggingConfigurationBuildOptions.LogLevelOption.DEBUG_LONG_OPTION);
                resolution.text(" option to get more log output.");
            });
        }

        if (!context.missingBuild && !isGradleEnterprisePluginApplied()) {
            addBuildScanMessage(context);
        }
    }

    private void addBuildScanMessage(ContextImpl context) {
        context.appendResolution(output -> {
            output.text("Run with ");
            output.withStyle(UserInput).format("--%s", StartParameterBuildOptions.BuildScanOption.LONG_OPTION);
            output.text(" to get full insights.");
        });
    }

    private boolean isGradleEnterprisePluginApplied() {
        return gradleEnterprisePluginManager != null && gradleEnterprisePluginManager.isPresent();
    }

    private void writeGeneralTips(StyledTextOutput resolution) {
        resolution.println();
        resolution.text("* Get more help at ");
        resolution.withStyle(UserInput).text("https://help.gradle.org");
        resolution.println();
    }

    private static String getMessage(Throwable throwable) {
        try {
            String message = throwable.getMessage();
            if (GUtil.isTrue(message)) {
                return message;
            }
            return String.format("%s (no error message)", throwable.getClass().getName());
        } catch (Throwable t) {
            return String.format("Unable to get message for failure of type %s due to %s", throwable.getClass().getSimpleName(), t.getMessage());
        }
    }

    private void writeFailureDetails(StyledTextOutput output, FailureDetails details) {
        if (details.location.getHasContent()) {
            output.println();
            output.println("* Where:");
            details.location.writeTo(output);
            output.println();
        }

        if (details.details.getHasContent()) {
            output.println();
            output.println("* What went wrong:");
            details.details.writeTo(output);
            output.println();
        }

        if (details.resolution.getHasContent()) {
            output.println();
            output.println("* Try:");
            details.resolution.writeTo(output);
            output.println();
        }

        if (details.stackTrace.getHasContent()) {
            output.println();
            output.println("* Exception is:");
            details.stackTrace.writeTo(output);
            output.println();
        }
    }

    private static class FailureDetails {
        Throwable failure;
        final BufferingStyledTextOutput summary = new BufferingStyledTextOutput();
        final BufferingStyledTextOutput details = new BufferingStyledTextOutput();
        final BufferingStyledTextOutput location = new BufferingStyledTextOutput();
        final BufferingStyledTextOutput stackTrace = new BufferingStyledTextOutput();
        final BufferingStyledTextOutput resolution = new BufferingStyledTextOutput();
        final ExceptionStyle exceptionStyle;

        public FailureDetails(Throwable failure, ExceptionStyle exceptionStyle) {
            this.failure = failure;
            this.exceptionStyle = exceptionStyle;
        }

        void appendDetails() {
            renderStyledError(failure, details);
        }

        void renderStackTrace() {
            if (exceptionStyle == ExceptionStyle.FULL) {
                try {
                    stackTrace.exception(failure);
                } catch (Throwable t) {
                    // Discard. Should also render this as a separate build failure
                }
            }
        }
    }

    static void renderStyledError(Throwable failure, StyledTextOutput details) {
        if (failure instanceof StyledException) {
            ((StyledException) failure).render(details);
        } else {
            details.text(getMessage(failure));
        }
    }

    private class ContextImpl implements FailureResolutionAware.Context {
        private final BufferingStyledTextOutput resolution;
        private boolean missingBuild;

        public ContextImpl(BufferingStyledTextOutput resolution) {
            this.resolution = resolution;
        }

        @Override
        public BuildClientMetaData getClientMetaData() {
            return clientMetaData;
        }

        @Override
        public void doNotSuggestResolutionsThatRequireBuildDefinition() {
            missingBuild = true;
        }

        @Override
        public void appendResolution(Consumer<StyledTextOutput> resolutionProducer) {
            if (resolution.getHasContent()) {
                resolution.println();
            }
            resolution.style(Info).text("> ").style(Normal);
            resolutionProducer.accept(resolution);
        }
    }
}
