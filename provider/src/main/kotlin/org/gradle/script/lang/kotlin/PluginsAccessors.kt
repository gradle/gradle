/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.script.lang.kotlin

import org.gradle.plugin.use.PluginDependenciesSpec
import org.gradle.plugin.use.PluginDependencySpec


/**
 * The `embedded-kotlin` plugin.
 *
 * Fetched from plugin repositories using the following plugin id:
 * `org.gradle.script.lang.kotlin.plugins.embedded-kotlin`.
 *
 * @see org.gradle.script.lang.kotlin.plugins.embedded.EmbeddedKotlinPlugin
 */
inline val PluginDependenciesSpec.`embedded-kotlin`: PluginDependencySpec
    get() = id("org.gradle.script.lang.kotlin.plugins.embedded-kotlin") version gradleScriptKotlinVersion
