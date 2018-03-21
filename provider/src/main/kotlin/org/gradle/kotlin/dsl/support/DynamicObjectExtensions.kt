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
package org.gradle.kotlin.dsl.support

import org.gradle.api.InvalidUserCodeException

import org.gradle.api.internal.DynamicObjectAware
import org.gradle.api.internal.plugins.DslObject
import org.gradle.internal.Cast.uncheckedCast
import org.gradle.internal.metaobject.DynamicObject


internal
fun dynamicObjectFor(target: Any): DynamicObject =
    (target as? DynamicObjectAware ?: DslObject(target)).asDynamicObject


internal
fun <T> DynamicObject.getPropertyValue(name: String, nullable: Boolean, describe: () -> String): T =
    tryGetProperty(name).run {
        val foundValue = if (isFound) value else null
        if (nullable || (isFound && foundValue != null)) return uncheckedCast(foundValue)
        else throw InvalidUserCodeException("Cannot get non-null property '$name' on ${describe()} as it ${if (isFound) "is null" else "does not exist"}")
    }
