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

package org.gradle.tooling.events.test.internal;

import org.gradle.api.NonNullApi;
import org.gradle.tooling.events.OperationDescriptor;
import org.gradle.tooling.events.test.TestMetadataEvent;

/**
 * Implementation of the {@code TestMetadataEvent} interface.
 */
@NonNullApi
public class DefaultTestMetadataEvent implements TestMetadataEvent {
    private final long eventTime;
    private final OperationDescriptor descriptor;
    private final String key;
    private final Object value;

    public DefaultTestMetadataEvent(long eventTime, OperationDescriptor descriptor, String key, Object value) {
        this.eventTime = eventTime;
        this.descriptor = descriptor;
        this.key = key;
        this.value = value;
    }

    @Override
    public long getEventTime() {
        return eventTime;
    }

    @Override
    public String getDisplayName() {
        return descriptor.getDisplayName() + " " + key;
    }

    @Override
    public OperationDescriptor getDescriptor() {
        return descriptor;
    }

    @Override
    public String toString() {
        return getDisplayName(); // This must == displayName, see TestEventsFixture
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
