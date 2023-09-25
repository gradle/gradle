/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.internal.featurelifecycle;

import org.gradle.api.GradleException;
import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.api.logging.configuration.WarningMode;
import org.gradle.api.problems.ProblemBuilder;
import org.gradle.api.problems.ProblemBuilderDefiningLabel;
import org.gradle.api.problems.ProblemBuilderDefiningLocation;
import org.gradle.api.problems.ProblemBuilderDefiningCategory;
import org.gradle.api.problems.ProblemBuilderSpec;
import org.gradle.api.problems.Problems;
import org.gradle.internal.SystemProperties;
import org.gradle.internal.deprecation.DeprecatedFeatureUsage;
import org.gradle.internal.logging.LoggingConfigurationBuildOptions;
import org.gradle.internal.operations.BuildOperationProgressEventEmitter;
import org.gradle.internal.problems.NoOpProblemDiagnosticsFactory;
import org.gradle.problems.Location;
import org.gradle.problems.ProblemDiagnostics;
import org.gradle.problems.buildtree.ProblemDiagnosticsFactory;
import org.gradle.problems.buildtree.ProblemStream;
import org.gradle.util.internal.DefaultGradleVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

import static org.gradle.api.problems.Severity.WARNING;

public class LoggingDeprecatedFeatureHandler implements FeatureHandler<DeprecatedFeatureUsage> {
    public static final String ORG_GRADLE_DEPRECATION_TRACE_PROPERTY_NAME = "org.gradle.deprecation.trace";
    public static final String WARNING_SUMMARY = "Deprecated Gradle features were used in this build, making it incompatible with Gradle";

    private static final DocumentationRegistry DOCUMENTATION_REGISTRY = new DocumentationRegistry();
    private static final Logger LOGGER = LoggerFactory.getLogger(LoggingDeprecatedFeatureHandler.class);
    private static final String ELEMENT_PREFIX = "\tat ";
    private static final String RUN_WITH_STACKTRACE_INFO = "\t(Run with --stacktrace to get the full stack trace of this deprecation warning.)";
    private static boolean traceLoggingEnabled;

    private final Set<String> loggedMessages = new CopyOnWriteArraySet<String>();
    private final Set<String> loggedUsages = new CopyOnWriteArraySet<String>();
    private boolean deprecationsFound = false;
    private ProblemStream problemStream = NoOpProblemDiagnosticsFactory.EMPTY_STREAM;

    private WarningMode warningMode = WarningMode.Summary;
    private BuildOperationProgressEventEmitter progressEventEmitter;
    private Problems problemsService;
    private GradleException error;

    public void init(ProblemDiagnosticsFactory problemDiagnosticsFactory, WarningMode warningMode, BuildOperationProgressEventEmitter progressEventEmitter, Problems problemsService) {
        this.warningMode = warningMode;
        this.problemStream = warningMode.shouldDisplayMessages()
            ? problemDiagnosticsFactory.newUnlimitedStream()
            : problemDiagnosticsFactory.newStream();
        this.progressEventEmitter = progressEventEmitter;
        this.problemsService = problemsService;
    }

    @Override
    public void featureUsed(final DeprecatedFeatureUsage usage) {
        deprecationsFound = true;
        final ProblemDiagnostics diagnostics = problemStream.forCurrentCaller(new StackTraceSanitizer(usage.getCalledFrom()));
        if (warningMode.shouldDisplayMessages()) {
            maybeLogUsage(usage, diagnostics);
        }
        if (warningMode == WarningMode.Fail) {
            if (error == null) {
                error = new GradleException(WARNING_SUMMARY + " " + DefaultGradleVersion.current().getNextMajorVersion().getVersion());
            }
        }
        if (problemsService != null) {
            problemsService.createProblem(new ProblemBuilderSpec() {
                    @Override
                    public ProblemBuilder apply(ProblemBuilderDefiningLabel builder) {
                        ProblemBuilderDefiningLocation problemBuilderDefiningLocation = builder.label(usage.formattedMessage())
                            .documentedAt(usage.getDocumentationUrl());
                        return addPossibleLocation(diagnostics, problemBuilderDefiningLocation)
                            .category("generic_deprecation")
                            .severity(WARNING);
                    }
                })
                .report();
        }
        fireDeprecatedUsageBuildOperationProgress(usage, diagnostics);
    }

    private static ProblemBuilderDefiningCategory addPossibleLocation(ProblemDiagnostics diagnostics, ProblemBuilderDefiningLocation genericDeprecation) {
        Location location = diagnostics.getLocation();
        if (location == null) {
            return genericDeprecation.noLocation();
        }
        return genericDeprecation.location(location.getSourceLongDisplayName().getDisplayName(), location.getLineNumber());
    }

