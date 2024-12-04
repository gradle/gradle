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

package org.gradle.api.tasks.testing;

import org.gradle.api.Incubating;

/**
 * Metadata captured during the execution of a test.
 *
 * @since 8.12
 */
@Incubating
public interface TestMetadataEvent {
    /**
     * The time the message was logged.
     * <p>
     * Producers can supply the same value as the test start time to indicate that the metadata is "timeless", such
     * as environment information that isn't tied to a specific point during test execution.
     *
     * @return log time, in milliseconds since UNIX epoch
     * @since 8.12
     */
    long getLogTime();

    /**
     * Retrieves the key used to identify the metadata for this event.
     *
     * @return the event metadata key
     * @since 8.12
     */
    String getKey();

    /**
     * Retrieves the recorded metadata for this event.
     *
     * @return the event metadata
     * @since 8.12
     */
    Object getValue();
}
