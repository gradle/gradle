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

import org.gradle.util.internal.TextUtil;

import java.util.List;

public class SelectOptionPromptEvent extends PromptOutputEvent {
    private final String question;
    private final List<String> options;
    private final int defaultOption;

    public SelectOptionPromptEvent(long timestamp, String question, List<String> options, int defaultOption) {
        super(timestamp);
        this.question = question;
        this.options = options;
        this.defaultOption = defaultOption;
    }

    public String getQuestion() {
        return question;
    }

    public List<String> getOptions() {
        return options;
    }

    public int getDefaultOption() {
        return defaultOption;
    }

    @Override
    public String getPrompt() {
        StringBuilder builder = new StringBuilder();
        builder.append(question);
        builder.append(":");
        builder.append(TextUtil.getPlatformLineSeparator());
        for (int i = 0; i < options.size(); i++) {
            builder.append("  ");
            builder.append(i + 1);
            builder.append(": ");
            builder.append(options.get(i));
            builder.append(TextUtil.getPlatformLineSeparator());
        }
        builder.append("Enter selection (default: ");
        builder.append(options.get(defaultOption));
        builder.append(") [1..");
        builder.append(options.size());
        builder.append("] ");
        return builder.toString();
    }

    @Override
    public PromptResult<Integer> convert(String text) {
        if (text.isEmpty()) {
            return PromptResult.response(defaultOption);
        }
        String trimmed = text.trim();
        if (trimmed.matches("\\d+")) {
            int value = Integer.parseInt(trimmed);
            if (value > 0 && value <= options.size()) {
                return PromptResult.response(value - 1);
            }
        }
        return PromptResult.newPrompt("Please enter a value between 1 and " + options.size() + ": ");
    }
}
