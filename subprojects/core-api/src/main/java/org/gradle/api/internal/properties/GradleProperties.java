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

package org.gradle.api.internal.properties;

import javax.annotation.Nullable;
import java.util.Map;

/**
 * Immutable set of Gradle properties loaded at the start of the build.
 */
public interface GradleProperties {

    @Nullable
    String find(String propertyName);

    /**
     * Merges the loaded properties with the given properties and returns an immutable
     * map with the result.
     *
     * @param properties read-only properties to be merged with the set of loaded properties.
     */
    Map<String, String> mergeProperties(Map<String, String> properties);
}
