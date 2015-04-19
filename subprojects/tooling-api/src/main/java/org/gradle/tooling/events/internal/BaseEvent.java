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

import org.gradle.tooling.events.Event;

/**
 * Base class for {@code Event} implementations.
 */
abstract class BaseEvent implements Event {

    private final long eventTime;
    private final String eventDescription;

    BaseEvent(long eventTime, String eventDescription) {
        this.eventTime = eventTime;
        this.eventDescription = eventDescription;
    }

    @Override
    public long getEventTime() {
        return eventTime;
    }

    @Override
    public String getDescription() {
        return eventDescription;
    }

}
