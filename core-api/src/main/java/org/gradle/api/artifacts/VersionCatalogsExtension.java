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

import org.gradle.api.InvalidUserDataException;

import java.util.Optional;
import java.util.Set;

/**
 * Gives access to all version catalogs available.
 *
 * @since 7.0
 */
public interface VersionCatalogsExtension extends Iterable<VersionCatalog> {

    /**
     * Tries to find a catalog with the corresponding name
     * @param name the name of the catalog
     */
    Optional<VersionCatalog> find(String name);

    /**
     * Returns the catalog with the supplied name or throws an exception
     * if it doesn't exist.
     */
    default VersionCatalog named(String name) {
        return find(name).orElseThrow(() -> new InvalidUserDataException("Catalog named " + name + " doesn't exist"));
    }

    /**
     * Returns the list of catalog names
     *
     * @since 7.2
     */
    Set<String> getCatalogNames();
}
