/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.api.tasks.diagnostics.internal.text;

import org.gradle.api.UncheckedIOException;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.logging.StyledTextOutput;
import org.gradle.logging.internal.LinePrefixingStyledTextOutput;
import org.gradle.reporting.ReportRenderer;

import java.io.File;
import java.io.IOException;
import java.util.Collection;

import static org.gradle.logging.StyledTextOutput.Style.Header;
import static org.gradle.logging.StyledTextOutput.Style.Normal;

public class DefaultTextReportBuilder implements TextReportBuilder {
    public static final String SEPARATOR = "------------------------------------------------------------";
    private StyledTextOutput textOutput;
    private final FileResolver fileResolver;

    public DefaultTextReportBuilder(StyledTextOutput textOutput, FileResolver fileResolver) {
        this.textOutput = textOutput;
        this.fileResolver = fileResolver;
    }

    public void item(String title, String value) {
        textOutput.append("    ").append(title).append(": ");
        StyledTextOutput itemOutput = new LinePrefixingStyledTextOutput(textOutput, "    ");
        itemOutput.append(value).println();
    }

    public void item(String title, File value) {
        item(title, fileResolver.resolveAsRelativePath(value));
    }

    public void item(String value) {
        textOutput.append("    ");
        StyledTextOutput itemOutput = new LinePrefixingStyledTextOutput(textOutput, "    ");
        itemOutput.append(value).println();
    }

    public void item(File value) {
        item(fileResolver.resolveAsRelativePath(value));
    }

    public void heading(String heading) {
        textOutput.println().style(Header);
        textOutput.println(SEPARATOR);
        textOutput.println(heading);
        textOutput.text(SEPARATOR);
        textOutput.style(Normal);
        textOutput.println().println();
    }

    public void subheading(String heading) {
        textOutput.style(Header).println(heading);
        for (int i = 0; i < heading.length(); i++) {
            textOutput.text("-");
        }
        textOutput.style(Normal).println();
    }

    public <T> void collection(String title, Collection<? extends T> items, ReportRenderer<T, TextReportBuilder> renderer, String elementsPlural) {
        textOutput.println(title);
        if (items.isEmpty()) {
            textOutput.formatln("    No %s.", elementsPlural);
            return;
        }
        collection(items, renderer);
    }

    @Override
    public <T> void collection(Iterable<? extends T> items, ReportRenderer<T, TextReportBuilder> renderer) {
        StyledTextOutput original = textOutput;
        boolean hasItem = false;
        try {
            textOutput = new LinePrefixingStyledTextOutput(original, "    ");
            for (T t : items) {
                // TODO - change LinePrefixingStyledTextOutput to prefix every line
                if (!hasItem) {
                    textOutput.append("    ");
                    hasItem = true;
                }
                try {
                    renderer.render(t, this);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        } finally {
            textOutput = original;
        }
    }

    public StyledTextOutput getOutput() {
        return textOutput;
    }
}
