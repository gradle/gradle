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
import org.gradle.internal.UncheckedException;
import org.gradle.internal.logging.ConsoleRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class LoggingDeprecatedFeatureHandler implements DeprecatedFeatureHandler {
    public static final String RENDER_REPORT_SYSTEM_PROPERTY = "org.gradle.internal.deprecation.report";
    public static final String BLOCK_SEPARATOR = "\n----------\n";

    private static final Logger LOGGER = LoggerFactory.getLogger(LoggingDeprecatedFeatureHandler.class);
    private static final String ELEMENT_PREFIX = "\tat ";
    private final Map<String, DeprecatedFeatureUsage> deprecationUsages = new LinkedHashMap<String, DeprecatedFeatureUsage>();
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
        if (!deprecationUsages.containsKey(usage.getMessage())) {
            usage = usage.withStackTrace();
            deprecationUsages.put(usage.getMessage(), usage);
        }
    }

    public void renderDeprecationReport(File reportLocation) {
        if (deprecationUsages.isEmpty()) {
            return;
        }
        if (!shouldRenderReport()) {
            LOGGER.warn("\nThere are {} deprecation warnings.", deprecationUsages.size());
            return;
        }

        writeToFile(renderWarnings(), reportLocation);
        LOGGER.warn("\nThere are {} deprecation warnings. See the detailed report at: {}", deprecationUsages.size(), new ConsoleRenderer().asClickableFileUrl(reportLocation));
    }

    private void writeToFile(String content, File file) {
        try {
            Files.createDirectories(file.getParentFile().toPath());
            Files.write(file.toPath(), content.getBytes("UTF-8"));
        } catch (IOException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    private boolean shouldRenderReport() {
        return "true".equals(System.getProperty(RENDER_REPORT_SYSTEM_PROPERTY, "true"));
    }

    private String renderWarnings() {
        StringBuilder result = new StringBuilder();
        int i = 0;
        for (DeprecatedFeatureUsage usage : deprecationUsages.values()) {
            result.append(getStacktrace(usage));
            i++;
            if (i != deprecationUsages.size()) {
                result.append(BLOCK_SEPARATOR);
            }
        }
        return result.toString();
    }

    private String getStacktrace(DeprecatedFeatureUsage usage) {
        StringBuilder sb = new StringBuilder();
        reportLocation(usage, sb);
        sb.append(usage.getMessage());
        appendLogTraceIfNecessary(usage.getStack(), sb);
        return sb.toString();
    }

    private void reportLocation(DeprecatedFeatureUsage usage, StringBuilder message) {
        locationReporter.reportLocation(usage, message);
        if (message.length() > 0) {
            message.append(SystemProperties.getInstance().getLineSeparator());
        }
    }

    private static void appendLogTraceIfNecessary(List<StackTraceElement> stack, StringBuilder message) {
        final String lineSeparator = SystemProperties.getInstance().getLineSeparator();

        // append full stack trace
        for (StackTraceElement frame : stack) {
            appendStackTraceElement(frame, message, lineSeparator);
        }
    }

    private static void appendStackTraceElement(StackTraceElement frame, StringBuilder message, String lineSeparator) {
        message.append(lineSeparator);
        message.append(ELEMENT_PREFIX);
        message.append(frame.toString());
    }
}
