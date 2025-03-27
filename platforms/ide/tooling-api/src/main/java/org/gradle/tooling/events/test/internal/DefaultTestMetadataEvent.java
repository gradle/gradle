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

import org.gradle.tooling.events.OperationDescriptor;
import org.gradle.tooling.events.test.TestMetadataEvent;
import org.jspecify.annotations.NullMarked;

import java.util.Map;

/**
 * Implementation of the {@code TestMetadataEvent} interface.
 */
@NullMarked
public class DefaultTestMetadataEvent implements TestMetadataEvent {
    private final long eventTime;
    private final OperationDescriptor descriptor;
    private final Map<String, Object> values;

    public DefaultTestMetadataEvent(long eventTime, OperationDescriptor descriptor, Map<String, Object> values) {
        this.eventTime = eventTime;
        this.descriptor = descriptor;
        this.values = values;
    }

    @Override
    public long getEventTime() {
        return eventTime;
    }

    @Override
    public String getDisplayName() {
        return descriptor.getDisplayName();
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
    public Map<String, Object> getValues() {
        return values;
    }
}
