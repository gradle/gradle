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

package org.gradle.api.internal.properties;

import org.gradle.internal.service.scopes.EventScope;
import org.gradle.internal.service.scopes.Scope;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.util.Map;

/**
 * Allows registering Gradle property access as build configuration inputs.
 *
 * @see org.gradle.api.internal.properties.GradleProperties
 */
@EventScope(Scope.BuildTree.class)
public interface GradlePropertiesListener {

    /**
     * Tracks property loading.
     */
    void onGradlePropertiesLoaded(
        GradlePropertyScope propertyScope,
        File propertiesDir
    );

    /**
     * Tracks property access.
     * <p>
     * Gradle property values cannot be null, so null {@code propertyValue} represents
     * a lookup of missing property, which can happen when checking for property presence.
     */
    void onGradlePropertyAccess(
        GradlePropertyScope propertyScope,
        String propertyName,
        @Nullable Object propertyValue
    );

    /**
     * Tracks prefixed property access.
     */
    void onGradlePropertiesByPrefix(
        GradlePropertyScope propertyScope,
        String prefix,
        Map<String, String> snapshot
    );
}
