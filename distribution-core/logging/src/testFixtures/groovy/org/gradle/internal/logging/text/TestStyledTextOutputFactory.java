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

public class TestStyledTextOutputFactory extends AbstractStyledTextOutputFactory implements StyledTextOutputFactory {
    private final List<StyledTextOutput> textOutputs = new ArrayList<StyledTextOutput>();

    @Override
    public StyledTextOutput create(String logCategory, LogLevel logLevel) {
        StyledTextOutput textOutput = new TestStyledTextOutput();

        if (logCategory != null) {
            textOutput.append("{").append(logCategory).append("}");
        }
        if (logLevel != null) {
            textOutput.append("{").append(logLevel.toString()).append("}");
        }

        textOutputs.add(textOutput);
        return textOutput;
    }

    public String toString() {
        StringBuilder builder = new StringBuilder();
        for (StyledTextOutput textOutput: textOutputs) {
            builder.append(textOutput);
        }
        return builder.toString();
    }

    public void clear() {
        textOutputs.clear();
    }
}
