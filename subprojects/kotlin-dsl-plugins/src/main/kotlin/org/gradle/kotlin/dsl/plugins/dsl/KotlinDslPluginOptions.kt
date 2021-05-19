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
import org.gradle.api.provider.Property
import org.gradle.internal.deprecation.DeprecationLogger.deprecateProperty

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
     * Flag has no effect since `kotlin-dsl` no longer relies on experimental features.
     */
    @Deprecated(deprecationMessage)
    val experimentalWarning: Property<Boolean>
        get() {
            nagUserAboutExperimentalWarning()
            return experimentalWarningProperty
        }

    private
    val experimentalWarningProperty = objects.property<Boolean>()

    private
    fun nagUserAboutExperimentalWarning() {
        deprecateProperty(KotlinDslPluginOptions::class.java, "experimentalWarning")
            .withContext(deprecationMessage)
            .willBeRemovedInGradle8()
            .undocumented()
            .nagUser()
    }
}


private
const val deprecationMessage = "Flag has no effect since `kotlin-dsl` no longer relies on experimental features."


internal
fun Project.kotlinDslPluginOptions(action: KotlinDslPluginOptions.() -> Unit) =
    configure(action)
