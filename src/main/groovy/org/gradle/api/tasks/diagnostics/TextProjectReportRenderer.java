/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.tasks.diagnostics;

import org.gradle.api.Project;

import java.io.*;
import java.util.Formatter;

/**
 * <p>A basic {@link ProjectReportRenderer} which writes out a text report.
 */
public class TextProjectReportRenderer implements ProjectReportRenderer {
    public static final String SEPARATOR = "------------------------------------------------------------";
    private Appendable writer;
    private boolean close;
    private Formatter formatter;

    public TextProjectReportRenderer() {
        this(System.out);
    }

    public TextProjectReportRenderer(Appendable writer) {
        setWriter(writer, false);
    }

    public void setOutputFile(File file) throws IOException {
        cleanupWriter();
        setWriter(new FileWriter(file), true);
    }

    public void startProject(Project project) {
        formatter.format("%n%s%n", SEPARATOR);
        if (project.getRootProject() == project) {
            formatter.format("Root Project %s%n", project.getPath());
        } else {
            formatter.format("Project %s%n", project.getPath());
        }
        formatter.format("%s%n", SEPARATOR);
    }

    public void completeProject(Project project) {
    }

    public void complete() throws IOException {
        cleanupWriter();
        setWriter(System.out, false);
    }

    private void setWriter(Appendable writer, boolean close) {
        this.writer = writer;
        this.close = close;
        formatter = new Formatter(writer);
    }

    private void cleanupWriter() throws IOException {
        formatter.flush();
        if (close) {
            ((Closeable) writer).close();
        }
    }

    protected Appendable getWriter() {
        return writer;
    }

    protected Formatter getFormatter() {
        return formatter;
    }
}
