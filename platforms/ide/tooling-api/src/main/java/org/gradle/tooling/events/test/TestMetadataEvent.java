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

package org.gradle.tooling.events.test;

import org.gradle.api.Incubating;
import org.gradle.tooling.events.ProgressEvent;
import org.jspecify.annotations.Nullable;

import java.util.Map;

/**
 * An event that informs about a test capturing metadata while running.
 * <p>
 * A new test metadata event instance is created for each metadata reporting event, which
 * might involve multiple values.
 *
 * @since 8.13
 */
@Incubating
public interface TestMetadataEvent extends ProgressEvent {
    /**
     * Returns the key-value data if this event represents a key-value event.
     *
     * @apiNote Since Gradle 9.4.0, this will only return {@code Map<String, String>}.
     *
     * @return map of key-values or an empty collection if this data is some other type
     * @since 8.13
     */
    Map<String, Object> getValues();

    /**
     * Request the data associated with this event as the given type.
     *
     * @apiNote Builds older than Gradle 9.4.0 will never produce events with non-Map data. Check {@link #getValues()} first.
     *
     * @param viewType the type to view the data as
     * @return the data as the given type or null if the data cannot be represented as the given type
     * @param <T> view type
     * @since 9.4.0
     */
    @Nullable
    <T> T get(Class<T> viewType);
}
