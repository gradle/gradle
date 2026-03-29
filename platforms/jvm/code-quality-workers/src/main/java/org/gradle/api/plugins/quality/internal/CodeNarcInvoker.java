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


package org.gradle.api.plugins.quality.internal;

import com.google.common.collect.ImmutableMap;
import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.RegularFile;
import org.gradle.api.internal.exceptions.MarkedVerificationException;
import org.gradle.api.internal.project.ant.AntLoggingAdapter;
import org.gradle.api.internal.project.antbuilder.AntBuilderDelegate;
import org.gradle.internal.logging.ConsoleRenderer;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.util.List;
import java.util.stream.Collectors;

class CodeNarcInvoker implements Action<AntBuilderDelegate> {

    private static final Logger LOGGER = LoggerFactory.getLogger(CodeNarcInvoker.class);

    private final CodeNarcActionParameters parameters;

    CodeNarcInvoker(CodeNarcActionParameters parameters) {
        this.parameters = parameters;
    }

    @Override
    public void execute(AntBuilderDelegate ant) {
        FileCollection compilationClasspath = parameters.getCompilationClasspath();
        RegularFile configFile = parameters.getConfig().get();
        int maxPriority1Violations = parameters.getMaxPriority1Violations().get();
        int maxPriority2Violations = parameters.getMaxPriority2Violations().get();
        int maxPriority3Violations = parameters.getMaxPriority3Violations().get();
        List<CodeNarcActionParameters.EnabledReport> reports = parameters.getEnabledReports().get();
        boolean ignoreFailures = parameters.getIgnoreFailures().get();
        FileCollection source = parameters.getSource();

        boolean hasConsoleReport = reports.stream().anyMatch(r -> r.getName().get().equals("console"));

        setLifecycleLogLevel(ant, null);
        ant.taskdef(ImmutableMap.of("name", "codenarc", "classname", "org.codenarc.ant.CodeNarcTask"));
        final File[] consoleReportFile = {null};

        // When the console report is enabled, use unlimited max violations so CodeNarc
        // writes report files before we check limits. This lets us read the report and
        // selectively print only violation lines, avoiding verbose boilerplate output.
        int effectiveMax1 = hasConsoleReport ? Integer.MAX_VALUE : maxPriority1Violations;
        int effectiveMax2 = hasConsoleReport ? Integer.MAX_VALUE : maxPriority2Violations;
        int effectiveMax3 = hasConsoleReport ? Integer.MAX_VALUE : maxPriority3Violations;

        try {
            ant.invokeMethod("codenarc",
                ImmutableMap.of(
                    "ruleSetFiles", "file:" + configFile,
                    "maxPriority1Violations", effectiveMax1,
                    "maxPriority2Violations", effectiveMax2,
                    "maxPriority3Violations", effectiveMax3),
                () -> {
                    reports.forEach(r -> {
                        // See https://codenarc.org/codenarc-text-report-writer.html
                        if (r.getName().get().equals("console")) {
                            // Write the text report to a file so we can selectively print only violations
                            consoleReportFile[0] = r.getOutputLocation().getAsFile().get();
                            ant.invokeMethod("report", ImmutableMap.of("type", "text"), () ->
                                ant.invokeMethod("option", ImmutableMap.of("name", "outputFile", "value", consoleReportFile[0]))
                            );
                        } else if (r.getName().get().equals("html")) {
                            ant.invokeMethod("report", ImmutableMap.of("type", "sortable"), () ->
                                ant.invokeMethod("option", ImmutableMap.of("name", "outputFile", "value", r.getOutputLocation().getAsFile().get()))
                            );
                        } else {
                            ant.invokeMethod("report", ImmutableMap.of("type", r.getName().get()), () ->
                                ant.invokeMethod("option", ImmutableMap.of("name", "outputFile", "value", r.getOutputLocation().getAsFile().get()))
                            );
                        }
                    });

                    source.addToAntBuilder(ant, "fileset", FileCollection.AntType.FileSet);

                    if (!compilationClasspath.isEmpty()) {
                        compilationClasspath.addToAntBuilder(ant, "classpath");
                    }
                });

            // When console report is enabled, we bypassed CodeNarc's built-in violation limits.
            // Print any violations from the report, then enforce the original limits ourselves.
            if (hasConsoleReport) {
                printConsoleReport(ant, consoleReportFile[0]);
                checkViolationLimits(reports, ignoreFailures, maxPriority1Violations, maxPriority2Violations, maxPriority3Violations);
            }
        } catch (Exception e) {
            if (e.getMessage().matches("Exceeded maximum number of priority \\d* violations.*")) {
                String message = "CodeNarc rule violations were found.";

                // Find all reports that produced a file
                List<CodeNarcActionParameters.EnabledReport> reportsWithFiles = reports.stream()
                    .filter(it -> !it.getName().get().equals("console"))
                    .collect(Collectors.toList());
                // a report file was generated
                if (!reportsWithFiles.isEmpty()) {
                    CodeNarcActionParameters.EnabledReport humanReadableReport = reportsWithFiles.stream().filter(it -> it.getName().get().equals("html"))
                        .findFirst()
                        .orElse(null);
                    if (humanReadableReport == null) {
                        humanReadableReport = reportsWithFiles.stream().filter(it -> it.getName().get().equals("text"))
                            .findFirst()
                            .orElse(null);
                    }
                    if (humanReadableReport == null) {
                        humanReadableReport = reportsWithFiles.stream().filter(it -> it.getName().get().equals("xml"))
                            .findFirst()
                            .orElse(null);
                    }
                    // Prefer HTML > text > XML and don't include a link if we don't recognize the report format
                    if (humanReadableReport != null) {
                        String reportUrl = new ConsoleRenderer().asClickableFileUrl(humanReadableReport.getOutputLocation().getAsFile().get());
                        message += " See the report at: " + reportUrl;
                    }
                }

                if (ignoreFailures) {
                    LOGGER.warn(message);
                    return;
                }
                throw new MarkedVerificationException(message, e);
            }
            if (e.getMessage().contains("codenarc doesn't support the nested \"classpath\" element.")) {
                String message = "The compilationClasspath property of CodeNarc task can only be non-empty when using CodeNarc 0.27.0 or newer.";
                throw new GradleException(message, e);
            }
            throw e;
        }
    }

