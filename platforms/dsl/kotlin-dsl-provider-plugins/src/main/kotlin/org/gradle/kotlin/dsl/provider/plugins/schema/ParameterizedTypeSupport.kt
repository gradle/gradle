/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.kotlin.dsl.provider.plugins.schema

import org.gradle.api.reflect.TypeOf
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.WildcardType


internal data class TypeProjection(val clazz: Class<*>, val projection: TypeProjectionKind = TypeProjectionKind.NONE)


internal enum class TypeProjectionKind {
    NONE, OUT, IN
}


/**
 * Workaround: The [TypeOf] infrastructure handles parameterized types specially.
 * Passing the raw [Class] obtained from the class loader to [TypeOf.parameterizedTypeOf] would not work.
 * We need to provide a [ParameterizedType] instance.
 */
internal fun parameterizedTypeOfRawGenericClass(typeArgs: List<TypeProjection>, loadedClass: Class<*>): TypeOf<Any> =
    TypeOf.typeOf(object : ParameterizedType {
        override fun getActualTypeArguments(): Array<Type> = typeArgs.map { (clazz, projection) ->
            when (projection) {
                TypeProjectionKind.NONE -> clazz
                TypeProjectionKind.OUT -> object : WildcardType {
                    override fun getUpperBounds(): Array<out Type> = arrayOf(clazz)
                    override fun getLowerBounds() = emptyArray<Type>()
                }
                TypeProjectionKind.IN -> object : WildcardType {
                    override fun getUpperBounds(): Array<out Type> = emptyArray()
                    override fun getLowerBounds() = arrayOf(clazz)
                }
            }
        }.toTypedArray<Type>()

        override fun getRawType(): Type = loadedClass

        override fun getOwnerType() = null
    })
