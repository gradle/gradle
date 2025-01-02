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

package org.gradle.configuration;

import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.SettingsInternal;
import org.gradle.api.internal.plugins.PluginAwareInternal;
import org.gradle.api.internal.project.ProjectInternal;

import javax.annotation.Nullable;

/**
 * @see ConfigurationTargetIdentifier
 */
public class ConfigurationTargetIdentifiers {

    /**
     * Returns null if the thing is of an unknown type.
     * This can happen with {@code apply(from: "foo", to: someTask)},
     * where “to” can be absolutely anything.
     */
    @Nullable
    public static ConfigurationTargetIdentifier of(Object any) {
        if (any instanceof PluginAwareInternal) {
            return ((PluginAwareInternal) any).getConfigurationTargetIdentifier();
        } else {
            return null;
        }
    }

    public static ConfigurationTargetIdentifier of(final ProjectInternal project) {
        return new ConfigurationTargetIdentifier() {
            @Override
            public Type getTargetType() {
                return Type.PROJECT;
            }

            @Nullable
            @Override
            public String getTargetPath() {
                return project.getProjectPath().getPath();
            }

            @Override
            public String getBuildPath() {
                return project.getGradle().getIdentityPath().getPath();
            }
        };
    }

    public static ConfigurationTargetIdentifier of(final SettingsInternal settings) {
        return new ConfigurationTargetIdentifier() {
            @Override
            public Type getTargetType() {
                return Type.SETTINGS;
            }

            @Nullable
            @Override
            public String getTargetPath() {
                return null;
            }

            @Override
            public String getBuildPath() {
                return settings.getGradle().getIdentityPath().getPath();
            }
        };
    }

    public static ConfigurationTargetIdentifier of(final GradleInternal gradle) {
        return new ConfigurationTargetIdentifier() {
            @Override
            public Type getTargetType() {
                return Type.GRADLE;
            }

            @Nullable
            @Override
            public String getTargetPath() {
                return null;
            }

            @Override
            public String getBuildPath() {
                return gradle.getIdentityPath().getPath();
            }
        };
    }
}
