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

package org.gradle.tooling.events;

/**
 * Represents the data associated with an event.
 */
public interface EventData {
    /**
     * Returns the data associated with this event represented by the given type.
     *
     * @param type Representation of the data
     * @return data associated with this event
     * @param <T> type of the data
     */
    <T> T get(Class<T> type);

    String getDisplayName();
}
