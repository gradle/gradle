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
package org.gradle.kotlin.dsl.support

import org.gradle.api.artifacts.dsl.DependencyHandler

import org.gradle.kotlin.dsl.embeddedKotlinVersion

import java.util.Properties


class EmbeddedKotlinProvider {

    fun addDependenciesTo(
        dependencies: DependencyHandler,
        configuration: String,
        vararg kotlinModules: String
    ) {
        kotlinModules.forEach { kotlinModule ->
            dependencies.add(configuration, kotlinModuleVersionNotationFor(kotlinModule))
        }
    }

    internal
    fun pinEmbeddedKotlinDependenciesOn(
        dependencies: DependencyHandler,
        configuration: String
    ) {
        embeddedKotlinVersions.forEach { (module, version) ->
            dependencies.constraints.add(configuration, module).apply {
                version { it.strictly(version) }
                because("Pinned to the embedded Kotlin")
            }
        }
    }

    private
    fun kotlinModuleVersionNotationFor(kotlinModule: String) =
        "${kotlinModuleNotationFor(kotlinModule)}:$embeddedKotlinVersion"

    private
    fun kotlinModuleNotationFor(kotlinModule: String) =
        "org.jetbrains.kotlin:kotlin-$kotlinModule"

    private
    val embeddedKotlinVersions by lazy {
        uncheckedCast<Map<String, String>>(
            Properties().apply {
                EmbeddedKotlinProvider::class.java
                    .classLoader
                    .getResourceAsStream("gradle-kotlin-dsl-embedded-kotlin.properties")
                    .use { input ->
                        load(input)
                    }
            }
        )
    }
}
