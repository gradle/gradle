/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.initialization;

import org.gradle.api.internal.properties.GradleProperties;

import java.io.File;

/**
 * Controls the state (not loaded / loaded) of the attached {@link GradleProperties} instance
 * so that the set of Gradle properties is deterministically loaded only once per build.
 */
public interface GradlePropertiesController {

    /**
     * The {@link GradleProperties} instance attached to this service.
     */
    GradleProperties getGradleProperties();

    /**
     * Loads the immutable set of {@link GradleProperties} from the given directory and
     * makes it available to the build.
     *
     * This method should be called only once per build but multiple calls with the
     * same argument are allowed.
     *
     * @param settingsDir directory where to look for the {@code gradle.properties} file
     * @throws IllegalStateException if called with a different argument in the same build
     */
    void loadGradlePropertiesFrom(File settingsDir);
}
