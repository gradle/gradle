/*
 * Copyright 2016 the original author or authors.
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

import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ModuleDependency

import org.gradle.kotlin.dsl.support.excludeMapFor


/**
 * Adds an exclude rule to exclude transitive dependencies for all dependencies of this configuration.
 * You can also add exclude rules per-dependency. See [ModuleDependency.exclude].
 *
 * @param group the optional group identifying the dependencies to be excluded.
 * @param module the optional module name identifying the dependencies to be excluded.
 * @return this
 */
fun Configuration.exclude(group: String? = null, module: String? = null): Configuration =
    exclude(excludeMapFor(group, module))
