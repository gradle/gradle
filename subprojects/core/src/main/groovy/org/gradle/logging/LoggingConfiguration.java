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
package org.gradle.logging;

import org.gradle.api.logging.LogLevel;

import java.io.Serializable;

public class LoggingConfiguration implements Serializable {
    private LogLevel logLevel = LogLevel.LIFECYCLE;
    private ShowStacktrace showStacktrace = ShowStacktrace.INTERNAL_EXCEPTIONS;
    private boolean colorOutput = true;

    public LogLevel getLogLevel() {
        return logLevel;
    }

    public void setLogLevel(LogLevel logLevel) {
        this.logLevel = logLevel;
    }

    /**
     * Returns true if logging output should be displayed in color when Gradle is running in a terminal which supports
     * color output. The default value is true.
     *
     * @return true if logging output should be displayed in color.
     */
    public boolean isColorOutput() {
        return colorOutput;
    }

    /**
     * Specifies whether logging output should be displayed in color.
     *
     * @param colorOutput true if logging output should be displayed in color.
     */
    public void setColorOutput(boolean colorOutput) {
        this.colorOutput = colorOutput;
    }

    public ShowStacktrace getShowStacktrace() {
        return showStacktrace;
    }

    public void setShowStacktrace(ShowStacktrace showStacktrace) {
        this.showStacktrace = showStacktrace;
    }
}
