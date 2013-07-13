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

import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.internal.SystemProperties;
import org.gradle.util.SingleMessageLogger;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class LoggingDeprecatedFeatureHandler implements DeprecatedFeatureHandler {
    private static final Logger LOGGER = Logging.getLogger(LoggingDeprecatedFeatureHandler.class);
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
                message.append(SystemProperties.getLineSeparator());
            }
            message.append(usage.getMessage());
            logTraceIfNecessary(usage.getStack(), message);
            LOGGER.warn(message.toString());
        }
    }

    private void logTraceIfNecessary(List<StackTraceElement> stack, StringBuilder message) {
        if (isTraceLoggingEnabled()) {
            for (StackTraceElement frame : stack) {
                message.append(SystemProperties.getLineSeparator());
                message.append("    ");
                message.append(frame.toString());
            }
        }
    }

    private static boolean isTraceLoggingEnabled() {
        return Boolean.getBoolean(SingleMessageLogger.ORG_GRADLE_DEPRECATION_TRACE_PROPERTY_NAME);
    }

}
