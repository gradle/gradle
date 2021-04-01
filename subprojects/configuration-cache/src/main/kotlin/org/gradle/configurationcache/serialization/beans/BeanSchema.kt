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

package org.gradle.configurationcache.serialization.beans

import org.gradle.api.DefaultTask
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.internal.ConventionTask
import org.gradle.api.internal.TaskInternal

import org.gradle.configurationcache.problems.DisableConfigurationCacheFieldTypeCheck
import org.gradle.configurationcache.problems.PropertyKind
import org.gradle.configurationcache.serialization.IsolateContext
import org.gradle.configurationcache.serialization.Workarounds
import org.gradle.configurationcache.serialization.logUnsupported
import org.gradle.internal.reflect.ClassInspector
import org.gradle.internal.reflect.PropertyDetails

import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier.isStatic
import java.lang.reflect.Modifier.isTransient
import kotlin.reflect.KClass


internal
val unsupportedFieldDeclaredTypes = listOf(
    Configuration::class
)


internal
fun relevantStateOf(beanType: Class<*>): List<RelevantField> =
    when (ConventionTask::class.java.isAssignableFrom(beanType)) {
        true -> applyConventionMappingTo(beanType, relevantFieldsOf(beanType))
        else -> relevantFieldsOf(beanType)
    }


private
fun relevantFieldsOf(beanType: Class<*>) =
    relevantTypeHierarchyOf(beanType)
        .flatMap(Class<*>::relevantFields)
        .onEach(Field::makeAccessible)
        .map { RelevantField(it, unsupportedFieldTypeFor(it)) }
        .toList()


private
fun applyConventionMappingTo(taskType: Class<*>, relevantFields: List<RelevantField>): List<RelevantField> =
    backingFieldsOf(taskType).toMap().let { getters ->
        relevantFields.map { relevantField ->
            relevantField.run {
                getters[field]?.getters?.firstOrNull { it.returnType == field.type }?.let {
                    relevantField.copy(conventionGetter = it)
                }
            } ?: relevantField
        }
    }


private
fun backingFieldsOf(beanType: Class<*>): List<Pair<Field, PropertyDetails>> =
    ClassInspector.inspect(beanType).properties.mapNotNull { property ->
        property.backingField?.let {
            if (isNotAbstractTaskField(it)) it to property
            else null
        }
    }


internal
data class RelevantField(
    val field: Field,
    val unsupportedFieldType: KClass<*>?,
    val conventionGetter: Method? = null
)


internal
fun IsolateContext.reportUnsupportedFieldType(
    unsupportedType: KClass<*>,
    action: String,
    fieldName: String,
    fieldValue: Any? = null
) {
    withPropertyTrace(PropertyKind.Field, fieldName) {
        if (fieldValue == null) logUnsupported(action, unsupportedType)
        else logUnsupported(action, unsupportedType, fieldValue::class.java)
    }
}


internal
fun unsupportedFieldTypeFor(field: Field): KClass<*>? =
    field.takeUnless {
        field.isAnnotationPresent(DisableConfigurationCacheFieldTypeCheck::class.java)
    }?.let {
        unsupportedFieldDeclaredTypes
            .firstOrNull { it.java.isAssignableFrom(field.type) }
    }


private
fun relevantTypeHierarchyOf(beanType: Class<*>) = sequence<Class<*>> {
    var current: Class<*>? = beanType
    while (current != null) {
        if (isRelevantDeclaringClass(current)) {
            yield(current)
        }
        current = current.superclass
    }
}


private
fun isRelevantDeclaringClass(declaringClass: Class<*>): Boolean =
    declaringClass !in irrelevantDeclaringClasses


private
val irrelevantDeclaringClasses = setOf(
    Object::class.java,
    Task::class.java,
    TaskInternal::class.java,
    DefaultTask::class.java,
    ConventionTask::class.java
)


private
val Class<*>.relevantFields: Sequence<Field>
    get() = declaredFields.asSequence()
        .filterNot { field ->
            field.isStatic
                || field.isTransient
                || Workarounds.isIgnoredBeanField(field)
        }.filter { field ->
            isNotAbstractTaskField(field)
                || field.name in abstractTaskRelevantFields
        }
        .sortedBy { it.name }


@Suppress("deprecation")
private
fun isNotAbstractTaskField(field: Field) =
    field.declaringClass != org.gradle.api.internal.AbstractTask::class.java


private
val Field.isTransient
    get() = isTransient(modifiers)


private
val Field.isStatic
    get() = isStatic(modifiers)


private
val abstractTaskRelevantFields = listOf(
    "actions",
    "enabled",
    "timeout",
    "onlyIfSpec"
)


internal
fun Field.makeAccessible() {
    @Suppress("deprecation")
    if (!isAccessible) isAccessible = true
}
