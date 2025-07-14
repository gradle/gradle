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

import org.jspecify.annotations.Nullable;

import java.util.Map;

/**
 * For Project and Settings the extra properties lookup has Gradle properties as a fallback.
 *
 * @see org.gradle.api.internal.plugins.ExtraPropertiesExtensionInternal
 */
public interface ExtraPropertiesGradlePropertiesLookup {

    /**
     * Returns the value of a given Gradle property or null if the property is not present.
     * <p>
     * If a property is present, its value is never null.
     */
    @Nullable
    Object findGradleProperty(String propertyName);

    /**
     * Returns all available Gradle properties as a map.
     * <p>
     * For configuration input tracking, all Gradle properties are then considered accessed.
     */
    Map<String, Object> asMap();

}
