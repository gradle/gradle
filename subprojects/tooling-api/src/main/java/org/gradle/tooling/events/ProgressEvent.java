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
 * Root interface for all events that signal progress while executing an operation. For example, an operation can be
 * the execution of a build, of a task, of a test, etc. A progress event can, for example, signal that a test has started, a
 * task has finished, etc.
 *
 * @since 2.4
 */
@Incubating
public interface ProgressEvent {

    /**
     * Returns the time this event was triggered.
     *
     * @return The event time, in milliseconds since the epoch.
     */
    long getEventTime();

    /**
     * Returns a human consumable short description of the event.
     *
     * @return The short description of the event.
     */
    String getDisplayName();

    /**
     * Returns the description of the operation for which progress is reported.
     *
     * @return The description of the operation.
     */
    OperationDescriptor getDescriptor();

}
