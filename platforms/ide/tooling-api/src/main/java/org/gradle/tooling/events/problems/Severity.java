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

package org.gradle.tooling.events.problems;

import org.gradle.api.Incubating;
import org.gradle.tooling.events.problems.internal.DefaultSeverity;

/**
 * Represents a problem severity.
 *
 * @since 8.6
 */
@Incubating
public interface Severity {

    // Note: the static fields must be in sync with entries from org.gradle.api.problems.Severity.
    /**
     * Advice-level severity.
     *
     * @since 8.6
     */
    Severity ADVICE = new DefaultSeverity(0, true);

    /**
     * Warning-level severity.
     *
     * @since 8.6
     */
    Severity WARNING = new DefaultSeverity(1, true);

    /**
     * Error-level severity.
     *
     * @since 8.6
     */
    Severity ERROR = new DefaultSeverity(2, true);

    /**
     * The severity level represented by a string.
     *
     * @return the severity
     * @since 8.6
     */
    int getSeverity();

    /**
     * Returns true if this severity is one of {@link #ADVICE}, {@link #WARNING}, or {@link #ERROR}.
     *
     * @return if this instance is a known severity
     * @since 8.6
     */
    boolean isKnown();
}
