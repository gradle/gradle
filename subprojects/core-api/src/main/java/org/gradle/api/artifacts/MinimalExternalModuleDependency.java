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

import org.gradle.internal.HasInternalProtocol;

/**
 * The minimal information Gradle needs to address an external module.
 *
 * @since 6.8
 * @deprecated This interface was made to extend {@link ExternalModuleDependency} in order to work around an issue with the {@link org.gradle.api.artifacts.dsl.DependencyCollector} API.
 * This issue has been resolved by re-working version catalogs to no longer use a {@link org.gradle.api.provider.Provider}. In order to standardize and make the
 * version catalog API clearly immutable, this interface will be replaced with {@link VersionCatalogLibrary}, which will not have the troublesome supertype of {@link ExternalModuleDependency}.
 * This interface will be removed in Gradle 9.0.
 */
@Deprecated
@HasInternalProtocol
public interface MinimalExternalModuleDependency extends ExternalModuleDependency, VersionCatalogLibrary {
    @Override
    ModuleIdentifier getModule();
    @Override
    VersionConstraint getVersionConstraint();

    /**
     * {@inheritDoc}
     */
    @Override
    MinimalExternalModuleDependency copy();
}
