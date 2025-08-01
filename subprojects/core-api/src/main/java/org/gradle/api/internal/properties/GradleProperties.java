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

import org.gradle.api.provider.ProviderFactory;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;
import org.jspecify.annotations.Nullable;

import java.util.Map;

/**
 * Gradle properties of a build or a project. Only build-scoped properties when injected as a service.
 * <p>
 * See {@link org.gradle.initialization.GradlePropertiesController GradlePropertiesController} on how these properties
 * are loaded and what contributes to their values at each scope.
 * <p>
 * Build-scoped properties are accessible by users via {@link ProviderFactory#gradleProperty(String)}.
 * Currently, properties accessed this way do not take project-scoped Gradle properties into account,
 * even if the provider factory was injected in the project.
 * <p>
 * Build-scoped properties are also accessible via extra-properties of the Settings instance.
 * This includes any access to these extra-properties, including the dynamic lookup in the settings script.
 * <p>
 * Project-scoped properties are only accessible via extra-properties of the Project instance.
 * This includes any access to these extra-properties, including the dynamic lookup in the build script.
 */
@ServiceScope(Scope.Build.class)
public interface GradleProperties {

    /**
     * Returns the value of the property or null if not found.
     * <p>
     * The actual value of a property can never be null. Therefore, if null is returned, then the property was not specified.
     */
    @Nullable
    String find(String propertyName);

    /**
     * Returns all properties as a map.
     */
    Map<String, String> getProperties();

    /**
     * Returns all properties, whose name starts with a given prefix, as a map.
     * <p>
     * The returned map is never null. An empty map is returned if nothing is found.
     */
    Map<String, String> getPropertiesWithPrefix(String prefix);
}
