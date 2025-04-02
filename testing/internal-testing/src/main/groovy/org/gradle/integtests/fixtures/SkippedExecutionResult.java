/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.integtests.fixtures;

import javax.annotation.Nullable;

public class SkippedExecutionResult {
    private final @Nullable String message;
    private final @Nullable String type;
    private final @Nullable String text;

    public SkippedExecutionResult() {
        this(null, null, null);
    }

    public SkippedExecutionResult(@Nullable String message, @Nullable String type, @Nullable String text) {
        this.message = message;
        this.type = type;
        this.text = text;
    }

    @Nullable
    public String getMessage() {
        return message;
    }

    @Nullable
    public String getType() {
        return type;
    }

    @Nullable
    public String getText() {
        return text;
    }
}
