/*
 * Copyright 2024 the original author or authors.
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

import org.gradle.api.provider.Provider;
import org.gradle.plugin.use.PluginDependency;

/**
 * Marker interface for a {@link Provider} that provides a {@link org.gradle.plugin.use.PluginDependency}.
 *
 * @since 8.13
 * @deprecated This interface is immediately deprecated, and should not be referred to. It exists solely to provide a migration path from the old {@link Provider} API to the new
 * {@link VersionCatalogPlugin} API. Reference {@link VersionCatalogPlugin} directly instead. This interface will be removed in Gradle 9.0.
 */
@Deprecated
public interface PluginDependencyProvider extends Provider<PluginDependency>, VersionCatalogPlugin {
    @Override
    default String getPluginId() {
        return get().getPluginId();
    }

    @Override
    default VersionConstraint getVersion() {
        return get().getVersion();
    }
}
