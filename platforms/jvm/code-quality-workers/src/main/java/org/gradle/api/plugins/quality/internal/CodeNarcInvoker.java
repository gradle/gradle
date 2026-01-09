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
import org.gradle.api.GradleException;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.RegularFile;
import org.gradle.api.internal.exceptions.MarkedVerificationException;
import org.gradle.api.internal.file.FileCollectionInternal;
import org.gradle.api.internal.project.ant.AntLoggingAdapter;
import org.gradle.api.internal.project.antbuilder.AntBuilderDelegate;
import org.gradle.api.plugins.internal.ant.AntWorkAction;
import org.gradle.internal.logging.ConsoleRenderer;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.stream.Collectors;

public abstract class CodeNarcInvoker extends AntWorkAction<CodeNarcActionParameters> {

    private static final Logger LOGGER = LoggerFactory.getLogger(CodeNarcInvoker.class);

    @Override
    protected String getActionName() {
        return "codenarc";
    }

    @Override
    public void execute(AntBuilderDelegate ant) {
        CodeNarcActionParameters parameters = getParameters();
        FileCollection compilationClasspath = parameters.getCompilationClasspath();
        RegularFile configFile = parameters.getConfig().get();
        int maxPriority1Violations = parameters.getMaxPriority1Violations().get();
        int maxPriority2Violations = parameters.getMaxPriority2Violations().get();
        int maxPriority3Violations = parameters.getMaxPriority3Violations().get();
        List<CodeNarcActionParameters.EnabledReport> reports = parameters.getEnabledReports().get();
        boolean ignoreFailures = parameters.getIgnoreFailures().get();
        FileCollection source = parameters.getSource();

        setLifecycleLogLevel(ant, null);
        ant.taskdef("codenarc", "org.codenarc.ant.CodeNarcTask");
        try {
            ant.createNode("codenarc",
                ImmutableMap.of(
                    "ruleSetFiles", "file:" + configFile,
                    "maxPriority1Violations", maxPriority1Violations,
                    "maxPriority2Violations", maxPriority2Violations,
                    "maxPriority3Violations", maxPriority3Violations),
                () -> {
                    reports.forEach(r -> {
                        // See https://codenarc.org/codenarc-text-report-writer.html
                        if (r.getName().get().equals("console")) {
                            // The output from Ant is written at INFO level
                            setLifecycleLogLevel(ant, "INFO");

                            // Prefer to use the IDE based formatter because this produces a useful/clickable link to the violation on the console
                            ant.createNode("report", ImmutableMap.of("type", "ide"), () ->
                                ant.createNode("option", ImmutableMap.of("name", "writeToStandardOut", "value", true))
                            );
                        } else if (r.getName().get().equals("html")) {
                            ant.createNode("report", ImmutableMap.of("type", "sortable"), () ->
                                ant.createNode("option", ImmutableMap.of("name", "outputFile", "value", r.getOutputLocation().getAsFile().get()))
                            );
                        } else {
                            ant.createNode("report", ImmutableMap.of("type", r.getName().get()), () ->
                                ant.createNode("option", ImmutableMap.of("name", "outputFile", "value", r.getOutputLocation().getAsFile().get()))
                            );
                        }
                    });

                    ant.addDirectoryTrees("fileset", ((FileCollectionInternal) source).getAsDirectoryTrees());

                    if (!compilationClasspath.isEmpty()) {
                        ant.addFiles("classpath", compilationClasspath);
                    }
                });
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

    @SuppressWarnings("unchecked")
    static void setLifecycleLogLevel(AntBuilderDelegate ant, @Nullable String lifecycleLogLevel) {
        try {
            List<Object> buildListeners = (List<Object>) ant.getProject().invokeMethod("getBuildListeners");
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
