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

import com.google.common.collect.Lists;
import org.apache.commons.lang.BooleanUtils;
import org.apache.commons.lang.StringUtils;
import org.gradle.internal.time.Timestamp;

import java.util.List;
import java.util.Locale;

public class BooleanQuestionPromptEvent extends PromptOutputEvent {
    private static final List<String> LENIENT_YES_NO_CHOICES = Lists.newArrayList("yes", "no", "y", "n");
    private final String question;
    private final boolean defaultValue;

    public BooleanQuestionPromptEvent(Timestamp timestamp, String question, boolean defaultValue) {
        super(timestamp);
        this.question = question;
        this.defaultValue = defaultValue;
    }

    @Override
    public String getPrompt() {
        StringBuilder builder = new StringBuilder();
        builder.append(question);
        builder.append(" (default: ");
        String defaultString = defaultValue ? "yes" : "no";
        builder.append(defaultString);
        builder.append(") [");
        builder.append(StringUtils.join(YesNoQuestionPromptEvent.YES_NO_CHOICES, ", "));
        builder.append("] ");
        return builder.toString();
    }

    public String getQuestion() {
        return question;
    }

    public boolean getDefaultValue() {
        return defaultValue;
    }

    @Override
    public PromptResult<Boolean> convert(String text) {
        if (text.isEmpty()) {
            return PromptResult.response(defaultValue);
        }
        String trimmed = text.toLowerCase(Locale.US).trim();
        if (LENIENT_YES_NO_CHOICES.contains(trimmed)) {
            return PromptResult.response(BooleanUtils.toBoolean(trimmed));
        }
        String defaultString = defaultValue ? "yes" : "no";
        return PromptResult.newPrompt("Please enter 'yes' or 'no' (default: '" + defaultString + "'): ");
    }
}
