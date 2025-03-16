/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice;

import com.google.common.collect.ImmutableList;
import org.gradle.api.artifacts.ResolveException;

import java.util.List;

/**
 * An internal specialization of the public {@link ResolveException}. All resolve exceptions thrown
 * by Gradle are assumed to be an instance of this class.
 */
public class TypedResolveException extends ResolveException {
    private final String type;
    private final ImmutableList<String> resolutions;

    /**
     * Creates a new instance without resolutions.
     */
    public TypedResolveException(String type, String displayName, Iterable<? extends Throwable> failures) {
        this(type, displayName, failures, ImmutableList.of());
    }

    /**
     * Creates a new instance with resolutions.
     */
    public TypedResolveException(String type, String displayName, Iterable<? extends Throwable> failures, List<String> resolutions) {
        super(buildMessage(type, displayName), failures, false);
        this.type = type;
        this.resolutions = ImmutableList.copyOf(resolutions);
    }

    /**
     * The type. This is used to build the failure message.
     *
     * Usually "dependencies", "artifacts", or "files".
     */
    public String getType() {
        return type;
    }

    @Override
    public List<String> getResolutions() {
        return ImmutableList.<String>builder()
            .addAll(resolutions)
            .addAll(super.getResolutions()) // Calculated from causes
            .build();
    }

    private static String buildMessage(String type, String displayName) {
        return String.format("Could not resolve all %s for %s.", type, displayName);
    }
}
