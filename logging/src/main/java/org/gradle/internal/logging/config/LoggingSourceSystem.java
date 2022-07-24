/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.internal.logging.config;

import org.gradle.api.logging.LogLevel;

/**
 * Represents a logging system that can generate logging events.
 */
public interface LoggingSourceSystem extends LoggingSystem {
    /**
     * Sets the minimum log level for this logging system. This is advisory only, the logging system may generate events at lower priority, but these will be discarded.
     * Logging systems that have no intrinsic levels should generate events at the specified logging level.
     *
     * <p>This method should not have any effect when capture is not enabled for this logging system using {@link #startCapture()}.</p>
     *
     * @param logLevel The minimum log level.
     * @return the state of this logging system immediately before the changes are applied.
     */
    Snapshot setLevel(LogLevel logLevel);

    /**
     * Enables generation of logging events from this logging system. This method is always called after {@link #setLevel(LogLevel)}.
     *
     * @return the state of this logging system immediately before the changes are applied.
     */
    Snapshot startCapture();
}
