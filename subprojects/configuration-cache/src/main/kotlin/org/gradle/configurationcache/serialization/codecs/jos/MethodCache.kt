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

package org.gradle.configurationcache.serialization.codecs.jos

import org.gradle.internal.reflect.ClassInspector

import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap


internal
class MethodCache(

    private
    val predicate: Method.() -> Boolean

) {
    private
    val methodCache = ConcurrentHashMap<Class<*>, Method?>()

    fun forObject(value: Any) =
        forClass(value.javaClass)

    fun forClass(type: Class<*>) = methodCache.computeIfAbsent(type) {
        it.firstAccessibleMatchingMethodOrNull(predicate)
    }
}


internal
fun Class<*>.firstAccessibleMatchingMethodOrNull(predicate: Method.() -> Boolean): Method? =
    allMethods().firstAccessibleMatchingMethodOrNull(predicate)


internal
fun Iterable<Method>.firstAccessibleMatchingMethodOrNull(predicate: Method.() -> Boolean): Method? =
    find(predicate)?.apply { isAccessible = true }


internal
fun Class<*>.firstMatchingMethodOrNull(predicate: Method.() -> Boolean): Method? =
    allMethods().find(predicate)


internal
fun Class<*>.allMethods() =
    ClassInspector.inspect(this).allMethods
