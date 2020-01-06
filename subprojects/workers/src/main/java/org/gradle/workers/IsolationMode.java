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

package org.gradle.workers;

/**
 * Isolation mode for workers.
 *
 * @since 4.0
 */
public enum IsolationMode {
    /**
     * Let Gradle decide, this is the default.
     */
    AUTO,
    /**
     * Don't attempt to isolate the work, use in-process workers.
     */
    NONE,
    /**
     * Isolate the work in it's own classloader, use in-process workers.
     */
    CLASSLOADER,
    /**
     * Isolate the work in a separate process, use out-of-process workers.
     */
    PROCESS
}
