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

import org.gradle.internal.SystemProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class LoggingDeprecatedFeatureHandler implements DeprecatedFeatureHandler {
    public static final String ORG_GRADLE_DEPRECATION_TRACE_PROPERTY_NAME = "org.gradle.deprecation.trace";

    private static final Logger LOGGER = LoggerFactory.getLogger(LoggingDeprecatedFeatureHandler.class);
    private static final String ELEMENT_PREFIX = "\tat ";
    private static final String RUN_WITH_STACKTRACE_INFO = "\t(Run with --stacktrace to get the full stack trace of this deprecation warning.)";
    private static boolean traceLoggingEnabled;
    private final Set<String> messages = new HashSet<String>();
    private UsageLocationReporter locationReporter;

    public LoggingDeprecatedFeatureHandler() {
        this(new UsageLocationReporter() {
            public void reportLocation(DeprecatedFeatureUsage usage, StringBuilder target) {
            }
        });
    }

    public LoggingDeprecatedFeatureHandler(UsageLocationReporter locationReporter) {
        this.locationReporter = locationReporter;
    }

    public void setLocationReporter(UsageLocationReporter locationReporter) {
        this.locationReporter = locationReporter;
    }

    public void deprecatedFeatureUsed(DeprecatedFeatureUsage usage) {
        if (messages.add(usage.getMessage())) {
            usage = usage.withStackTrace();
            StringBuilder message = new StringBuilder();
            locationReporter.reportLocation(usage, message);
            if (message.length() > 0) {
                message.append(SystemProperties.getInstance().getLineSeparator());
            }
            message.append(usage.getMessage());
            logTraceIfNecessary(usage.getStack(), message);
            LOGGER.warn(message.toString());
        }
    }

    private static void logTraceIfNecessary(List<StackTraceElement> stack, StringBuilder message) {
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
        if(value == null) {
            return traceLoggingEnabled;
        }
        return Boolean.parseBoolean(value);
    }

}
