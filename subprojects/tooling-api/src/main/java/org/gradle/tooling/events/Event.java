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
package org.gradle.tooling.events;

import org.gradle.api.Incubating;

/**
 * Root interface for all events propagated through the Tooling API. An event is an abstract concept for an entity
 * that can be monitored having experienced something worthwhile to share. Monitored entities could be tests, tasks,
 * the build, the file system, etc.
 *
 * @since 2.5
 */
@Incubating
public interface Event {

    /**
     * Returns the time this event was triggered. Note that the event time is independent from the time something
     * happened in the underlying entity that is monitored (test, task, build, file system, etc.).
     *
     * @return The event time, in milliseconds since the epoch.
     */
    long getEventTime();

    /**
     * Returns a short description of the event.
     *
     * @return The short description of the event.
     */
    String getDescription();

}
