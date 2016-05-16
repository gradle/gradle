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
     * Enables generation of logging events from this logging source.
     *
     * @param minimumLevel The minimum log level to produce events for, for those logging systems that have intrinsic levels. This is advisory only, the logging system may generate events at lower
     * priority, but these will be discarded.
     * @param defaultLevel The default log level to use, for those logging system that don't have intrinsic levels.
     * @return the state of this logging system immediately before the changes are applied.
     */
    Snapshot on(LogLevel minimumLevel, LogLevel defaultLevel);
}
