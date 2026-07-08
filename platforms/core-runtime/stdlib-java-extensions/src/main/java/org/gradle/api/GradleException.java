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
import org.jspecify.annotations.Nullable;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * <p><code>GradleException</code> is the base class of all exceptions thrown by Gradle.</p>
 */
public class GradleException extends RuntimeException implements ResolutionProvider {
    private final List<String> resolutions;

    public GradleException() {
        this.resolutions = new ArrayList<>();
    }

    public GradleException(String message) {
        super(message);
        this.resolutions = new ArrayList<>();
    }

    public GradleException(String message, @Nullable Throwable cause) {
        super(message, cause);
        this.resolutions = new ArrayList<>();
    }

    public GradleException(String message, List<String> resolutions) {
        super(message);
        this.resolutions = new ArrayList<>(resolutions);
    }

    public GradleException(String message, @Nullable Throwable cause, List<String> resolutions) {
        super(message, cause);
        this.resolutions = new ArrayList<>(resolutions);
    }

    public final void addResolution(String resolution) {
        resolutions.add(resolution);
    }

    public final void clearResolutions() {
        resolutions.clear();
    }

    @NonNull
    @Override
    public final List<String> getResolutions() {
        return Collections.unmodifiableList(resolutions);
    }
}
