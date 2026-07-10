/*
 * Copyright 2017 the original author or authors.
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
import org.gradle.api.internal.project.ProjectIdentity;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;
import org.gradle.util.Path;
import org.jspecify.annotations.Nullable;

import java.util.Locale;

/**
 * Uniquely identifies the target of some configuration.
 *
 * This is primarily used to support
 * {@code ApplyScriptPluginBuildOperationType.Details} and {@code ApplyPluginBuildOperationType.Details}.
 */
@ServiceScope({Scope.Build.class, Scope.Settings.class, Scope.Project.class})
public abstract class ConfigurationTargetIdentifier {

    private ConfigurationTargetIdentifier() {
    }

    public enum Type {
        GRADLE,
        SETTINGS,
        PROJECT;

        public final String label = name().toLowerCase(Locale.ROOT);
    }

    public abstract Type getTargetType();

    /**
     * If type == project, that project's path (not identity path).
     * Else, null.
     */
    @Nullable
    public abstract String getTargetPath();

    public abstract String getBuildPath();

    /**
     * Returns null if the thing is of an unknown type.
     * This can happen with {@code apply(from: "foo", to: someTask)},
     * where "to" can be absolutely anything.
     */
    @Nullable
    public static ConfigurationTargetIdentifier of(Object any) {
        if (any instanceof PluginAwareInternal) {
            return ((PluginAwareInternal) any).getConfigurationTargetIdentifier();
        } else {
            return null;
        }
    }

    public static ConfigurationTargetIdentifier of(ProjectIdentity projectIdentity) {
        return new ConfigurationTargetIdentifier() {
            @Override
            public Type getTargetType() {
                return Type.PROJECT;
            }

            @Override
            public String getTargetPath() {
                return projectIdentity.getProjectPath().asString();
            }

            @Override
            public String getBuildPath() {
                return projectIdentity.getBuildPath().asString();
            }
        };
    }

    public static ConfigurationTargetIdentifier of(final SettingsInternal settings) {
        Path buildPath = settings.getGradle().getOwner().getIdentityPath();
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
                return buildPath.asString();
            }
        };
    }

    public static ConfigurationTargetIdentifier of(final GradleInternal gradle) {
        Path buildPath = gradle.getOwner().getIdentityPath();
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
                return buildPath.asString();
            }
        };
    }

}
