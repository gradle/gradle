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
package org.gradle.logging.internal;

import org.gradle.api.logging.LogLevel;
import org.gradle.internal.TimeProvider;

import java.util.ArrayList;
import java.util.List;

/**
 * A {@link org.gradle.logging.StyledTextOutput} implementation which generates events of type {@link
 * org.gradle.logging.internal.StyledTextOutputEvent}. This implementation is not thread-safe.
 */
public class LoggingBackedStyledTextOutput extends AbstractLineChoppingStyledTextOutput {
    private final OutputEventListener listener;
    private final String category;
    private final LogLevel logLevel;
    private final TimeProvider timeProvider;
    private final StringBuilder buffer = new StringBuilder();
    private List<StyledTextOutputEvent.Span> spans = new ArrayList<StyledTextOutputEvent.Span>();
    private Style style = Style.Normal;

    public LoggingBackedStyledTextOutput(OutputEventListener listener, String category, LogLevel logLevel, TimeProvider timeProvider) {
        this.listener = listener;
        this.category = category;
        this.logLevel = logLevel;
        this.timeProvider = timeProvider;
    }

    protected void doStyleChange(Style style) {
        if (buffer.length() > 0) {
            spans.add(new StyledTextOutputEvent.Span(this.style, buffer.toString()));
            buffer.setLength(0);
        }
        this.style = style;
    }

    @Override
    protected void doLineText(CharSequence text, boolean terminatesLine) {
        buffer.append(text);
        if (terminatesLine) {
            spans.add(new StyledTextOutputEvent.Span(this.style, buffer.toString()));
            buffer.setLength(0);
            listener.onOutput(new StyledTextOutputEvent(timeProvider.getCurrentTime(), category, logLevel, spans));
            spans = new ArrayList<StyledTextOutputEvent.Span>();
        }
    }
}
