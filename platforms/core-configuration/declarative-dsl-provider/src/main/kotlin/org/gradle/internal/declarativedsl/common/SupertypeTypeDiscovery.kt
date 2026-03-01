/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.internal.declarativedsl.common

import org.gradle.features.binding.BuildModel
import org.gradle.internal.declarativedsl.evaluationSchema.AnalysisSchemaComponent
import org.gradle.internal.declarativedsl.schemaBuilder.SupertypeDiscovery
import org.gradle.internal.declarativedsl.schemaBuilder.TypeDiscovery
import kotlin.reflect.full.isSubclassOf


internal
class SupertypeTypeDiscovery : AnalysisSchemaComponent {
    /**
     * Collect the supertypes using [SupertypeDiscovery].
     * Exclude the build model types to avoid bringing the build models into the schema (as they are likely to have non-declarative members)
     * if they only appear in the supertype (as in Foo : Definition<FooBuildModel>).
     *
     * TODO: This is not a precise solution, as it will reject BuildModel subtypes discovered in the type hierarchy even if they are not used as type arguments to Definition<T>, e.g. in
     *
     * ```kotlin
     * class SomeModel : BuildModel
     * class Foo : Definition<FooModel>, UnrelatedUsage<SomeModel> // excludes SomeModel as well
     * ```
     *
     * Properly detecting only types used as arguments to Definition<T> is more complex, especially in cases like:
     *
     * ```kotlin
     * open class Foo<T> : Definition<T>
     * class Bar : Foo<BarBuildModel>()
     * ```
     *
     * ```kotlin
     * open class Foo<T> : Definition<ParameterizedModel<T>>
     * ```
     */
    override fun typeDiscovery(): List<TypeDiscovery> = listOf(SupertypeDiscovery { it.isSubclassOf(BuildModel::class) })
}
