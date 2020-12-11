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
package org.gradle.api.artifacts;

import org.gradle.api.Incubating;
import org.gradle.api.Named;
import org.gradle.api.NonNullApi;
import org.gradle.api.provider.Provider;

import java.util.Optional;

/**
 * Provides access to a version catalog. Unlike generated extension
 * classes for catalogs, there is no guarantee that the requested
 * aliases exist so you must check existence on the returned optional
 * values.
 *
 * @since 7.0
 */
@Incubating
@NonNullApi
public interface VersionCatalog extends Named {
    /**
     * Returns the dependency provider for the corresponding alias.
     * @param alias the alias of the dependency
     */
    Optional<Provider<MinimalExternalModuleDependency>> findDependency(String alias);

    /**
     * Returns the dependency provider for the corresponding bundle.
     * @param bundle the alias of the bundle
     */
    Optional<Provider<ExternalModuleDependencyBundle>> findBundle(String bundle);

    /**
     * Returns the version constraint with the corresponding name in the catalog.
     * @param name the name of the version
     */
    Optional<VersionConstraint> findVersion(String name);
}
