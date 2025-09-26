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
package org.gradle.api.tasks.diagnostics.internal;

import org.apache.commons.lang3.StringUtils;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.tasks.diagnostics.internal.text.DefaultTextReportBuilder;
import org.gradle.api.tasks.diagnostics.internal.text.TextReportBuilder;
import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.internal.logging.text.StreamingStyledTextOutput;
import org.gradle.internal.logging.text.StyledTextOutput;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;

/**
 * <p>A basic {@link ReportRenderer} which writes out a text report.
 */
public class TextReportRenderer implements ReportRenderer {
    private @Nullable FileResolver fileResolver;
    private @Nullable StyledTextOutput textOutput;
    private @Nullable TextReportBuilder builder;
    private boolean close;

    public void setFileResolver(FileResolver fileResolver) {
        this.fileResolver = fileResolver;
    }

    @Override
    public void setOutput(StyledTextOutput textOutput) {
        setWriter(textOutput, false);
    }

    @Override
    public void setOutputFile(File file) throws IOException {
        cleanupWriter();
        setWriter(new StreamingStyledTextOutput(Files.newBufferedWriter(file.toPath(), Charset.defaultCharset())), true);
    }

    @SuppressWarnings("DataFlowIssue")
    @Override
    public void startProject(ProjectDetails project) {
        String header = createHeader(project);
        builder.heading(header);
    }

    protected String createHeader(ProjectDetails project) {
        return StringUtils.capitalize(project.getDisplayName());
    }

    @Override
    public void completeProject(ProjectDetails project) {
    }

    @Override
    public void complete() {
        cleanupWriter();
    }

    private void setWriter(StyledTextOutput styledTextOutput, boolean close) {
        this.textOutput = styledTextOutput;
        this.builder = new DefaultTextReportBuilder(textOutput, fileResolver);
        this.close = close;
    }

    private void cleanupWriter() {
        try {
            if (close) {
                //noinspection DataFlowIssue
                CompositeStoppable.stoppable(textOutput).stop();
            }
        } finally {
            textOutput = null;
        }
    }

    @Nullable
    public StyledTextOutput getTextOutput() {
        return textOutput;
    }

    @Nullable
    public TextReportBuilder getBuilder() {
        return builder;
    }
}
