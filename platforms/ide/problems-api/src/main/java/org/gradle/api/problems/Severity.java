/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.api.problems;

import org.gradle.api.Incubating;

/**
 * Represents the severity of a problem reported via the Problems API.
 *
 * <p>Severity controls how a problem is surfaced to the user and whether it causes the build to fail:
 * <ul>
 *   <li>{@link #WARNING} — non-fatal; the build continues.</li>
 *   <li>{@link #ERROR} — fatal; the build fails.</li>
 * </ul>
 *
 * @see ProblemSpec
 * @since 8.6
 */
@Incubating
public enum Severity {
    /**
     * @deprecated This severity level is unused and will be removed in a future release.
     */
    @Deprecated
    ADVICE("Advice"),

    /**
     * Indicates a non-fatal issue that the user should be aware of but that does not prevent the build from succeeding.
     * This is the default severity for every problem report.
     */
    WARNING("Warning"),

    /**
     * Indicates a fatal issue that causes the build to fail.
     */
    ERROR("Error");

    private final String displayName;

    Severity(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
