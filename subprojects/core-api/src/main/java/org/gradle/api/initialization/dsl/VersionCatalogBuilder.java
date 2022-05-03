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
package org.gradle.api.initialization.dsl;

import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.api.Named;
import org.gradle.api.artifacts.MutableVersionConstraint;
import org.gradle.api.provider.Property;
import org.gradle.internal.HasInternalProtocol;

import java.util.List;

/**
 * A version catalog builder. Dependencies defined via this model
 * will trigger the generation of accessors available in build scripts.
 *
 * @since 7.0
 */
@HasInternalProtocol
public interface VersionCatalogBuilder extends Named {

    /**
     * A description for the dependencies model, which will be used in
     * the generated sources as documentation.
     * @return the description for this model
     */
    Property<String> getDescription();

    /**
     * Configures the model by reading it from a version catalog.
     * A version catalog is a component published using the `version-catalog` plugin or
     * a local TOML file.
     *
     * <p>
     * This function can be called only once, further calls will result in an error.
     * The passed notation should conform these constraints:
     * <ul>
     *     <li>If a file notation is passed, it should be a single file.</li>
     *     <li>If it's a resolvable dependency, it should resolve to a single file.</li>
     * </ul>
     *
     * <p>
     * If the notation doesn't conform these constraints, an exception will be thrown at configuration time.
     *
     * @param dependencyNotation any notation supported by {@link org.gradle.api.artifacts.dsl.DependencyHandler}
     */
    void from(Object dependencyNotation);

    /**
     * Configures a dependency version which can then be referenced using
     * the {@link VersionCatalogBuilder.LibraryAliasBuilder#versionRef(String)} )} method.
     *
     * @param alias an identifier for the version
     * @param versionSpec the dependency version spec
     * @return the version alias name
     */
    String version(String alias, Action<? super MutableVersionConstraint> versionSpec);

    /**
     * Configures a dependency version which can then be referenced using
     * the {@link VersionCatalogBuilder.LibraryAliasBuilder#versionRef(String)} method.
     *
     * @param alias an identifier for the version
     * @param version the version alias name
     */
    String version(String alias, String version);

    /**
     * Entry point for registering an alias for a library.
     *
     * @param alias the alias identifier
     * @return a builder for this alias
     * @deprecated Use {@link #library(String, String, String)}, {@link #library(String, String)}
     * and {@link #plugin(String, String)} instead. Will be removed in Gradle 8.
     */
    @Deprecated
    AliasBuilder alias(String alias);

    /**
     * Entry point for registering a library alias.
     *
     * @param alias the alias of the library
     * @param group the group of the library
     * @param artifact the artifact ID of the library
     * @return a builder for this alias, to finish the version configuration
     * @since 7.4
     */
    LibraryAliasBuilder library(String alias, String group, String artifact);

    /**
     * Declare a library alias in full. This does not return a builder, as the declaration is fully complete.
     * Use {@link #library(String, String, String)} if you need a more complex version declaration.
     *
     * <p>
     * Note that declaring a classifier or extension using this method is not possible.
     * </p>
     *
     * @param alias the alias of the library
     * @param groupArtifactVersion the {@code group:artifact:version} string, all components are required
     * @since 7.4
     */
    void library(String alias, String groupArtifactVersion);

    /**
     * Entry point for registering a plugin alias.
     *
     * @param alias the alias of the plugin
     * @param id the ID of the plugin
     * @return a builder for this alias, to finish the version configuration
     * @since 7.4
     */
    PluginAliasBuilder plugin(String alias, String id);

    /**
     * Declares a bundle of dependencies. A bundle consists of a name for the bundle,
     * and a list of aliases. The aliases must correspond to aliases defined via
     * the {@code library()} methods.
     *
     * @param alias the alias of the bundle
     * @param aliases the aliases of the dependencies included in the bundle
     * @see #library(String, String, String)
     * @see #library(String, String)
     */
    void bundle(String alias, List<String> aliases);

    /**
     * Returns the name of the extension configured by this builder
     */
    String getLibrariesExtensionName();

    /**
     * Allows configuring an alias
     *
     * @since 7.0
     * @deprecated With the removal of {@link #alias(String)}, this class will no longer needed.
     */
    @Incubating
    @Deprecated
    interface AliasBuilder {
        /**
         * Sets GAV coordinates for this alias
         * @param groupArtifactVersion the GAV coordinates, in the group:artifact:version form
         */
        void to(String groupArtifactVersion);

        /**
         * Sets the group and name of this alias
         * @param group the group
         * @param name the name (or artifact id)
         * @return a builder to configure the version
         */
        LibraryAliasBuilder to(String group, String name);

        /**
         * Sets the plugin id this alias will reference
         * @param id the plugin id
         * @return a builder to configure the plugin
         *
         * @since 7.2
         */
        PluginAliasBuilder toPluginId(String id);
    }

    /**
     * Allows configuring the version of a library
     *
     * @since 7.0
     */
    interface LibraryAliasBuilder {
        /**
         * Configures the version for this alias
         */
        void version(Action<? super MutableVersionConstraint> versionSpec);

        /**
         * Configures the required version for this alias
         */
        void version(String version);

        /**
         * Configures this alias to use a version reference, created
         * via the {@link #version(String, Action)} method.
         *
         * @param versionRef the version reference
         */
        void versionRef(String versionRef);

        /**
         * Do not associate this alias to a particular version, in which
         * case the dependency notation will just have group and artifact.
         *
         */
        void withoutVersion();
    }

    /**
     * Allows configuring the version of a plugin
     *
     * @since 7.2
     */
    interface PluginAliasBuilder {
        /**
         * Configures the version for this alias
         */
        void version(Action<? super MutableVersionConstraint> versionSpec);

        /**
         * Configures the required version for this alias
         */
        void version(String version);

        /**
         * Configures this alias to use a version reference, created
         * via the {@link #version(String, Action)} method.
         *
         * @param versionRef the version reference
         */
        void versionRef(String versionRef);
    }

}
