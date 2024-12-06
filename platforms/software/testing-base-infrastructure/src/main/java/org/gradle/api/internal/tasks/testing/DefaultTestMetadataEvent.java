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

package org.gradle.api.internal.tasks.testing;

import org.gradle.api.NonNullApi;
import org.gradle.api.tasks.testing.TestMetadataEvent;

/**
 * Default implementation of the {@code TestMetadataEvent} interface.
 */
@NonNullApi
public final class DefaultTestMetadataEvent implements TestMetadataEvent {
    private final long logTime;

    private final String key;
    private final Object value;

    public DefaultTestMetadataEvent(long logTime, String key, Object metadata) {
        this.logTime = logTime;
        this.key = key;
        this.value = metadata;
    }

    @Override
    public long getLogTime() {
        return logTime;
    }

    @Override
    public String getKey() {
        return key;
    }

    @Override
    public Object getValue() {
        return value;
    }
}
