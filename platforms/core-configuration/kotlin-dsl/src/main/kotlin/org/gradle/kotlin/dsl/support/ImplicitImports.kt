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

package org.gradle.kotlin.dsl.support

import org.gradle.configuration.ImportsReader


/**
 * Holds the list of imports implicitly added to every Kotlin build script.
 */
class ImplicitImports internal constructor(
    @Transient
    private val importsReader: ImportsReader
) {

    val list by lazy {
        gradleImports() + gradleKotlinDslImports()
    }

    private
    fun gradleImports() =
        importsReader.simpleNameToFullClassNamesMapping.values.map { it.first() }

    private
    fun gradleKotlinDslImports() =
        listOf(
            "org.gradle.kotlin.dsl.*",
            // TODO: let this be contributed by :plugins
            "org.gradle.kotlin.dsl.plugins.dsl.*",
            // TODO: infer list of types below at build time by inspecting the Gradle API
            "java.util.concurrent.Callable",
            "java.util.concurrent.TimeUnit",
            "java.math.BigDecimal",
            "java.math.BigInteger",
            "java.io.File",
            "javax.inject.Inject"
        )
}
