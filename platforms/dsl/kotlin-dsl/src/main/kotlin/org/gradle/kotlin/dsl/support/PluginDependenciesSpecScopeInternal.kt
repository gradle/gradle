/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.kotlin.dsl.support

import org.gradle.api.internal.catalog.ExternalModuleDependencyFactory
import org.gradle.api.model.ObjectFactory
import org.gradle.initialization.DependenciesAccessors
import org.gradle.kotlin.dsl.*
import org.gradle.plugin.use.PluginDependenciesSpec
import javax.inject.Inject


internal
class PluginDependenciesSpecScopeInternal(
    private val objects: ObjectFactory,
    plugins: PluginDependenciesSpec
) : PluginDependenciesSpecScope(plugins) {

    private
    val versionCatalogForPluginBlockFactories: Map<String, ExternalModuleDependencyFactory> by unsafeLazy {
        objects
            .newInstance<PluginDependenciesSpecScopeInternalServices>()
            .dependenciesAccessors.createPluginsBlockFactories(objects)
    }

    /**
     * Used by version catalog accessors in project scripts plugins {} block.
     */
    @Suppress("UNUSED")
    fun versionCatalogForPluginsBlock(name: String): ExternalModuleDependencyFactory =
        versionCatalogForPluginBlockFactories.getValue(name)
}


internal
interface PluginDependenciesSpecScopeInternalServices {

    @get:Inject
    val dependenciesAccessors: DependenciesAccessors
}