    private void maybeLogUsage(DeprecatedFeatureUsage usage, ProblemDiagnostics diagnostics) {
        String featureMessage = usage.formattedMessage();
        Location location = diagnostics.getLocation();
        if (!loggedUsages.add(featureMessage) && location == null && diagnostics.getStack().isEmpty()) {
            // This usage does not contain any useful diagnostics and the usage has already been logged, so skip it
            return;
        }
        StringBuilder message = new StringBuilder();
        if (location != null) {
            message.append(location.getFormatted());
            message.append(SystemProperties.getInstance().getLineSeparator());
        }
        message.append(featureMessage);
        if (location != null && !loggedUsages.add(message.toString()) && diagnostics.getStack().isEmpty()) {
            // This usage has no stack trace and has already been logged with the same location, so skip it
            return;
        }
        displayDeprecationIfSameMessageNotDisplayedBefore(message, diagnostics.getStack());
    }

    private void displayDeprecationIfSameMessageNotDisplayedBefore(StringBuilder message, List<StackTraceElement> callStack) {
        // Let's cut the first 10 lines of stack traces as the "key" to identify a deprecation message uniquely.
        // Even when two deprecation messages are emitted from the same location,
        // the stack traces at very bottom might be different due to thread pool scheduling.
        appendLogTraceIfNecessary(message, callStack, 0, 10);
        if (loggedMessages.add(message.toString())) {
            appendLogTraceIfNecessary(message, callStack, 10, callStack.size());
            LOGGER.warn(message.toString());
        }
    }

    private void fireDeprecatedUsageBuildOperationProgress(DeprecatedFeatureUsage usage, ProblemDiagnostics diagnostics) {
        if (progressEventEmitter != null) {
            progressEventEmitter.emitNowIfCurrent(new DefaultDeprecatedUsageProgressDetails(usage, diagnostics));
        }
    }

    public void reset() {
        problemStream = NoOpProblemDiagnosticsFactory.EMPTY_STREAM;
        progressEventEmitter = null;
        loggedMessages.clear();
        loggedUsages.clear();
        deprecationsFound = false;
        error = null;
    }

    public void reportSuppressedDeprecations() {
        if (warningMode == WarningMode.Summary && deprecationsFound) {
            LOGGER.warn("\n{} {}.\n\nYou can use '--{} {}' to show the individual deprecation warnings and determine if they come from your own scripts or plugins.\n\n{}",
                WARNING_SUMMARY, DefaultGradleVersion.current().getNextMajorVersion().getVersion(),
                LoggingConfigurationBuildOptions.WarningsOption.LONG_OPTION, WarningMode.All.name().toLowerCase(),
                DOCUMENTATION_REGISTRY.getDocumentationRecommendationFor("on this", "command_line_interface", "sec:command_line_warnings"));
        }
    }

    private static void appendLogTraceIfNecessary(StringBuilder message, List<StackTraceElement> stack, int startIndexInclusive, int endIndexExclusive) {
        final String lineSeparator = SystemProperties.getInstance().getLineSeparator();

        int endIndex = Math.min(stack.size(), endIndexExclusive);
        if (isTraceLoggingEnabled()) {
            // append full stack trace
            for (int i = startIndexInclusive; i < endIndex; ++i) {
                StackTraceElement frame = stack.get(i);
                appendStackTraceElement(frame, message, lineSeparator);
            }
        } else {
            for (int i = startIndexInclusive; i < endIndex; ++i) {
                StackTraceElement element = stack.get(i);
                if (isGradleScriptElement(element)) {
                    // only print first Gradle script stack trace element
                    appendStackTraceElement(element, message, lineSeparator);
                    appendRunWithStacktraceInfo(message, lineSeparator);
                    return;
                }
            }
        }
    }

    private static void appendStackTraceElement(StackTraceElement frame, StringBuilder message, String lineSeparator) {
        message.append(lineSeparator);
        message.append(ELEMENT_PREFIX);
        message.append(frame);
    }

    private static void appendRunWithStacktraceInfo(StringBuilder message, String lineSeparator) {
        message.append(lineSeparator);
        message.append(RUN_WITH_STACKTRACE_INFO);
    }

    private static boolean isGradleScriptElement(StackTraceElement element) {
        String fileName = element.getFileName();
        if (fileName == null) {
            return false;
        }
        fileName = fileName.toLowerCase(Locale.US);
        if (fileName.endsWith(".gradle") // ordinary Groovy Gradle script
            || fileName.endsWith(".gradle.kts") // Kotlin Gradle script
        ) {
            return true;
        }
        return false;
    }

    /**
     * Whether or not deprecated features should print a full stack trace.
     *
     * This property can be overridden by setting the ORG_GRADLE_DEPRECATION_TRACE_PROPERTY_NAME
     * system property.
     *
     * @param traceLoggingEnabled if trace logging should be enabled.
     */
    public static void setTraceLoggingEnabled(boolean traceLoggingEnabled) {
        LoggingDeprecatedFeatureHandler.traceLoggingEnabled = traceLoggingEnabled;
    }

    static boolean isTraceLoggingEnabled() {
        String value = System.getProperty(ORG_GRADLE_DEPRECATION_TRACE_PROPERTY_NAME);
        if (value == null) {
            return traceLoggingEnabled;
        }
        return Boolean.parseBoolean(value);
    }

    public GradleException getDeprecationFailure() {
        return error;
    }
}
