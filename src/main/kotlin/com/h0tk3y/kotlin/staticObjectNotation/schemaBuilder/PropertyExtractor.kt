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

import com.h0tk3y.kotlin.staticObjectNotation.AccessFromCurrentReceiverOnly
import com.h0tk3y.kotlin.staticObjectNotation.Adding
import com.h0tk3y.kotlin.staticObjectNotation.Builder
import com.h0tk3y.kotlin.staticObjectNotation.Configuring
import com.h0tk3y.kotlin.staticObjectNotation.HasDefaultValue
import com.h0tk3y.kotlin.staticObjectNotation.HiddenInRestrictedDsl
import com.h0tk3y.kotlin.staticObjectNotation.Restricted
import java.util.Locale
import kotlin.reflect.KCallable
import kotlin.reflect.KClass
import kotlin.reflect.KMutableProperty
import kotlin.reflect.KProperty
import kotlin.reflect.KVisibility
import kotlin.reflect.full.instanceParameter
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor

class PropertyExtractor(private val includeMemberFilter: MemberFilter) {
    fun extractProperties(kClass: KClass<*>) =
        (propertiesFromAccessorsOf(kClass) + memberPropertiesOf(kClass)).distinctBy { it }

    private fun propertiesFromAccessorsOf(kClass: KClass<*>): List<CollectedPropertyInformation> {
        val functionsByName = kClass.memberFunctions.groupBy { it.name }
        val getters = functionsByName
            .filterKeys { it.startsWith("get") && it.substringAfter("get").firstOrNull()?.isUpperCase() == true }
            .mapValues { (_, functions) -> functions.singleOrNull { fn -> fn.parameters.all { it == fn.instanceParameter } } }
            .filterValues { it != null && includeMemberFilter.shouldIncludeMember(it) }
        return getters.map { (name, getter) ->
            checkNotNull(getter)
            val nameAfterGet = name.substringAfter("get")
            val propertyName = nameAfterGet.replaceFirstChar { it.lowercase(Locale.getDefault()) }
            val type = getter.returnType.toDataTypeRefOrError()
            val hasSetter = functionsByName["set$nameAfterGet"].orEmpty().any { fn -> fn.parameters.singleOrNull { it != fn.instanceParameter }?.type == getter.returnType }
            val isHidden = getter.annotations.any { it is HiddenInRestrictedDsl }
            val isDirectAccessOnly = getter.annotations.any { it is AccessFromCurrentReceiverOnly }
            CollectedPropertyInformation(propertyName, getter.returnType, type, !hasSetter, true, isHidden, isDirectAccessOnly)
        }
    }

    private fun memberPropertiesOf(kClass: KClass<*>): List<CollectedPropertyInformation> = kClass.memberProperties
        .filter { property ->
            (includeMemberFilter.shouldIncludeMember(property) ||
                kClass.primaryConstructor?.parameters.orEmpty().any { it.name == property.name && it.type == property.returnType })
                && property.visibility == KVisibility.PUBLIC
        }.map { property -> kPropertyInformation(property) }

    private fun kPropertyInformation(property: KProperty<*>): CollectedPropertyInformation {
        val isReadOnly = property !is KMutableProperty<*>
        val isHidden = property.annotationsWithGetters.any { it is HiddenInRestrictedDsl }
        val isDirectAccessOnly = property.annotationsWithGetters.any { it is AccessFromCurrentReceiverOnly }
        return CollectedPropertyInformation(
            property.name,
            property.returnType,
            property.returnType.toDataTypeRefOrError(),
            isReadOnly,
            hasDefaultValue = run {
                isReadOnly || property.annotationsWithGetters.any { it is HasDefaultValue }
            },
            isHiddenInRestrictedDsl = isHidden,
            isDirectAccessOnly = isDirectAccessOnly
        )
    }
}

fun interface MemberFilter {
    fun shouldIncludeMember(member: KCallable<*>): Boolean
}

val isPublicAndRestricted: MemberFilter = MemberFilter { member: KCallable<*> ->
    member.visibility == KVisibility.PUBLIC &&
        member.annotationsWithGetters.any {
            it is Builder || it is Configuring || it is Adding || it is Restricted || it is HasDefaultValue
        }
}

private val KCallable<*>.annotationsWithGetters: List<Annotation>
    get() = this.annotations + if (this is KProperty) this.getter.annotations else emptyList()
