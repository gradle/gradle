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

package org.gradle.api.internal.tasks;

import javax.annotation.Nullable;

public enum TaskExecutionOutcome {
    FROM_CACHE(true, true, "FROM-CACHE"),
    UP_TO_DATE(true, true, "UP-TO-DATE"),
    SKIPPED(true, false, "SKIPPED"),
    NO_SOURCE(true, false, "NO-SOURCE"),
    EXECUTED(false, false, null);

    private final boolean skipped;
    private final boolean upToDate;
    private final String message;

    TaskExecutionOutcome(boolean skipped, boolean upToDate, @Nullable String message) {
        this.skipped = skipped;
        this.upToDate = upToDate;
        this.message = message;
    }

    public boolean isSkipped() {
        return skipped;
    }

    public boolean isUpToDate() {
        return upToDate;
    }

    @Nullable
    public String getMessage() {
        return message;
    }
}
