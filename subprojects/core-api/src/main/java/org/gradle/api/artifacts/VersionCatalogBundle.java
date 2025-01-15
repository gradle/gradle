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

package org.gradle.api.artifacts;

import org.gradle.internal.HasInternalProtocol;

/**
 * Represents a bundle from a {@link VersionCatalog}. It is immutable. An instance can be used to declare a dependency for each library inside it, which will each make an
 * {@link ExternalModuleDependency} that may be further configured.
 *
 * @since 8.13
 */
@HasInternalProtocol
public interface VersionCatalogBundle {
    /**
     * The libraries in the bundle.
     *
     * @return the libraries
     * @since 8.13
     */
    Iterable<? extends VersionCatalogLibrary> getLibraries();
}
