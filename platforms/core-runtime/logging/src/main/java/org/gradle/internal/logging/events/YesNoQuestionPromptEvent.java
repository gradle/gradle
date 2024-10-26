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

public class YesNoQuestionPromptEvent extends PromptOutputEvent {
    public static final List<String> YES_NO_CHOICES = Lists.newArrayList("yes", "no");
    private final String question;

    public YesNoQuestionPromptEvent(Timestamp timestamp, String question) {
        super(timestamp);
        this.question = question;
    }

    public String getQuestion() {
        return question;
    }

    @Override
    public String getPrompt() {
        StringBuilder builder = new StringBuilder();
        builder.append(question);
        builder.append(" [");
        builder.append(StringUtils.join(YesNoQuestionPromptEvent.YES_NO_CHOICES, ", "));
        builder.append("] ");
        return builder.toString();
    }

    @Override
    public PromptResult<Boolean> convert(String text) {
        String trimmed = text.trim();
        if (YES_NO_CHOICES.contains(trimmed)) {
            return PromptResult.response(BooleanUtils.toBoolean(trimmed));
        }
        return PromptResult.newPrompt("Please enter 'yes' or 'no': ");
    }
}
