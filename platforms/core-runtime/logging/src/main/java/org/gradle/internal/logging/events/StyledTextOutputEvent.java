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
import org.gradle.internal.SystemProperties;
import org.gradle.internal.logging.text.StyledTextOutput;
import org.gradle.internal.operations.OperationIdentifier;
import org.gradle.internal.operations.logging.LogEventLevel;
import org.gradle.internal.time.Timestamp;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@SuppressWarnings("deprecation")
public class StyledTextOutputEvent extends RenderableOutputEvent implements org.gradle.internal.logging.events.operations.StyledTextBuildOperationProgressDetails {
    public static final StyledTextOutputEvent.Span EOL = new StyledTextOutputEvent.Span(SystemProperties.getInstance().getLineSeparator());

    private final List<Span> spans;

    public StyledTextOutputEvent(Timestamp timestamp, String category, LogLevel logLevel, @Nullable OperationIdentifier buildOperationIdentifier, String text) {
        this(timestamp, category, logLevel, buildOperationIdentifier, Collections.singletonList(new Span(StyledTextOutput.Style.Normal, text)));
    }

    public StyledTextOutputEvent(Timestamp timestamp, String category, LogLevel logLevel, @Nullable OperationIdentifier buildOperationIdentifier, List<Span> spans) {
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
        return new StyledTextOutputEvent(getTime(), getCategory(), logLevel, getBuildOperationId(), spans);
    }

    @Override
    public StyledTextOutputEvent withBuildOperationId(OperationIdentifier buildOperationId) {
        return new StyledTextOutputEvent(getTime(), getCategory(), getLogLevel(), buildOperationId, spans);
    }

    @Override
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

    @Override
    public LogEventLevel getLevel() {
        return LogLevelConverter.convert(getLogLevel());
    }

    public static class Span implements org.gradle.internal.logging.events.operations.StyledTextBuildOperationProgressDetails.Span {
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

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj == null || obj.getClass() != getClass()) {
                return false;
            }
            Span other = (Span) obj;
            return text.equals(other.text) && style.equals(other.style);
        }

        @Override
        public int hashCode() {
            return text.hashCode() ^ style.hashCode();
        }

        public StyledTextOutput.Style getStyle() {
            return style;
        }

        @Override
        public String getStyleName() {
            return getStyle().name();
        }

        @Override
        public String getText() {
            return text;
        }

        @Override
        public String toString() {
            return style.toString() + ":" + text;
        }
    }
}
