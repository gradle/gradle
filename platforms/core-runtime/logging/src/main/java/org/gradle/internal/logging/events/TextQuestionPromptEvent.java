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

public class TextQuestionPromptEvent extends PromptOutputEvent {
    private final String question;
    private final String defaultValue;

    public TextQuestionPromptEvent(long timestamp, String question, String defaultValue) {
        super(timestamp);
        this.question = question;
        this.defaultValue = defaultValue;
    }

    public String getQuestion() {
        return question;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    @Override
    public String getPrompt() {
        StringBuilder builder = new StringBuilder();
        builder.append(question);
        builder.append(" (default: ");
        builder.append(defaultValue);
        builder.append("): ");
        return builder.toString();
    }

    @Override
    public PromptResult<String> convert(String text) {
        if (text.isEmpty()) {
            return PromptResult.response(defaultValue);
        }
        return PromptResult.response(text);
    }
}
