/*
 * Copyright 2017 the original author or authors.
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

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;

public class DefaultTaskOutputCachingDisabledReason implements TaskOutputCachingDisabledReason {
    private final TaskOutputCachingDisabledReasonCategory category;
    private final String description;

    DefaultTaskOutputCachingDisabledReason(TaskOutputCachingDisabledReasonCategory category, String description) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(description), "message for disabling must not be null or empty");
        this.category = category;
        this.description = description;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public TaskOutputCachingDisabledReasonCategory getCategory() {
        return category;
    }

    @Override
    public String toString() {
        return description;
    }
}
