/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.internal.restricteddsl.project

import com.h0tk3y.kotlin.staticObjectNotation.schemaBuilder.CollectedPropertyInformation
import com.h0tk3y.kotlin.staticObjectNotation.schemaBuilder.DataClassSchemaProducer
import kotlin.reflect.KClass
import kotlin.reflect.KFunction


internal
class ExtensionPropertiesProducer(private val extensionPropertiesByClass: Map<KClass<*>, Iterable<CollectedPropertyInformation>>) : DataClassSchemaProducer {
    override fun getOtherClassesToVisitFrom(kClass: KClass<*>): Iterable<KClass<*>> = emptyList()

    override fun extractPropertiesOf(kClass: KClass<*>): Iterable<CollectedPropertyInformation> = extensionPropertiesByClass[kClass] ?: emptyList()

    override fun getFunctionsToExtract(kClass: KClass<*>): Iterable<KFunction<*>> = emptyList()

    override fun getConstructorsToExtract(kClass: KClass<*>): Iterable<KFunction<*>> = emptyList()
}
