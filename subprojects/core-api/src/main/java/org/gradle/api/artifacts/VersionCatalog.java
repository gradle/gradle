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

import org.gradle.api.Named;
import org.gradle.api.NonNullApi;
import org.gradle.api.provider.Provider;
import org.gradle.plugin.use.PluginDependency;

import java.util.List;
import java.util.Optional;

/**
 * Provides access to a version catalog. Unlike generated extension
 * classes for catalogs, there is no guarantee that the requested
 * aliases exist, so you must check existence on the returned optional
 * values.
 *
 * @since 7.0
 */
@NonNullApi
public interface VersionCatalog extends Named {
    /**
     * Returns the dependency provider for the corresponding library alias.
     * <p>
     * Note: Alias will be automatically normalized: '-', '_' and '.' will be replaced with '.'
     * </p>
     * @param alias the alias of the dependency
     * @deprecated For consistency, this has been renamed to {@link #findLibrary(String)}.
     */
    @Deprecated
    Optional<Provider<MinimalExternalModuleDependency>> findDependency(String alias);

    /**
     * Returns the dependency provider for the corresponding library alias.
     * <p>
     * Note: Alias will be automatically normalized: '-', '_' and '.' will be replaced with '.'
     * </p>
     * @param alias the alias of the library
     * @since 7.4
     */
    Optional<Provider<MinimalExternalModuleDependency>> findLibrary(String alias);

    /**
     * Returns the provider for the corresponding bundle alias.
     * <p>
     * Note: Bundle will be automatically normalized: '-', '_' and '.' will be replaced with '.'
     * </p>
     * @param alias the alias of the bundle
     */
    Optional<Provider<ExternalModuleDependencyBundle>> findBundle(String alias);

    /**
     * Returns the version constraint with the corresponding alias in the catalog.
     * <p>
     * Note: Alias will be automatically normalized: '-', '_' and '.' will be replaced with '.'
     * </p>
     * @param alias the alias of the version
     */
    Optional<VersionConstraint> findVersion(String alias);

    /**
     * Returns the plugin dependency provider for the requested alias.
     * <p>
     * Note: Alias will be automatically normalized: '-', '_' and '.' will be replaced with '.'
     * </p>
     * @param alias the alias of the plugin
     *
     * @since 7.2
     */
    Optional<Provider<PluginDependency>> findPlugin(String alias);

    /**
     * Returns the list of aliases defined in this version catalog.
     * <p>
     * Note: Returned aliases are normalized: '-', '_' and '.' have been replaced with '.'
     * </p>
     * @return the list of dependency aliases
     *
     * @since 7.1
     * @deprecated For consistency, this has been renamed to {@link #getLibraryAliases()}.
     */
    @Deprecated
    List<String> getDependencyAliases();

    /**
     * Returns the list of aliases defined in this version catalog.
     * <p>
     * Note: Returned aliases are normalized: '-', '_' and '.' have been replaced with '.'
     * </p>
     * @return the list of library aliases
     *
     * @since 7.4
     */
    List<String> getLibraryAliases();

    /**
     * Returns the list of bundles defined in this version catalog.
     * <p>
     * Note: Returned aliases are normalized: '-', '_' and '.' have been replaced with '.'
     * </p>
     * @return the list of bundle aliases
     *
     * @since 7.1
     */
    List<String> getBundleAliases();

    /**
     * Returns the list of version aliases defined in this version catalog.
     * <p>
     * Note: Returned aliases are normalized: '-', '_' and '.' have been replaced with '.'
     * </p>
     * @return the list of version aliases
     *
     * @since 7.1
     */
    List<String> getVersionAliases();

    /**
     * Returns the list of plugin aliases defined in this version catalog.
     * <p>
     * Note: Returned aliases are normalized: '-', '_' and '.' have been replaced with '.'
     * </p>
     * @return the list of plugin aliases
     *
     * @since 7.2
     */
    List<String> getPluginAliases();
}
