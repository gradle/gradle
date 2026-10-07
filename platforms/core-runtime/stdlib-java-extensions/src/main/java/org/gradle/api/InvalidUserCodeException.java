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

package org.gradle.api;

import org.gradle.internal.exceptions.Contextual;

import java.util.Objects;

/**
 * A <code>InvalidUserCodeException</code> is thrown when user-provided code cannot be executed.
 * @since 1.4
 */
@Contextual
public class InvalidUserCodeException extends GradleException {
    /**
     * Creates a new {@code InvalidUserCodeException}.
     *
     * @since 1.4
     */
    public InvalidUserCodeException() {
    }

    /**
     * Creates a new {@code InvalidUserCodeException}.
     *
     * @since 1.4
     */
    public InvalidUserCodeException(String message) {
        super(message);
    }

    /**
     * Constructor that allows adding potential resolutions to this exception.
     * <p>
     * Note that {@code new InvalidUserCodeException(message, null)} is ambiguous, because {@code null} matches both
     * this constructor and {@link #InvalidUserCodeException(String, Throwable)}. Java and Kotlin reject such a call
     * at compile time. Groovy resolves overloads from the runtime types, so it selects this constructor and then
     * fails with a {@link NullPointerException}. Either use {@link #InvalidUserCodeException(String)} when there is
     * no cause, or cast the argument - {@code new InvalidUserCodeException(message, (Throwable) null)}.
     *
     * @since 9.9.0
     */
    @Incubating
    @SuppressWarnings("this-escape")
    public InvalidUserCodeException(String message, Iterable<String> resolutions) {
        // Calls super(message) rather than delegating to a cause-taking constructor, since passing an explicit
        // null cause counts as initializing it and makes any later initCause(...) call throw.
        super(message);
        Objects.requireNonNull(
            resolutions,
            "resolutions must not be null. A null second argument is ambiguous between "
                + "InvalidUserCodeException(String, Throwable) and InvalidUserCodeException(String, Iterable); "
                + "cast it to pick one, for example new InvalidUserCodeException(message, (Throwable) null)."
        ).forEach(this::addResolution);
    }

    /**
     * Creates a new {@code InvalidUserCodeException}.
     *
     * @since 1.4
     */
    public InvalidUserCodeException(String message, Throwable cause) {
        super(message, cause);
    }
}
