/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.tooling.events.internal;

import org.gradle.tooling.events.OperationDescriptor;
import org.gradle.tooling.events.ProgressEvent;

/**
 * Base class for {@code ProgressEvent} implementations.
 */
abstract class BaseProgressEvent implements ProgressEvent {

    private final long eventTime;
    private final String displayName;
    private final OperationDescriptor descriptor;

    BaseProgressEvent(long eventTime, String displayName, OperationDescriptor descriptor) {
        this.eventTime = eventTime;
        this.displayName = displayName;
        this.descriptor = descriptor;
    }

    @Override
    public long getEventTime() {
        return eventTime;
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public OperationDescriptor getDescriptor() {
        return descriptor;
    }

    @Override
    public String toString() {
        return getDisplayName();
    }

}
