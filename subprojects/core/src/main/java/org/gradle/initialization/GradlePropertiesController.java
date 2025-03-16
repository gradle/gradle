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
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

import java.io.File;

/**
 * Controls the state (not loaded / loaded) of the attached {@link GradleProperties} instance
 * so that the set of Gradle properties is deterministically loaded only once per build.
 */
@ServiceScope(Scope.Build.class)
public interface GradlePropertiesController {

    /**
     * The {@link GradleProperties} instance attached to this service.
     */
    GradleProperties getGradleProperties();

    /**
     * Loads the set of {@link GradleProperties} from the given directory and
     * makes it available to the build.
     *
     * Optionally sets a system properties after load.
     *
     * This method should be called only once per build but multiple calls with the
     * same argument are allowed.
     *
     * @param settingsDir directory where to look for the {@code gradle.properties} file
     * @param setSystemProperties should system properties be set or not
     * @throws IllegalStateException if called with a different argument in the same build
     */
    void loadGradlePropertiesFrom(File settingsDir, boolean setSystemProperties);

    /**
     * Unloads the properties so the next call to {@link #loadGradlePropertiesFrom(File, boolean)} would reload them and
     * re-evaluate any property defining system properties and environment variables.
     */
    void unloadGradleProperties();
}
