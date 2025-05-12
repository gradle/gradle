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

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Intended to be the base class of all exceptions thrown by Gradle.
 * <p>
 * Implements {@link ResolutionProvider} to allow the thrower to provide potential resolutions for an exception
 * that Gradle will display.
 */
public class GradleException extends RuntimeException implements ResolutionProvider {
    private final List<String> resolutions = new ArrayList<>();

    public GradleException() {
        super();
    }

    public GradleException(String message) {
        this(message, Collections.emptyList());
    }

    public GradleException(String message, List<String> resolutions) {
        this(message, resolutions, null);
    }

    public GradleException(String message, @Nullable Throwable cause) {
        this(message, Collections.emptyList(), cause);
    }

    public GradleException(String message, List<String> resolutions, @Nullable Throwable cause) {
        super(message, cause);
        this.resolutions.addAll(resolutions);
    }

    @Override
    public List<String> getResolutions() {
        return new ArrayList<>(resolutions);
    }
}
