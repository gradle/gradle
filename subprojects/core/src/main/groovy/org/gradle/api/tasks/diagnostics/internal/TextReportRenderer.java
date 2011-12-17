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
import org.gradle.logging.StyledTextOutput;
import org.gradle.logging.internal.StreamingStyledTextOutput;
import org.gradle.util.GUtil;

import java.io.*;

import static org.gradle.logging.StyledTextOutput.Style.Header;
import static org.gradle.logging.StyledTextOutput.Style.Normal;

/**
 * <p>A basic {@link ReportRenderer} which writes out a text report.
 */
public class TextReportRenderer implements ReportRenderer {
    public static final String SEPARATOR = "------------------------------------------------------------";
    private StyledTextOutput textOutput;
    private boolean close;

    public void setOutput(StyledTextOutput textOutput) {
        setWriter(textOutput, false);
    }

    public void setOutputFile(File file) throws IOException {
        cleanupWriter();
        setWriter(new StreamingStyledTextOutput(new BufferedWriter(new FileWriter(file))), true);
    }

    public void startProject(Project project) {
        String header = createHeader(project);
        writeHeading(header);
    }

    protected String createHeader(Project project) {
        String header;
        if (project.getRootProject() == project) {
            header = "Root project";
        } else {
            header = String.format("Project %s", project.getPath());
        }
        if (GUtil.isTrue(project.getDescription())) {
            header = header + " - " + project.getDescription();
        }
        return header;
    }

    public void completeProject(Project project) {
    }

    public void complete() throws IOException {
        cleanupWriter();
    }

    private void setWriter(StyledTextOutput styledTextOutput, boolean close) {
        this.textOutput = styledTextOutput;
        this.close = close;
    }

    private void cleanupWriter() throws IOException {
        try {
            if (textOutput != null && close) {
                ((Closeable) textOutput).close();
            }
        } finally {
            textOutput = null;
        }
    }

    public StyledTextOutput getTextOutput() {
        return textOutput;
    }

    public void writeHeading(String heading) {
        textOutput.println().style(Header);
        textOutput.println(SEPARATOR);
        textOutput.println(heading);
        textOutput.text(SEPARATOR);
        textOutput.style(Normal);
        textOutput.println().println();
    }

    public void writeSubheading(String heading) {
        getTextOutput().style(Header).println(heading);
        for (int i = 0; i < heading.length(); i++) {
            getTextOutput().text("-");
        }
        getTextOutput().style(Normal).println();
    }
}
