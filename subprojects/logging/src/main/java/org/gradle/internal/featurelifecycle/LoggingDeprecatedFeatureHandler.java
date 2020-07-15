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
import org.gradle.internal.SystemProperties;
import org.gradle.internal.deprecation.DeprecatedFeatureUsage;
import org.gradle.internal.logging.LoggingConfigurationBuildOptions;
import org.gradle.internal.operations.BuildOperationProgressEventEmitter;
import org.gradle.util.GradleVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class LoggingDeprecatedFeatureHandler implements FeatureHandler<DeprecatedFeatureUsage> {
    public static final String ORG_GRADLE_DEPRECATION_TRACE_PROPERTY_NAME = "org.gradle.deprecation.trace";
    public static final String WARNING_SUMMARY = "Deprecated Gradle features were used in this build, making it incompatible with Gradle";
    public static final String WARNING_LOGGING_DOCS_MESSAGE = "See";

    private static final DocumentationRegistry DOCUMENTATION_REGISTRY = new DocumentationRegistry();
    private static final Logger LOGGER = LoggerFactory.getLogger(LoggingDeprecatedFeatureHandler.class);
    private static final String ELEMENT_PREFIX = "\tat ";
    private static final String RUN_WITH_STACKTRACE_INFO = "\t(Run with --stacktrace to get the full stack trace of this deprecation warning.)";
    private static boolean traceLoggingEnabled;

    private final Set<String> messages = new HashSet<String>();
    private UsageLocationReporter locationReporter;

    private WarningMode warningMode = WarningMode.Summary;
    private BuildOperationProgressEventEmitter progressEventEmitter;
    private GradleException error;

    public LoggingDeprecatedFeatureHandler() {
        this.locationReporter = DoNothingReporter.INSTANCE;
    }

    public void init(UsageLocationReporter reporter, WarningMode warningMode, BuildOperationProgressEventEmitter progressEventEmitter) {
        this.locationReporter = reporter;
        this.warningMode = warningMode;
        this.progressEventEmitter = progressEventEmitter;
    }

    @Override
    public void featureUsed(DeprecatedFeatureUsage usage) {
        String featureMessage = usage.formattedMessage();
        if (messages.add(featureMessage)) {
            StringBuilder message = new StringBuilder();
            locationReporter.reportLocation(usage, message);
            if (message.length() > 0) {
                message.append(SystemProperties.getInstance().getLineSeparator());
            }
            message.append(featureMessage);
            appendLogTraceIfNecessary(usage.getStack(), message);
            if (warningMode.shouldDisplayMessages()) {
                LOGGER.warn(message.toString());
            }
            if (warningMode == WarningMode.Fail) {
                if (error == null) {
                    error = new GradleException(WARNING_SUMMARY + " " + GradleVersion.current().getNextMajor().getVersion());
                }
            }
        }
        fireDeprecatedUsageBuildOperationProgress(usage);
    }

    private void fireDeprecatedUsageBuildOperationProgress(DeprecatedFeatureUsage usage) {
        if (progressEventEmitter != null) {
            progressEventEmitter.emitNowIfCurrent(new DefaultDeprecatedUsageProgressDetails(usage));
        }
    }

    public void reset() {
        progressEventEmitter = null;
        messages.clear();
        error = null;
    }

    public void reportSuppressedDeprecations() {
        if (warningMode == WarningMode.Summary && !messages.isEmpty()) {
            LOGGER.warn("\n{} {}.\nUse '--{} {}' to show the individual deprecation warnings.\n{} {}",
                WARNING_SUMMARY, GradleVersion.current().getNextMajor().getVersion(),
                LoggingConfigurationBuildOptions.WarningsOption.LONG_OPTION, WarningMode.All.name().toLowerCase(),
                WARNING_LOGGING_DOCS_MESSAGE, DOCUMENTATION_REGISTRY.getDocumentationFor("command_line_interface", "sec:command_line_warnings"));
        }
    }

    private static void appendLogTraceIfNecessary(List<StackTraceElement> stack, StringBuilder message) {
        final String lineSeparator = SystemProperties.getInstance().getLineSeparator();

        if (isTraceLoggingEnabled()) {
            // append full stack trace
            for (StackTraceElement frame : stack) {
                appendStackTraceElement(frame, message, lineSeparator);
            }
            return;
        }

        for (StackTraceElement element : stack) {
            if (isGradleScriptElement(element)) {
                // only print first Gradle script stack trace element
                appendStackTraceElement(element, message, lineSeparator);
                appendRunWithStacktraceInfo(message, lineSeparator);
                return;
            }
        }
    }

    private static void appendStackTraceElement(StackTraceElement frame, StringBuilder message, String lineSeparator) {
        message.append(lineSeparator);
        message.append(ELEMENT_PREFIX);
        message.append(frame.toString());
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

    private enum DoNothingReporter implements UsageLocationReporter {
        INSTANCE;

        @Override
        public void reportLocation(FeatureUsage usage, StringBuilder target) {
        }
    }

}