    private static void checkViolationLimits(
        List<CodeNarcActionParameters.EnabledReport> reports,
        boolean ignoreFailures,
        int maxPriority1Violations,
        int maxPriority2Violations,
        int maxPriority3Violations
    ) {
        // Re-check the original violation limits by parsing the console report file
        // The IDE report contains lines like: "Violation: Rule=RuleName P=N ..."
        CodeNarcActionParameters.EnabledReport consoleReport = reports.stream()
            .filter(r -> r.getName().get().equals("console"))
            .findFirst()
            .orElse(null);
        if (consoleReport == null) {
            return;
        }
        File reportFile = consoleReport.getOutputLocation().getAsFile().get();
        if (!reportFile.exists()) {
            return;
        }

        int[] counts = {0, 0, 0}; // p1, p2, p3
        try {
            List<String> lines = Files.readAllLines(reportFile.toPath());
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.startsWith("Violation:")) {
                    if (trimmed.contains(" P=1 ")) {
                        counts[0]++;
                    } else if (trimmed.contains(" P=2 ")) {
                        counts[1]++;
                    } else if (trimmed.contains(" P=3 ")) {
                        counts[2]++;
                    }
                }
            }
        } catch (IOException e) {
            return;
        }

        String exceededMessage = null;
        if (counts[0] > maxPriority1Violations) {
            exceededMessage = "Exceeded maximum number of priority 1 violations: " + counts[0] + " (max: " + maxPriority1Violations + ")";
        } else if (counts[1] > maxPriority2Violations) {
            exceededMessage = "Exceeded maximum number of priority 2 violations: " + counts[1] + " (max: " + maxPriority2Violations + ")";
        } else if (counts[2] > maxPriority3Violations) {
            exceededMessage = "Exceeded maximum number of priority 3 violations: " + counts[2] + " (max: " + maxPriority3Violations + ")";
        }

        if (exceededMessage != null) {
            String message = "CodeNarc rule violations were found.";

            // Find report file link (same logic as the catch block)
            List<CodeNarcActionParameters.EnabledReport> reportsWithFiles = reports.stream()
                .filter(it -> !it.getName().get().equals("console"))
                .collect(Collectors.toList());
            if (!reportsWithFiles.isEmpty()) {
                CodeNarcActionParameters.EnabledReport humanReadableReport = reportsWithFiles.stream().filter(it -> it.getName().get().equals("html"))
                    .findFirst()
                    .orElse(reportsWithFiles.stream().filter(it -> it.getName().get().equals("text"))
                        .findFirst()
                        .orElse(reportsWithFiles.stream().filter(it -> it.getName().get().equals("xml"))
                            .findFirst()
                            .orElse(null)));
                if (humanReadableReport != null) {
                    String reportUrl = new ConsoleRenderer().asClickableFileUrl(humanReadableReport.getOutputLocation().getAsFile().get());
                    message += " See the report at: " + reportUrl;
                }
            }

            if (ignoreFailures) {
                LOGGER.warn(message);
                return;
            }
            throw new MarkedVerificationException(message);
        }
    }

    private static void printConsoleReport(AntBuilderDelegate ant, @Nullable File reportFile) {
        if (reportFile == null || !reportFile.exists()) {
            return;
        }
        try {
            List<String> lines = Files.readAllLines(reportFile.toPath());
            boolean hasViolations = lines.stream().anyMatch(line -> line.trim().startsWith("Violation:"));
            if (hasViolations) {
                // Temporarily promote Ant INFO messages to LIFECYCLE so they appear in default output
                setLifecycleLogLevel(ant, "INFO");
                try {
                    Object project = ant.getBuilder().getClass().getMethod("getProject").invoke(ant.getBuilder());
                    for (String line : lines) {
                        String trimmed = line.trim();
                        if (trimmed.startsWith("File:") || trimmed.startsWith("Violation:")) {
                            // Log via Ant project so output is associated with the task
                            project.getClass().getMethod("log", String.class, int.class)
                                .invoke(project, line, 2 /* Project.MSG_INFO */);
                        }
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    setLifecycleLogLevel(ant, null);
                }
            }
        } catch (IOException e) {
            LOGGER.warn("Could not read CodeNarc console report: " + reportFile, e);
        }
    }

    @SuppressWarnings("unchecked")
    static void setLifecycleLogLevel(AntBuilderDelegate ant, @Nullable String lifecycleLogLevel) {
        try {
            Object project = ant.getBuilder().getClass().getMethod("getProject").invoke(ant.getBuilder());
            List<Object> buildListeners = (List<Object>) project.getClass().getMethod("getBuildListeners").invoke(project);
            for (Object it : buildListeners) {
                // We cannot use instanceof or getClass().equals(AntLoggingAdapter.class) since they're in different class loaders
                if (it.getClass().getName().equals(AntLoggingAdapter.class.getName())) {
                    it.getClass().getMethod("setLifecycleLogLevel", String.class).invoke(it, lifecycleLogLevel);
                }
            }
        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }
}
