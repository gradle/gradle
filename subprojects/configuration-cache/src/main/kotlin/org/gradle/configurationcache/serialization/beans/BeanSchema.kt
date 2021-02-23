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

import java.lang.reflect.Field
import java.lang.reflect.Modifier
import kotlin.reflect.KClass


internal
val unsupportedFieldDeclaredTypes = listOf(
    Configuration::class
)


internal
fun relevantStateOf(beanType: Class<*>): List<RelevantField> =
    relevantTypeHierarchyOf(beanType)
        .flatMap(Class<*>::relevantFields)
        .onEach(Field::makeAccessible)
        .map { RelevantField(it, unsupportedFieldTypeFor(it)) }
        .toList()


internal
class RelevantField(
    val field: Field,
    val unsupportedFieldType: KClass<*>?
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
            Modifier.isStatic(field.modifiers)
                || Modifier.isTransient(field.modifiers)
                || Workarounds.isIgnoredBeanField(field)
        }
        .filter { field ->
            @Suppress("deprecation")
            field.declaringClass != org.gradle.api.internal.AbstractTask::class.java || field.name in abstractTaskRelevantFields
        }
        .sortedBy { it.name }


private
val abstractTaskRelevantFields = listOf("actions", "enabled", "timeout", "onlyIfSpec")


internal
fun Field.makeAccessible() {
    @Suppress("deprecation")
    if (!isAccessible) isAccessible = true
}
