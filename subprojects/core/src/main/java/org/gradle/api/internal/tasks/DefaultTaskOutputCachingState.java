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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Strings.isNullOrEmpty;

class DefaultTaskOutputCachingState implements TaskOutputCachingState {
    private final boolean enabled;
    private final String disabledReason;

    static final TaskOutputCachingState ENABLED = new DefaultTaskOutputCachingState(true, null);
    static final TaskOutputCachingState DISABLED = disabled("Task output caching is disabled.");
    static TaskOutputCachingState disabled(String disabledReason) {
        return new DefaultTaskOutputCachingState(false, disabledReason);
    }

    private DefaultTaskOutputCachingState(boolean enabled, String disabledReason) {
        if (enabled) {
            checkArgument(disabledReason == null, "disabledReason must be null if task output caching is enabled");
        } else {
            checkArgument(!isNullOrEmpty(disabledReason), "disabledReason must be set if task output caching is disabled");
        }
        this.enabled = enabled;
        this.disabledReason = disabledReason;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public String getDisabledReason() {
        return disabledReason;
    }
}
