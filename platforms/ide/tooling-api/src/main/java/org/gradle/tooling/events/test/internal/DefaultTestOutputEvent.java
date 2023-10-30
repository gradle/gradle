/*
 * Copyright 2019 the original author or authors.
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

import org.gradle.tooling.events.test.TestOutputDescriptor;
import org.gradle.tooling.events.test.TestOutputEvent;

/**
 * Implementation of the {@code TestOutputEvent} interface.
 */
public final class DefaultTestOutputEvent implements TestOutputEvent {

    private final long eventTime;
    private final TestOutputDescriptor descriptor;

    public DefaultTestOutputEvent(long eventTime, TestOutputDescriptor descriptor) {
        this.eventTime = eventTime;
        this.descriptor = descriptor;
    }

    @Override
    public long getEventTime() {
        return eventTime;
    }

    @Override
    public String getDisplayName() {
        return descriptor.getDestination().toString() + ": " + descriptor.getMessage();
    }

    @Override
    public TestOutputDescriptor getDescriptor() {
        return descriptor;
    }

    @Override
    public String toString() {
        return getDisplayName();
    }
}
