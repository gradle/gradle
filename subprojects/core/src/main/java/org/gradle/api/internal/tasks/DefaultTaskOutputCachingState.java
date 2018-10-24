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

import org.gradle.api.internal.TaskOutputCachingState;

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Strings.isNullOrEmpty;

public class DefaultTaskOutputCachingState implements TaskOutputCachingState {
    public static TaskOutputCachingState enabled() {
        return new DefaultTaskOutputCachingState(null, null);
    }
    public static TaskOutputCachingState disabled(TaskOutputCachingDisabledReasonCategory disabledReasonCategory, String disabledReason) {
        checkArgument(!isNullOrEmpty(disabledReason), "disabledReason must be set if task output caching is disabled");
        checkNotNull(disabledReasonCategory, "disabledReasonCategory must be set if task output caching is disabled");
        return new DefaultTaskOutputCachingState(disabledReasonCategory, disabledReason);
    }

    private final String disabledReason;
    private final TaskOutputCachingDisabledReasonCategory disabledReasonCategory;

    private DefaultTaskOutputCachingState(TaskOutputCachingDisabledReasonCategory disabledReasonCategory, String disabledReason) {
        this.disabledReasonCategory = disabledReasonCategory;
        this.disabledReason = disabledReason;
    }

    @Override
    public boolean isEnabled() {
        return disabledReason == null;
    }

    @Nullable
    @Override
    public String getDisabledReason() {
        return disabledReason;
    }

    @Nullable
    @Override
    public TaskOutputCachingDisabledReasonCategory getDisabledReasonCategory() {
        return disabledReasonCategory;
    }

    @Override
    public String toString() {
        return "DefaultTaskOutputCachingState{"
            + "disabledReason='" + disabledReason + '\''
            + ", disabledReasonCategory=" + disabledReasonCategory
            + '}';
    }
}
