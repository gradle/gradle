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

import com.google.common.base.Joiner;
import org.gradle.api.Action;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.internal.logging.text.StyledTextOutput;
import org.gradle.internal.logging.text.LinePrefixingStyledTextOutput;
import org.gradle.reporting.ReportRenderer;

import java.io.File;
import java.io.IOException;
import java.util.Collection;

import static org.gradle.internal.logging.text.StyledTextOutput.Style.Header;
import static org.gradle.internal.logging.text.StyledTextOutput.Style.Normal;

public class DefaultTextReportBuilder implements TextReportBuilder {
    public static final String SEPARATOR = "------------------------------------------------------------";
    public static final String INDENT = "    ";
    private int depth;
    private boolean hasTitledItems;
    private StyledTextOutput textOutput;
    private final FileResolver fileResolver;

    public DefaultTextReportBuilder(StyledTextOutput textOutput, FileResolver fileResolver) {
        this.textOutput = textOutput;
        this.fileResolver = fileResolver;
    }

    @Override
    public void item(String title, String value) {
        hasTitledItems = true;
        StyledTextOutput itemOutput = new LinePrefixingStyledTextOutput(textOutput, INDENT, false);
        itemOutput.append(title).append(": ");
        itemOutput.append(value).println();
    }

    @Override
    public void item(String title, File value) {
        item(title, fileResolver.resolveForDisplay(value));
    }

    @Override
    public void item(String value) {
        hasTitledItems = true;
        textOutput.append(value).println();
    }

    @Override
    public void item(String title, Iterable<String> values) {
        item(title, Joiner.on(", ").join(values));
    }

    @Override
    public void item(File value) {
        item(fileResolver.resolveForDisplay(value));
    }

    @Override
    public <T> void item(final T value, final ReportRenderer<T, TextReportBuilder> renderer) {
        renderItem(textReportBuilder -> {
            try {
                renderer.render(value, textReportBuilder);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    @Override
    public void heading(String heading) {
        if (depth == 0) {
            textOutput.println().style(Header);
            textOutput.println(SEPARATOR);
            textOutput.println(heading);
            textOutput.text(SEPARATOR);
            textOutput.style(Normal);
            textOutput.println().println();
        } else if (depth == 1) {
            writeSubheading(heading);
        } else {
            textOutput.println(heading);
            textOutput = new LinePrefixingStyledTextOutput(textOutput, INDENT);
        }
        depth++;
    }

    @Override
    public void subheading(String heading) {
        writeSubheading(heading);
        if (depth == 0) {
            depth = 1;
        }
    }

    private void writeSubheading(String heading) {
        textOutput.style(Header).println(heading);
        for (int i = 0; i < heading.length(); i++) {
            textOutput.text("-");
        }
        textOutput.style(Normal).println();
    }

    @Override
    public <T> void collection(String title, Collection<? extends T> items, ReportRenderer<T, TextReportBuilder> renderer, String elementsPlural) {
        if (depth <= 1 && !hasTitledItems) {
            writeSubheading(title);
        } else if (depth == 2 && !hasTitledItems) {
            textOutput.println(title);
        } else {
            textOutput.append(title).println(":");
        }
        if (items.isEmpty()) {
            textOutput.formatln("    No %s.", elementsPlural);
            return;
        }
        collection(items, renderer);
    }

    @Override
    public <T> void collection(final Iterable<? extends T> items, final ReportRenderer<T, TextReportBuilder> renderer) {
        for (final T t : items) {
            nested(textReportBuilder -> {
                try {
                    renderer.render(t, textReportBuilder);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        }
    }

    @Override
    public StyledTextOutput getOutput() {
        return textOutput;
    }

    private void nested(final Action<TextReportBuilder> action) {
        final boolean indent = depth > 1 || hasTitledItems;
        renderItem(textReportBuilder -> {
            if (indent) {
                textOutput = new LinePrefixingStyledTextOutput(textOutput, INDENT);
            }
            depth++;
            action.execute(textReportBuilder);
        });
    }

    private void renderItem(Action<TextReportBuilder> action) {
        StyledTextOutput original = textOutput;
        int originalDepth = depth;
        boolean originalItems = hasTitledItems;
        try {
            hasTitledItems = false;
            action.execute(this);
        } finally {
            textOutput = original;
            depth = originalDepth;
            hasTitledItems = originalItems;
        }
    }

}
