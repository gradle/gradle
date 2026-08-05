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

package org.gradle.internal.time;

/**
 * Provides the current wall clock time and a high-resolution timer for measuring elapsed time.
 */
interface TimeSource {

    /**
     * The current wall clock time, in milliseconds since the epoch.
     *
     * @see System#currentTimeMillis()
     */
    long currentTimeMillis();

    /**
     * The current reading of a high-resolution timer, in nanoseconds. Only to be used for
     * measuring elapsed time. Unrelated to the wall clock.
     *
     * @see System#nanoTime()
     */
    long nanoTime();

}
