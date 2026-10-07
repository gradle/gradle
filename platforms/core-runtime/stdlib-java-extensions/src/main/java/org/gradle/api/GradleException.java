/*
 * Copyright 2007 the original author or authors.
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

import org.gradle.internal.exceptions.ResolutionProvider;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * The base class of all exceptions thrown by Gradle.
 * <p>
 * Implements {@link ResolutionProvider} as of Gradle 9.9, to make it simple to provide a list of resolutions
 * for any exception.
 * <p>
 * Deliberately calls {@code super(X, Y, Z...)} constructors instead of using a telescoping constructor pattern,
 * since passing an explicit null cause counts as initializing it, which makes any later
 * {@link #initCause(Throwable)} call throw {@link IllegalStateException}.
 *
 * @since 0.7
 */
@NullMarked
public class GradleException extends RuntimeException implements ResolutionProvider {
    private final List<String> resolutions = new ArrayList<>();

    /**
     * Creates a new {@code GradleException}.
     *
     * @since 0.7
     */
    public GradleException() { /* Empty */ }

    /**
     * Creates a new {@code GradleException}.
     *
     * @since 0.7
     */
    public GradleException(String message) {
        super(message);
    }

    /**
     * Creates a new {@code GradleException}.
     *
     * @since 0.7
     */
    public GradleException(String message, @Nullable Throwable cause) {
        super(message, cause);
    }

    /**
     * Creates a new {@code GradleException} carrying the given resolution suggestions.
     * <p>
     * There is deliberately no {@code (String, Iterable)} overload. It would make
     * {@code new GradleException(message, null)} ambiguous with {@link #GradleException(String, Throwable)}:
     * Java and Kotlin would reject such a call at compile time, and Groovy, which resolves overloads from the
     * runtime types, would select the resolutions constructor and fail on the null. To attach resolutions
     * without a cause, use {@link #GradleException(String)} followed by {@link #addResolution(String)}.
     * <p>
     * Note that passing a {@code null} cause here still counts as initializing it, so a later
     * {@link #initCause(Throwable)} call on the result throws {@link IllegalStateException}.
     *
     * @since 9.9.0
     */
    @Incubating
    public GradleException(String message, @Nullable Throwable cause, Iterable<String> resolutions) {
        super(message, cause);
        Objects.requireNonNull(resolutions, "resolutions must not be null").forEach(this.resolutions::add);
    }

    /**
     * Adds a potential resolution to this exception.
     *
     * @since 9.9.0
     */
    @Incubating
    public void addResolution(String resolution) {
        resolutions.add(resolution);
    }

    /**
     * Clears the resolutions.
     *
     * @since 9.9.0
     */
    @Incubating
    public void clearResolutions() {
        resolutions.clear();
    }

    /**
     * Gets the resolutions.
     * <p>
     * This is left non-{@code final} to allow {@link org.gradle.internal.exceptions.DefaultMultiCauseException DefaultMultiCauseException}
     * to override it.
     *
     * @since 9.9.0
     */
    @Incubating
    @Override
    public List<String> getResolutions() {
        return Collections.unmodifiableList(new ArrayList<>(resolutions));
    }
}
