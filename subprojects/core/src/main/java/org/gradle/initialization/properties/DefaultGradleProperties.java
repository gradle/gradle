/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.initialization.properties;

import com.google.common.collect.ImmutableMap;
import org.gradle.api.internal.properties.GradleProperties;
import org.jspecify.annotations.Nullable;

import java.util.Map;

public class DefaultGradleProperties implements GradleProperties {

    private final ImmutableMap<String, String> properties;

    public DefaultGradleProperties(Map<String, String> properties) {
        this.properties = ImmutableMap.copyOf(properties);
    }

    @Override
    public @Nullable String find(String propertyName) {
        return properties.get(propertyName);
    }

    @Override
    public Map<String, String> getProperties() {
        return properties;
    }

    @Override
    public Map<String, String> getPropertiesWithPrefix(String prefix) {
        return properties.entrySet().stream()
            .filter(entry -> entry.getKey().startsWith(prefix))
            .collect(ImmutableMap.toImmutableMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}
