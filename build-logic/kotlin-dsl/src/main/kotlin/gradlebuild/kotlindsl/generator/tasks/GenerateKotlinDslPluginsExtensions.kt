/*
 * Copyright 2020 the original author or authors.
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

package gradlebuild.kotlindsl.generator.tasks

import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.kotlin.dsl.internal.sharedruntime.codegen.LICENSE_HEADER
import org.gradle.work.DisableCachingByDefault

import java.io.File


@Suppress("unused")
@DisableCachingByDefault(because = "Not worth caching")
abstract class GenerateKotlinDslPluginsExtensions : CodeGenerationTask() {

    @get:Input
    abstract val kotlinDslPluginsVersion: Property<Any>

    override fun File.writeFiles() {
        writeFile(
            "org/gradle/kotlin/dsl/plugins/Version.kt",
            """$LICENSE_HEADER
package org.gradle.kotlin.dsl.plugins


internal
val appliedKotlinDslPluginsVersion = "${kotlinDslPluginsVersion.get()}"
"""
        )
    }
}
