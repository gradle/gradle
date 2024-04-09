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

import org.gradle.internal.Either;

public class SelectOptionPromptEvent extends PromptOutputEvent {
    private final int optionCount;
    private final int defaultOption;

    public SelectOptionPromptEvent(long timestamp, String prompt, int optionCount, int defaultOption) {
        super(timestamp, prompt, true);
        this.optionCount = optionCount;
        this.defaultOption = defaultOption;
    }

    public int getOptionCount() {
        return optionCount;
    }

    public int getDefaultOption() {
        return defaultOption;
    }

    @Override
    public Either<Integer, String> convert(String text) {
        if (text.isEmpty()) {
            return Either.left(defaultOption);
        }
        String trimmed = text.trim();
        if (trimmed.matches("\\d+")) {
            int value = Integer.parseInt(trimmed);
            if (value > 0 && value <= optionCount) {
                return Either.left(value);
            }
        }
        return Either.right("Please enter a value between 1 and " + optionCount + ": ");
    }
}
