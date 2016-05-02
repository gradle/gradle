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

import org.gradle.api.Project;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.tasks.diagnostics.internal.text.DefaultTextReportBuilder;
import org.gradle.api.tasks.diagnostics.internal.text.TextReportBuilder;
import org.gradle.initialization.BuildClientMetaData;
import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.internal.logging.text.StyledTextOutput;
import org.gradle.internal.logging.text.StreamingStyledTextOutput;
import org.gradle.util.GUtil;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

/**
 * <p>A basic {@link ReportRenderer} which writes out a text report.
 */
public class TextReportRenderer implements ReportRenderer {
    private BuildClientMetaData clientMetaData;
    private FileResolver fileResolver;
    private StyledTextOutput textOutput;
    private TextReportBuilder builder;
    private boolean close;

    public void setFileResolver(FileResolver fileResolver) {
        this.fileResolver = fileResolver;
    }

    @Override
    public void setClientMetaData(BuildClientMetaData clientMetaData) {
        this.clientMetaData = clientMetaData;
    }

    @Override
    public void setOutput(StyledTextOutput textOutput) {
        setWriter(textOutput, false);
    }

    @Override
    public void setOutputFile(File file) throws IOException {
        cleanupWriter();
        setWriter(new StreamingStyledTextOutput(new BufferedWriter(new FileWriter(file))), true);
    }

    @Override
    public void startProject(Project project) {
        String header = createHeader(project);
        builder.heading(header);
    }

    protected String createHeader(Project project) {
        String header;
        if (project.getRootProject() == project) {
            header = "Root project";
        } else {
            header = "Project " + project.getPath();
        }
        if (GUtil.isTrue(project.getDescription())) {
            header = header + " - " + project.getDescription();
        }
        return header;
    }

    @Override
    public void completeProject(Project project) {
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
                CompositeStoppable.stoppable(textOutput).stop();
            }
        } finally {
            textOutput = null;
        }
    }

    public BuildClientMetaData getClientMetaData() {
        return clientMetaData;
    }

    public StyledTextOutput getTextOutput() {
        return textOutput;
    }

    public TextReportBuilder getBuilder() {
        return builder;
    }

}
