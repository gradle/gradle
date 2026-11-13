/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.tooling.events.problems;

import org.gradle.api.Incubating;

/**
 * Who owns a {@link Frame}.
 *
 * @since 9.10.0
 */
@Incubating
public enum Locality {
    /**
     * The user's build: build scripts, buildSrc and plugins from included builds.
     *
     * @since 9.10.0
     */
    LOCAL,
    /**
     * A third-party plugin.
     *
     * @since 9.10.0
     */
    EXTERNAL,
    /**
     * Gradle itself.
     *
     * @since 9.10.0
     */
    CORE,
    /**
     * The user's machine or CI setup rather than the build, such as an init script from the Gradle user home.
     *
     * @since 9.10.0
     */
    ENVIRONMENT,
    /**
     * Gradle can't tell who owns the frame, or the build sent a locality this client doesn't know.
     *
     * @since 9.10.0
     */
    UNKNOWN
}
