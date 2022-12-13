/*
 * Copyright 2007-2008 the original author or authors.
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

import org.gradle.StartParameter;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.SettingsInternal;
import org.gradle.api.internal.initialization.ClassLoaderScope;

/**
 * Responsible for locating, constructing, and configuring the {@link SettingsInternal} for a build.
 */
public interface SettingsProcessor {
    /**
     * Load the settings for the given build. The caller is responsible for closing the return value.
     */
    SettingsState process(
        GradleInternal gradle,
        SettingsLocation settingsLocation,
        ClassLoaderScope buildRootClassLoaderScope,
        StartParameter startParameter
    );
}
