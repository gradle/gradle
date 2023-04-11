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
import org.gradle.api.artifacts.Configuration
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.internal.ConventionTask
import org.gradle.api.internal.IConventionAware
import org.gradle.configurationcache.problems.PropertyKind
import org.gradle.configurationcache.serialization.MutableIsolateContext
import org.gradle.configurationcache.serialization.Workarounds
import org.gradle.configurationcache.serialization.logUnsupported
import org.gradle.internal.instantiation.generator.AsmBackedClassGenerator
import org.gradle.internal.reflect.ClassInspector
import java.lang.reflect.AccessibleObject
import java.lang.reflect.Field
import java.lang.reflect.Modifier.isStatic
import java.lang.reflect.Modifier.isTransient
import kotlin.reflect.KClass


internal
val unsupportedFieldDeclaredTypes = listOf(
    Configuration::class,
    SourceDirectorySet::class
)


internal
fun relevantStateOf(beanType: Class<*>): List<RelevantField> =
    when (IConventionAware::class.java.isAssignableFrom(beanType)) {
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
    conventionAwareFieldsOf(taskType).toMap().let { flags ->
        relevantFields.map { relevantField ->
            relevantField.run {
                flags[field]?.let { flagField ->
                    copy(isExplicitValueField = flagField.apply(Field::makeAccessible))
                }
            } ?: relevantField
        }
    }


/**
 * Returns every property backing field for which a corresponding convention mapping flag field also exists.
 *
 * The convention mapping flag field is a Boolean field injected by [AsmBackedClassGenerator] in order to capture
 * whether a convention mapped property has been explicitly set or not.
 */
private
fun conventionAwareFieldsOf(beanType: Class<*>): Sequence<Pair<Field, Field>> =
    ClassInspector.inspect(beanType).let { details ->
        details.properties.asSequence().mapNotNull { property ->
            property.backingField?.let { backingField ->
                val flagFieldName = AsmBackedClassGenerator.propFieldName(backingField.name)
                details
                    .instanceFields
                    .firstOrNull { field ->
                        field.declaringClass == beanType
                            && field.name == flagFieldName
                            && field.type == java.lang.Boolean.TYPE
                    }?.let { flagField ->
                        backingField to flagField
                    }
            }
        }
    }


internal
data class RelevantField(
    val field: Field,
    val unsupportedFieldType: KClass<*>?,
    /**
     * Boolean flag field injected by [AsmBackedClassGenerator] to capture
     * whether a convention mapped property has been explicitly set or not.
     */
    val isExplicitValueField: Field? = null
)


internal
fun MutableIsolateContext.reportUnsupportedFieldType(
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
    unsupportedFieldDeclaredTypes.firstOrNull { it.java.isAssignableFrom(field.type) }


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
fun AccessibleObject.makeAccessible() {
    @Suppress("deprecation")
    if (!isAccessible) isAccessible = true
}
