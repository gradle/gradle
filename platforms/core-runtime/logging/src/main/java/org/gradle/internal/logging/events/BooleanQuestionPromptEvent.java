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
import org.gradle.internal.Either;

import java.util.List;
import java.util.Locale;

public class BooleanQuestionPromptEvent extends PromptOutputEvent {
    private static final List<String> LENIENT_YES_NO_CHOICES = Lists.newArrayList("yes", "no", "y", "n");
    private final boolean defaultValue;
    private final String defaultString;

    public BooleanQuestionPromptEvent(long timestamp, String prompt, boolean defaultValue, String defaultString) {
        super(timestamp, prompt, true);
        this.defaultValue = defaultValue;
        this.defaultString = defaultString;
    }

    public boolean getDefaultValue() {
        return defaultValue;
    }

    public String getDefaultString() {
        return defaultString;
    }

    @Override
    public Either<Boolean, String> convert(String text) {
        if (text.isEmpty()) {
            return Either.left(defaultValue);
        }
        String trimmed = text.toLowerCase(Locale.US).trim();
        if (LENIENT_YES_NO_CHOICES.contains(trimmed)) {
            return Either.left(BooleanUtils.toBoolean(trimmed));
        }
        return Either.right("Please enter 'yes' or 'no' (default: '" + defaultString + "'): ");
    }
}
