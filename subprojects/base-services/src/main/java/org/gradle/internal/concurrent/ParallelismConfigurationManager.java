/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.internal.concurrent;

import org.gradle.concurrent.ParallelismConfiguration;

/**
 * Maintains information about max workers that can be accessed from any scope
 */
public interface ParallelismConfigurationManager {
    /**
     * Get the current parallelism configuration.
     */
    ParallelismConfiguration getParallelismConfiguration();

    /**
     * Change the current parallelism configuration.  This will also send notifications to any listeners.
     */
    void setParallelismConfiguration(ParallelismConfiguration parallelismConfiguration);

    /**
     * Add a new listener to receive notifications when the parallelism configuration changes.
     */
    void addListener(ParallelismConfigurationListener listener);

    /**
     * Remove the specified parallelism configuration listener.
     */
    void removeListener(ParallelismConfigurationListener listener);
}
