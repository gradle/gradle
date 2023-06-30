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

package org.gradle.kotlin.dsl

import org.gradle.api.NamedDomainObjectProvider
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer

/**
 * Configures an object by name, without triggering its creation or configuration, failing if there is no such object.
 */
context(ConfigurationContainer)
operator fun String.invoke(configuration: Configuration.() -> Unit): NamedDomainObjectProvider<Configuration> =
    named(this).apply { configure(configuration) }

/**
 * Locates an object by name, without triggering its creation or configuration, failing if there is no such object.
 *
 */
context(ConfigurationContainer)
operator fun String.invoke(): NamedDomainObjectProvider<Configuration> =
    named(this)
