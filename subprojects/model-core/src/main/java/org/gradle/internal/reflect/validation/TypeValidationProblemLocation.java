/*
 * Copyright 2021 the original author or authors.
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
package org.gradle.internal.reflect.validation;

import org.gradle.plugin.use.PluginId;

import javax.annotation.Nullable;
import java.util.Optional;

public class TypeValidationProblemLocation implements Location {
    private final Class<?> type;
    private final PluginId pluginId;
    private final String parentPropertyName;
    private final String propertyName;

    private TypeValidationProblemLocation(@Nullable Class<?> type, @Nullable PluginId pluginId, @Nullable String parentProperty, @Nullable String property) {
        this.type = type;
        this.pluginId = pluginId;
        this.parentPropertyName = parentProperty;
        this.propertyName = property;
    }

    public static TypeValidationProblemLocation irrelevant() {
        return new TypeValidationProblemLocation(null, null, null, null);
    }

    public static TypeValidationProblemLocation inType(Class<?> type, @Nullable PluginId pluginId) {
        return new TypeValidationProblemLocation(type, pluginId, null, null);
    }

    public static TypeValidationProblemLocation forProperty(@Nullable Class<?> rootType, @Nullable PluginId pluginId, @Nullable String parentProperty, @Nullable String property) {
        return new TypeValidationProblemLocation(rootType, pluginId, parentProperty, property);
    }

    public Optional<Class<?>> getType() {
        return Optional.ofNullable(type);
    }

    public Optional<String> getParentPropertyName() {
        return Optional.ofNullable(parentPropertyName);
    }

    public Optional<String> getPropertyName() {
        return Optional.ofNullable(propertyName);
    }

    public Optional<PluginId> getPlugin() {
        return Optional.ofNullable(pluginId);
    }
}
