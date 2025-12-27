/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.internal.serialize.codecs.core.jos

import org.gradle.internal.reflect.ClassInspector
import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType.methodType
import java.lang.reflect.Method
import java.lang.reflect.Modifier.isStatic


internal
class ReadResolveCache {

    private
    val cache = object : ClassValue<MethodHandle?>() {
        override fun computeValue(type: Class<*>): MethodHandle? =
            type.allMethods()
                .firstAccessibleMatchingMethodOrNull {
                    isReadResolve()
                }
                ?.let {
                    MethodHandles.lookup()
                        .unreflect(it)
                        .asType(methodType(Any::class.java, Any::class.java))
                }
    }

    fun forObject(value: Any) =
        forClass(value.javaClass)

    fun forClass(type: Class<*>): MethodHandle? =
        cache.get(type)
}


internal
fun Method.isReadResolve() =
    !isStatic(modifiers)
        && parameterCount == 0
        && returnType == Any::class.java
        && name == "readResolve"


internal
fun Iterable<Method>.firstAccessibleMatchingMethodOrNull(predicate: Method.() -> Boolean): Method? =
    find(predicate)?.apply { isAccessible = true }


internal
fun Class<*>.allMethods() =
    ClassInspector.inspectMethods(this)
