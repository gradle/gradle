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

import com.google.common.collect.ImmutableMap;
import org.gradle.initialization.properties.MutableGradleProperties;
import org.jspecify.annotations.Nullable;

import java.util.Map;

public class DefaultGradleProperties implements MutableGradleProperties {
    private final Map<String, Object> defaultProperties;
    private final Map<String, Object> overrideProperties;
    private ImmutableMap<String, Object> gradleProperties;

    public DefaultGradleProperties(
        Map<String, Object> defaultProperties,
        Map<String, Object> overrideProperties
    ) {
        this.defaultProperties = defaultProperties;
        this.overrideProperties = overrideProperties;
        this.gradleProperties = mergePropertiesWith(ImmutableMap.of());
    }

    @Nullable
    @Override
    public Object find(String propertyName) {
        return gradleProperties.get(propertyName);
    }

    @Override
    public Map<String, Object> mergeProperties(Map<String, Object> properties) {
        return properties.isEmpty()
            ? gradleProperties
            : mergePropertiesWith(properties);
    }

    @Override
    public void updateOverrideProperties(Map<String, Object> properties) {
        overrideProperties.putAll(properties);
        gradleProperties = mergePropertiesWith(ImmutableMap.of());
    }

    @Override
    public Map<String, Object> getProperties() {
        return gradleProperties;
    }

    private ImmutableMap<String, Object> mergePropertiesWith(Map<String, Object> properties) {
        return ImmutableMap.<String, Object>builder()
            .putAll(defaultProperties)
            .putAll(properties)
            .putAll(overrideProperties)
            .buildKeepingLast();
    }
}
