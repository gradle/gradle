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

package org.gradle.internal.code;

/**
 * Who owns a piece of code, and so who can change it.
 */
public enum Locality {
    /**
     * The user's build: build scripts, buildSrc, plugins from included builds.
     */
    LOCAL,
    /**
     * A third-party plugin from a repository.
     */
    EXTERNAL,
    /**
     * Gradle itself, including its bundled plugins.
     */
    CORE,
    /**
     * The user's machine or CI setup rather than the build: an init script from the Gradle user home or the installation.
     */
    ENVIRONMENT,
    /**
     * Gradle can't tell who owns the code.
     */
    UNKNOWN
}
