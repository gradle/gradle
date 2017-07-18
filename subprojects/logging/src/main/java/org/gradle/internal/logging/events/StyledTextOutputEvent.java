/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.internal.logging.events;

import org.gradle.api.logging.LogLevel;
import org.gradle.internal.logging.text.StyledTextOutput;
import org.gradle.internal.scan.UsedByScanPlugin;

import javax.annotation.Nullable;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@UsedByScanPlugin
public class StyledTextOutputEvent extends RenderableOutputEvent {
    private final List<Span> spans;

    public StyledTextOutputEvent(long timestamp, String category, LogLevel logLevel, @Nullable Object buildOperationIdentifier, String text) {
        this(timestamp, category, logLevel, buildOperationIdentifier, Collections.singletonList(new Span(StyledTextOutput.Style.Normal, text)));
    }

    public StyledTextOutputEvent(long timestamp, String category, LogLevel logLevel, @Nullable Object buildOperationIdentifier, List<Span> spans) {
        super(timestamp, category, logLevel, buildOperationIdentifier);
        this.spans = new ArrayList<Span>(spans);
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append('[').append(getLogLevel()).append("] [");
        builder.append(getCategory()).append("] ");
        for (Span span : spans) {
            builder.append('<');
            builder.append(span.style);
            builder.append(">");
            builder.append(span.text);
            builder.append("</");
            builder.append(span.style);
            builder.append(">");
        }
        return builder.toString();
    }

    public StyledTextOutputEvent withLogLevel(LogLevel logLevel) {
        return new StyledTextOutputEvent(getTimestamp(), getCategory(), logLevel, getBuildOperationId(), spans);
    }

    public List<Span> getSpans() {
        return spans;
    }

    @Override
    public void render(StyledTextOutput output) {
        for (Span span : spans) {
            output.style(span.style);
            output.text(span.text);
        }
    }

    @UsedByScanPlugin
    public static class Span implements Serializable {
        private final String text;
        private final StyledTextOutput.Style style;

        public Span(StyledTextOutput.Style style, String text) {
            this.style = style;
            this.text = text;
        }

        public Span(String text) {
            this.style = StyledTextOutput.Style.Normal;
            this.text = text;
        }

        public StyledTextOutput.Style getStyle() {
            return style;
        }

        public String getText() {
            return text;
        }
    }
}
