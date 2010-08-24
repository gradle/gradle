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
import org.gradle.logging.StyledTextOutput;

public class LoggingBackedStyledTextOutput extends AbstractStyledTextOutput implements LoggingConfigurer {
    private final OutputEventListener listener;
    private final String category;
    private LogLevel logLevel;

    public LoggingBackedStyledTextOutput(OutputEventListener listener, String category, LogLevel logLevel) {
        this.listener = listener;
        this.category = category;
        this.logLevel = logLevel;
    }

    public LogLevel getLogLevel() {
        return logLevel;
    }

    public void configure(LogLevel logLevel) {
        this.logLevel = logLevel;
    }

    public StyledTextOutput text(Object text) {
        listener.onOutput(new LogEvent(category, logLevel, text.toString()));
        return this;
    }
}
