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

package com.h0tk3y.kotlin.staticObjectNotation.schemaBuilder

import com.h0tk3y.kotlin.staticObjectNotation.HiddenInRestrictedDsl
import com.h0tk3y.kotlin.staticObjectNotation.analysis.DataTypeRef
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KType
import kotlin.reflect.full.memberFunctions

interface DataClassSchemaProducer {
    fun getOtherClassesToVisitFrom(kClass: KClass<*>): Iterable<KClass<*>>
    fun extractPropertiesOf(kClass: KClass<*>): Iterable<CollectedPropertyInformation>
    // TODO: allow more flexibility in producing functions and constructors, similar to what is done with properties
    fun getFunctionsToExtract(kClass: KClass<*>): Iterable<KFunction<*>>
    fun getConstructorsToExtract(kClass: KClass<*>): Iterable<KFunction<*>>
}

data class CollectedPropertyInformation(
    val name: String,
    val originalReturnType: KType,
    val returnType: DataTypeRef,
    val isReadOnly: Boolean,
    val hasDefaultValue: Boolean,
    val isHiddenInRestrictedDsl: Boolean,
    val isDirectAccessOnly: Boolean
)

fun DataClassSchemaProducer.plus(other: DataClassSchemaProducer) = CompositeDataClassSchemaProducer(
    buildList {
        if (this@plus is CompositeDataClassSchemaProducer) addAll(contributors) else add(this@plus)
        if (other is CompositeDataClassSchemaProducer) addAll(other.contributors) else add(other)
    }
)

class CompositeDataClassSchemaProducer(
    internal val contributors: Iterable<DataClassSchemaProducer>
) : DataClassSchemaProducer {
    override fun getOtherClassesToVisitFrom(kClass: KClass<*>): Iterable<KClass<*>> =
        contributors.flatMapTo(mutableSetOf()) { it.getOtherClassesToVisitFrom(kClass) }

    override fun extractPropertiesOf(kClass: KClass<*>): Iterable<CollectedPropertyInformation> =
        contributors.flatMapTo(mutableSetOf()) { it.extractPropertiesOf(kClass) }

    override fun getFunctionsToExtract(kClass: KClass<*>): Iterable<KFunction<*>> =
        contributors.flatMapTo(mutableSetOf()) { it.getFunctionsToExtract(kClass) }

    override fun getConstructorsToExtract(kClass: KClass<*>): Iterable<KFunction<*>> =
        contributors.flatMapTo(mutableSetOf()) { it.getConstructorsToExtract(kClass) }
}

val defaultDataClassSchemaProducer
    get() = DefaultDataClassSchemaProducer(PropertyExtractor(isPublicAndRestricted), isPublicAndRestricted)

class DefaultDataClassSchemaProducer(
    private val propertyExtractor: PropertyExtractor,
    private val functionMemberFilter: MemberFilter
) : DataClassSchemaProducer {
    /**
     * This implementation does not add any new types to visit based on the types it is presented.
     */
    override fun getOtherClassesToVisitFrom(kClass: KClass<*>): Iterable<KClass<*>> = emptyList()

    override fun extractPropertiesOf(kClass: KClass<*>): Iterable<CollectedPropertyInformation> = propertyExtractor.extractProperties(kClass)

    override fun getFunctionsToExtract(kClass: KClass<*>): Iterable<KFunction<*>> = kClass.memberFunctions.filter(functionMemberFilter::shouldIncludeMember)

    override fun getConstructorsToExtract(kClass: KClass<*>): Iterable<KFunction<*>> = kClass.constructors.filter(functionMemberFilter::shouldIncludeMember)
}
