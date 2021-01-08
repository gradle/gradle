/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.kotlin.dsl

import org.gradle.api.Action
import org.gradle.api.ActionConfiguration
import org.gradle.api.artifacts.ComponentMetadataRule
import org.gradle.api.artifacts.dsl.ComponentMetadataHandler


/**
 * Adds a class based rule that may modify the metadata of any resolved software component.
 *
 * @param T the rule to be added
 * @return this
 *
 * @see [ComponentMetadataHandler.all]
 */
inline fun <reified T : ComponentMetadataRule> ComponentMetadataHandler.all(): ComponentMetadataHandler =
    all(T::class.java)


/**
 * Adds a class based rule that may modify the metadata of any resolved software component.
 * The rule itself is configured by the provided configure action.
 *
 * @param T the rule to be added
 * @param configureAction the rule configuration
 * @return this
 *
 * @see [ComponentMetadataHandler.all]
 */
@Suppress("extension_shadowed_by_member")
inline fun <reified T : ComponentMetadataRule> ComponentMetadataHandler.all(configureAction: Action<in ActionConfiguration>): ComponentMetadataHandler =
    all(T::class.java, configureAction)


/**
 * Adds a class based rule that may modify the metadata of any resolved software component belonging to the specified module.
 *
 * @param T the rule to be added
 * @param id the module to apply this rule to in "group:module" format or as a [org.gradle.api.artifacts.ModuleIdentifier]
 * @return this
 *
 * @see [ComponentMetadataHandler.withModule]
 */
inline fun <reified T : ComponentMetadataRule> ComponentMetadataHandler.withModule(id: Any): ComponentMetadataHandler =
    withModule(id, T::class.java)


/**
 * Adds a class based rule that may modify the metadata of any resolved software component belonging to the specified module.
 *
 * @param T the rule to be added
 * @param id the module to apply this rule to in "group:module" format or as a [org.gradle.api.artifacts.ModuleIdentifier]
 * @return this
 *
 * @see [ComponentMetadataHandler.withModule]
 */
@Suppress("extension_shadowed_by_member")
inline fun <reified T : ComponentMetadataRule> ComponentMetadataHandler.withModule(id: Any, configureAction: Action<in ActionConfiguration>): ComponentMetadataHandler =
    withModule(id, T::class.java, configureAction)
