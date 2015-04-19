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

import org.gradle.tooling.Descriptor;
import org.gradle.tooling.TestDescriptor;
import org.gradle.tooling.events.StartEvent;

/**
 * Default implementation of the {@code StartEvent} interface.
 */
public final class DefaultStartEvent implements StartEvent {

    private final long eventTme;
    private final String eventDescription;
    private final Descriptor descriptor;

    public DefaultStartEvent(long eventTme, String eventDescription, TestDescriptor descriptor) {
        this.eventTme = eventTme;
        this.eventDescription = eventDescription;
        this.descriptor = descriptor;
    }

    @Override
    public long getEventTime() {
        return eventTme;
    }

    @Override
    public String getDescription() {
        return eventDescription;
    }

    @Override
    public Descriptor getDescriptor() {
        return descriptor;
    }

}
