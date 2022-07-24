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
package org.gradle.internal.logging;

import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.logging.configuration.ConsoleOutput;
import org.gradle.api.logging.configuration.LoggingConfiguration;
import org.gradle.api.logging.configuration.ShowStacktrace;
import org.gradle.api.logging.configuration.WarningMode;

import java.io.Serializable;

public class DefaultLoggingConfiguration implements Serializable, LoggingConfiguration {
    private LogLevel logLevel = LogLevel.LIFECYCLE;
    private ShowStacktrace showStacktrace = ShowStacktrace.INTERNAL_EXCEPTIONS;
    private ConsoleOutput consoleOutput = ConsoleOutput.Auto;
    private WarningMode warningMode =  WarningMode.Summary;

    public boolean equals(Object obj) {
        return EqualsBuilder.reflectionEquals(this, obj);
    }

    public int hashCode() {
        return HashCodeBuilder.reflectionHashCode(this);
    }

    @Override
    public LogLevel getLogLevel() {
        return logLevel;
    }

    @Override
    public void setLogLevel(LogLevel logLevel) {
        this.logLevel = logLevel;
    }

    @Override
    public ConsoleOutput getConsoleOutput() {
        return consoleOutput;
    }

    @Override
    public void setConsoleOutput(ConsoleOutput consoleOutput) {
        this.consoleOutput = consoleOutput;
    }

    @Override
    public WarningMode getWarningMode() {
        return warningMode;
    }

    @Override
    public void setWarningMode(WarningMode warningMode) {
        this.warningMode = warningMode;
    }

    @Override
    public ShowStacktrace getShowStacktrace() {
        return showStacktrace;
    }

    @Override
    public void setShowStacktrace(ShowStacktrace showStacktrace) {
        this.showStacktrace = showStacktrace;
    }
}
