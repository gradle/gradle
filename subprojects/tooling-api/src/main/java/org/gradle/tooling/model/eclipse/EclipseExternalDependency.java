/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.tooling.model.eclipse;

import org.gradle.api.Incubating;
import org.gradle.tooling.model.ComponentSelector;
import org.gradle.tooling.model.ExternalDependency;

import javax.annotation.Nullable;

/**
 * Represents an Eclipse-specific external artifact dependency.
 *
 * @since 2.14
 */
public interface EclipseExternalDependency extends ExternalDependency, EclipseClasspathEntry {

    /**
     * Returns {@code true} if the current instance represents a resolved dependency.
     * <p>
     * If the target Gradle version is older than 6.7 then this method will always return {@code true}.
     *
     * @since 6.7
     */
    @Incubating
    boolean isResolved();

    /**
     * Returns the coordinates of the artifact that Gradle was not able to resolve.
     * <p>
     * Returns {@code null} for resolved dependencies (i.e. when {@link #isResolved()} returns true).
     * <p>
     * If the target Gradle version is older than 6.7 then this method will always return {@code null}.
     *
     * @since 6.7
     */
    @Nullable
    @Incubating
    ComponentSelector getAttemptedSelector();
}
