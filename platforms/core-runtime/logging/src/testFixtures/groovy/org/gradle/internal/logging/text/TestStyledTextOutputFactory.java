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

package org.gradle.internal.logging.text;

import org.gradle.api.logging.LogLevel;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Test fixture for {@link StyledTextOutputFactory} that tracks the styled text produced.
 *
 * This is intended to be used in tests and only in cases where a single category/log level is used at a time.
 *
 * This fixture allows tests to assert that the correct log category, log level and styled output has been produced.
 */
public class TestStyledTextOutputFactory extends AbstractStyledTextOutputFactory implements StyledTextOutputFactory {
    private final List<StyledTextOutput> textOutputs = new ArrayList<StyledTextOutput>();
    private String category;
    private LogLevel logLevel;

    /**
     * {@inheritDoc}
     *
     * @implNote This implementation tracks the output of the most recently created category and log level. If the category or log level
     * changes, the tracked output is reset.
     */
    @Override
    public StyledTextOutput create(String logCategory, LogLevel logLevel) {
        if (!Objects.equals(this.category, logCategory) || !Objects.equals(this.logLevel, logLevel)) {
            reset();
            this.category = logCategory;
            this.logLevel = logLevel;
        }

        StyledTextOutput textOutput = new TestStyledTextOutput();
        textOutputs.add(textOutput);
        return textOutput;
    }

    @Override
    public String toString() {
        return getOutput();
    }

    /**
     * @return the output that has been seen so far.
     */
    public String getOutput() {
        StringBuilder builder = new StringBuilder();
        for (StyledTextOutput textOutput: textOutputs) {
            builder.append(textOutput);
        }
        return builder.toString();
    }

    /**
     * @return category last seen by the factory.
     */
    public String getCategory() {
        return category;
    }

    /**
     * @return log level last seen by the factory.
     */
    public LogLevel getLogLevel() {
        return logLevel;
    }

    private void reset() {
        textOutputs.clear();
        category = null;
        logLevel = null;
    }
}
