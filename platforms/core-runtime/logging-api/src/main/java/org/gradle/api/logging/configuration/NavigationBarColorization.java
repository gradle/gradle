/*
 * Copyright 2024 Gradle and contributors.
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

package org.gradle.api.logging.configuration;

import org.gradle.api.Incubating;

/**
 * Specifies how to colorize the navigation bar levels in console output.
 *
 * @since 8.7
 */
@Incubating
public enum NavigationBarColorization {
    /**
     * Disable navigation bar colorization. Use default console colors.
     */
    OFF,

    /**
     * Enable navigation bar colorization when the current process is attached to a console that supports colors.
     */
    AUTO,

    /**
     * Always enable navigation bar colorization, regardless of console capabilities.
     */
    ON
} 