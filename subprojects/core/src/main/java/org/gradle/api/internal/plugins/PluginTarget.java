/*
 * Copyright 2014 the original author or authors.
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

import org.gradle.api.Plugin;
import org.gradle.configuration.ConfigurationTargetIdentifier;

import javax.annotation.Nullable;

/**
 * Represents the target that a plugin should be applied to, such as a Gradle, Settings or Project object.
 * This is used to apply a plugin to a target in a way that is appropriate for that target, such as adding
 * additional functionality specific to the target or disallowing certain types of plugins to be applied.
 */
public interface PluginTarget {

    // Implementations should not wrap exceptions, this is done in DefaultObjectConfigurationAction

    ConfigurationTargetIdentifier getConfigurationTargetIdentifier();

    void applyImperative(@Nullable String pluginId, Plugin<?> plugin);

    void applyRules(@Nullable String pluginId, Class<?> clazz);

    void applyImperativeRulesHybrid(@Nullable String pluginId, Plugin<?> plugin, Class<?> declaringClass);
}
