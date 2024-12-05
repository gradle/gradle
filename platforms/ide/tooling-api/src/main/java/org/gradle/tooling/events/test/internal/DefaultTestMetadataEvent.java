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
import org.gradle.tooling.events.EventData;
import org.gradle.tooling.events.OperationDescriptor;
import org.gradle.tooling.events.test.TestMetadataEvent;

/**
 * Implementation of the {@code TestMetadataEvent} interface.
 */
@NonNullApi
public class DefaultTestMetadataEvent implements TestMetadataEvent {
    private final long eventTime;
    private final OperationDescriptor descriptor;
    private final EventData metadata;

    public DefaultTestMetadataEvent(long eventTime, OperationDescriptor descriptor, EventData metadata) {
        this.eventTime = eventTime;
        this.descriptor = descriptor;
        this.metadata = metadata;
    }

    @Override
    public long getEventTime() {
        return eventTime;
    }

    @Override
    public String getDisplayName() {
        // TODO: What to show as a display name for this?
        return descriptor.getDisplayName() + " " + metadata.getClass().getSimpleName();
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
    public EventData getMetadata() {
        return metadata;
    }
}
