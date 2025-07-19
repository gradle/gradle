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

package org.gradle.internal.extensibility;

import com.google.common.collect.ImmutableMap;
import org.jspecify.annotations.Nullable;

import java.util.Map;

public class DefaultExtraPropertiesGradlePropertiesLookup implements ExtraPropertiesGradlePropertiesLookup {

    private final GradlePropertiesAccessListener accessListener;
    private final ImmutableMap<String, Object> gradleProperties;

    public DefaultExtraPropertiesGradlePropertiesLookup(GradlePropertiesAccessListener accessListener, Map<String, Object> gradleProperties) {
        this.accessListener = accessListener;
        this.gradleProperties = ImmutableMap.copyOf(gradleProperties);
    }

    @Override
    @Nullable
    public Object findGradleProperty(String propertyName) {
        Object maybeValue = gradleProperties.get(propertyName);
        // Report access of missing properties as well
        accessListener.onGradlePropertyAccess(propertyName, maybeValue);
        return maybeValue;
    }

    @Override
    public Map<String, Object> asMap() {
        gradleProperties.forEach(accessListener::onGradlePropertyAccess);
        return gradleProperties;
    }
}
