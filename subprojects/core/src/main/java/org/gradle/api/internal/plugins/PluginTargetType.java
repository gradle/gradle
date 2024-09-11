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

package org.gradle.api.internal.plugins;

import org.gradle.api.NonNullApi;
import org.gradle.api.Project;
import org.gradle.api.initialization.Settings;
import org.gradle.api.invocation.Gradle;

import javax.annotation.Nullable;

@NonNullApi
public enum PluginTargetType {

    PROJECT("in a build script (or to the Project object)"),
    SETTINGS("in a settings script (or to the Settings object)"),
    GRADLE("in an init script (or to the Gradle object)");

    private final String applyTargetDescription;

    PluginTargetType(String applyTargetDescription) {
        this.applyTargetDescription = applyTargetDescription;
    }

    public String getApplyTargetDescription() {
        return applyTargetDescription;
    }

    /**
     * Returns a recognized target type or null
     */
    @Nullable
    public static PluginTargetType from(Class<?> type) {
        if (Project.class.isAssignableFrom(type)) {
            return PROJECT;
        } else if (Settings.class.isAssignableFrom(type)) {
            return SETTINGS;
        } else if (Gradle.class.isAssignableFrom(type)) {
            return GRADLE;
        } else {
            return null;
        }
    }
}
