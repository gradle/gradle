/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.kotlin.dsl.plugins.dsl

import org.gradle.api.Project
import org.gradle.api.model.ObjectFactory

import org.gradle.kotlin.dsl.*


/**
 * Options for the `kotlin-dsl` plugin.
 *
 * @see KotlinDslPlugin
 */
class KotlinDslPluginOptions internal constructor(objects: ObjectFactory) {

    /**
     * Kotlin compilation JVM target.
     *
     * Defaults to `1.8`.
     *
     * @see [org.jetbrains.kotlin.gradle.dsl.KotlinJvmOptions.jvmTarget]
     */
    val jvmTarget = objects.property<String>().apply {
        set("1.8")
    }

    /**
     * Set to `false` to silence the warning about the `kotlin-dsl` plugin enabling Kotlin compiler experimental features.
     */
    val experimentalWarning = objects.property<Boolean>().apply {
        set(true)
    }
}


internal
fun Project.kotlinDslPluginOptions(action: KotlinDslPluginOptions.() -> Unit) =
    configure(action)
