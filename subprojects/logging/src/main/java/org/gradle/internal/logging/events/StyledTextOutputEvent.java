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

package org.gradle.internal.logging.events;

import org.gradle.api.logging.LogLevel;
import org.gradle.internal.logging.text.StyledTextOutput;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class StyledTextOutputEvent extends RenderableOutputEvent {
    private static final OperationIdentifier INVALID = new OperationIdentifier(-1);
    private final List<Span> spans;
    private final OperationIdentifier operationId;

    public StyledTextOutputEvent(long timestamp, String category, String text) {
        this(timestamp, category, StyledTextOutput.Style.Normal, text);
    }

    public StyledTextOutputEvent(long timestamp, String category, OperationIdentifier operationId, String text) {
        this(timestamp, category, operationId, StyledTextOutput.Style.Normal, text);
    }

    public StyledTextOutputEvent(long timestamp, String category, LogLevel logLevel, OperationIdentifier operationId, String text) {
        this(timestamp, category, logLevel, operationId, StyledTextOutput.Style.Normal, text);
    }

    public StyledTextOutputEvent(long timestamp, String category, StyledTextOutput.Style style, String text) {
        this(timestamp, category, null, INVALID, style, text);
    }

    public StyledTextOutputEvent(long timestamp, String category, OperationIdentifier operationId, StyledTextOutput.Style style, String text) {
        this(timestamp, category, null, operationId, style, text);
    }

    public StyledTextOutputEvent(long timestamp, String category, LogLevel logLevel, OperationIdentifier operationId, StyledTextOutput.Style style, String text) {
        this(timestamp, category, logLevel, operationId, Collections.singletonList(new Span(style, text)));
    }

    public StyledTextOutputEvent(long timestamp, String category, List<Span> spans) {
        this(timestamp, category, null, INVALID, spans);
    }

    public StyledTextOutputEvent(long timestamp, String category, LogLevel logLevel, OperationIdentifier operationId, Span... spans) {
        this(timestamp, category, logLevel, operationId, Arrays.asList(spans));
    }

    public StyledTextOutputEvent(long timestamp, String category, LogLevel logLevel, OperationIdentifier operationId, List<Span> spans) {
        super(timestamp, category, logLevel);
        this.operationId = operationId;
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
        return new StyledTextOutputEvent(getTimestamp(), getCategory(), logLevel, operationId, spans);
    }

    public OperationIdentifier getOperationId() {
        return operationId;
    }

    public List<Span> getSpans() {
        return spans;
    }

    @Override
    public void render(StyledTextOutput output) {
        output.text(operationId.toString() + "  ");
        for (Span span : spans) {
            output.style(span.style);
            output.text(span.text);
        }
    }

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
