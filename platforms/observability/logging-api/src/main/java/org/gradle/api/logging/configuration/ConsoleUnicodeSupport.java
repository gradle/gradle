/*
 * Copyright 2025 the original author or authors.
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
import org.jspecify.annotations.NullMarked;

/**
 * Specifies how to use Unicode characters in console output.
 *
 * @since 9.4.0
 */
@NullMarked
@Incubating
public enum ConsoleUnicodeSupport {
    /**
     * Automatically detect if unicode characters could be used in the output.
     *
     * @since 9.4.0
     */
    @Incubating
    Auto,

    /**
     * Enable use of unicode characters in the console output.
     *
     * @since 9.4.0
     */
    @Incubating
    Enable,

    /**
     * Disable use of unicode characters in the console output.
     *
     * @since 9.4.0
     */
    @Incubating
    Disable,
}
