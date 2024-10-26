/*
 * Copyright 2024 the original author or authors.
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

import org.gradle.internal.time.Timestamp;

public class IntQuestionPromptEvent extends PromptOutputEvent {
    private final String question;
    private final int minValue;
    private final int defaultValue;

    public IntQuestionPromptEvent(Timestamp timestamp, String question, int minValue, int defaultValue) {
        super(timestamp);
        this.question = question;
        this.minValue = minValue;
        this.defaultValue = defaultValue;
    }

    public String getQuestion() {
        return question;
    }

    public int getMinValue() {
        return minValue;
    }

    public int getDefaultValue() {
        return defaultValue;
    }

    @Override
    public String getPrompt() {
        StringBuilder builder = new StringBuilder();
        builder.append(question);
        builder.append(" (min: ");
        builder.append(minValue);
        builder.append(", default: ");
        builder.append(defaultValue);
        builder.append("): ");
        return builder.toString();
    }

    @Override
    public PromptResult<Integer> convert(String text) {
        if (text.isEmpty()) {
            return PromptResult.response(defaultValue);
        }
        String trimmed = text.trim();
        try {
            int result = Integer.parseInt(trimmed);
            if (result >= minValue) {
                return PromptResult.response(result);
            }
            return PromptResult.newPrompt("Please enter an integer value >= " + minValue + " (default: " + defaultValue + "): ");
        } catch (NumberFormatException e) {
            return PromptResult.newPrompt("Please enter an integer value (min: " + minValue + ", default: " + defaultValue + "): ");
        }
    }
}
