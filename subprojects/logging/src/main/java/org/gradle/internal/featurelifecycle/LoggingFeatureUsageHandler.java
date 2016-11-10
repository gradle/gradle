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

import org.gradle.util.TextUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Locale;

/**
 * Notified when a deprecated or incubating feature is used.
 *
 * <p>Implementation does not have to be thread-safe.
 */
class LoggingFeatureUsageHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(LoggingFeatureUsageHandler.class);
    private static final String ELEMENT_PREFIX = "\tat ";
    private UsageLocationReporter locationReporter;

    LoggingFeatureUsageHandler() {
        this(null);
    }

    LoggingFeatureUsageHandler(UsageLocationReporter locationReporter) {
        this.locationReporter = locationReporter;
    }

    void setLocationReporter(UsageLocationReporter locationReporter) {
        this.locationReporter = locationReporter;
    }

    void featureUsed(FeatureUsage usage) {
        StringBuilder message = new StringBuilder();
        if (locationReporter != null) {
            locationReporter.reportLocation(usage, message);
        }
        if (message.length() > 0) {
            message.append('\n');
        }
        message.append(usage.getMessage());
        logStackTrace(usage.getStack(), message);

        // line separators of message are converted at the end since
        // usage.getMessage() may already contain hardcoded \n anyway.
        LOGGER.warn(TextUtil.toPlatformLineSeparators(message.toString()));
    }

    private static void logStackTrace(List<StackTraceElement> stack, StringBuilder message) {
        if (Naggers.isTraceLoggingEnabled()) {
            // append full stack trace
            for (StackTraceElement frame : stack) {
                appendStackTraceElement(frame, message);
            }
            return;
        }

        for (StackTraceElement element : stack) {
            if (isGradleScriptElement(element)) {
                // only print first Gradle script stack trace element
                appendStackTraceElement(element, message);
                return;
            }
        }
    }

    private static void appendStackTraceElement(StackTraceElement frame, StringBuilder message) {
        message.append('\n'); // use normalized line separator
        message.append(ELEMENT_PREFIX);
        message.append(frame.toString());
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
}
