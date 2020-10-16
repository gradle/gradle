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
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.artifacts.MutableVersionConstraint;
import org.gradle.api.artifacts.VersionConstraint;
import org.gradle.internal.HasInternalProtocol;

import java.util.List;

/**
 * A dependencies model builder. Dependencies defined via this model
 * will trigger the generation of accessors available in build scripts.
 *
 * @since 6.8
 */
@Incubating
@HasInternalProtocol
public interface DependenciesModelBuilder {
    /**
     * Declares an alias for a dependency
     * @param alias the alias for the dependency
     * @param group the group of the dependency
     * @param name the module name of the dependency
     * @param versionSpec the version specification
     */
    void alias(String alias, String group, String name, Action<? super MutableVersionConstraint> versionSpec);

    /**
     * A shorthand notation to declare a simple dependency with group,
     * artifact, version coordinates. The notation must consist of 3 colon
     * separated parts: group:artifact:version.
     *
     * The version is implicity {@link VersionConstraint#getRequiredVersion() required}.
     *
     * @param alias the alias for the dependency
     * @param gavCoordinates the gav coordinates notation
     */
    default void alias(String alias, String gavCoordinates) {
        String[] coordinates = gavCoordinates.split(":");
        if (coordinates.length == 3) {
            alias(alias, coordinates[0], coordinates[1], vc -> vc.require(coordinates[2]));
        }
        throw new InvalidUserDataException("Invalid dependency notation: it must consist of 3 colon separated parts, eg: my.group:artifact:1.2");
    }

    /**
     * Declares a bundle of dependencies. A bundle consists of a name for the bundle,
     * and a list of aliases. The aliases must correspond to aliases defined via
     * the {@link #alias(String, String, String, Action)} method.
     *
     * @param name the name of the bundle
     * @param aliases the aliases of the dependencies included in the bundle
     */
    void bundle(String name, List<String> aliases);

}
