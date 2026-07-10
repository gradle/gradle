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

/**
 * <p><code>GradleException</code> is the base class of all exceptions thrown by Gradle.</p>
 */
@NullMarked
public class GradleException extends RuntimeException implements ResolutionProvider {
    private final List<String> resolutions = new ArrayList<>();

    public GradleException() { /* Empty */ }

    public GradleException(String message) {
        this(message, (Throwable) null);
    }

    public GradleException(String message, @Nullable Throwable cause) {
        this(message, cause, Collections.emptyList());
    }

    public GradleException(String message, Iterable<String> resolutions) {
        this(message, null, resolutions);
    }

    public GradleException(String message, @Nullable Throwable cause, Iterable<String> resolutions) {
        super(message, cause);
        resolutions.forEach(this.resolutions::add);
    }

    /**
     * Adds a potential resolution to this exception.
     *
     * @since 9.8.0
     */
    @Incubating
    public final void addResolution(String resolution) {
        resolutions.add(resolution);
    }

    /**
     * Clears the resolutions.
     *
     * @since 9.8.0
     */
    @Incubating
    public final void clearResolutions() {
        resolutions.clear();
    }

    @Override
    public List<String> getResolutions() {
        return Collections.unmodifiableList(new ArrayList<>(resolutions));
    }
}
