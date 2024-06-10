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

package org.gradle.internal.declarativedsl.evaluationSchema

import org.gradle.internal.declarativedsl.schemaBuilder.TypeDiscovery
import kotlin.reflect.KClass


/**
 * Utility [TypeDiscovery] implementation that allows introducing [discoverClasses] as soon as [keyClass] is encountered in type discovery.
 */
internal
class FixedTypeDiscovery(private val keyClass: KClass<*>, private val discoverClasses: List<KClass<*>>) : TypeDiscovery {
    override fun getClassesToVisitFrom(kClass: KClass<*>): Iterable<KClass<*>> =
        when (kClass) {
            keyClass -> discoverClasses
            else -> emptyList()
        }
}
