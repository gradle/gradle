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

package org.gradle.configurationcache.problems


internal
fun propertyDescriptionFor(trace: PropertyTrace): String =
    when (trace) {
        is PropertyTrace.Property -> trace.simplePropertyDescription()
        else -> trace.toString()
    }


private
fun PropertyTrace.Property.simplePropertyDescription(): String = when (kind) {
    PropertyKind.Field -> "field '$name' from type '${firstTypeFrom(trace).name}'"
    else -> "$kind '$name' of '${taskPathFrom(trace)}'"
}


internal
fun taskPathFrom(trace: PropertyTrace): String =
    trace.sequence.filterIsInstance<PropertyTrace.Task>().first().path


internal
fun firstTypeFrom(trace: PropertyTrace): Class<*> =
    trace.sequence.mapNotNull { typeFrom(it) }.first()


private
fun typeFrom(trace: PropertyTrace): Class<out Any>? = when (trace) {
    is PropertyTrace.Bean -> trace.type
    is PropertyTrace.Task -> trace.type
    else -> null
}
