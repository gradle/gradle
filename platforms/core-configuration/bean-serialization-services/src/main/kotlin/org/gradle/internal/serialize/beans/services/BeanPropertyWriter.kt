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

package org.gradle.internal.serialize.beans.services

import com.google.common.primitives.Primitives.wrap
import org.gradle.api.internal.IConventionAware
import org.gradle.internal.configuration.problems.PropertyKind
import org.gradle.internal.configuration.problems.PropertyTrace
import org.gradle.internal.extensions.stdlib.uncheckedCast
import org.gradle.internal.reflect.UnsupportedTypeException
import org.gradle.internal.serialize.graph.BeanStateWriter
import org.gradle.internal.serialize.graph.WriteContext
import org.gradle.internal.serialize.graph.reportUnsupportedFieldType
import org.gradle.internal.serialize.graph.withDebugFrame
import org.gradle.internal.serialize.graph.withPropertyTrace
import org.gradle.internal.serialize.graph.writePropertyValue
import java.lang.reflect.Field


class BeanPropertyWriter(
    beanType: Class<*>
) : BeanStateWriter {

    private
    val relevantFields = relevantStateOf(beanType)

    /**
     * Serializes a bean by serializing the value of each of its fields.
     */
    override suspend fun WriteContext.writeStateOf(bean: Any) {
        for (relevantField in relevantFields) {
            val field = relevantField.field
            val fieldName = field.name
            val fieldValue =
                when (val isExplicitValue = relevantField.isExplicitValueField) {
                    null -> field.get(bean)
                    else -> conventionValueOf(bean, field, isExplicitValue)
                }
            relevantField.unsupportedFieldType?.let {
                reportUnsupportedFieldType(it, "serialize", fieldName, fieldValue)
            }
            if (relevantField.unsupportedFieldType == null) {
                checkKotlinDelegateForUnsupportedValue(field, fieldName, fieldValue)
            }
            withDebugFrame({ field.debugFrameName() }) {
                writePropertyValue(PropertyKind.Field, fieldName, fieldValue)
            }
        }
    }

    /**
     * Inspects the value of a Kotlin property delegate field for unsupported types.
     *
     * Kotlin `by`-delegates (e.g. `by lazy { … }`) create a backing field whose
     * declared type is the delegate class, hiding the actual value type from
     * [unsupportedFieldTypeFor].  This method looks inside the delegate and
     * reports a configuration cache error when the wrapped value is an
     * unsupported type (such as [org.gradle.api.artifacts.Configuration]).
     *
     * Only reports when the Kotlin property type (the getter return type) is itself
     * an unsupported type.  When the user explicitly narrows the property type to
     * a safe supertype (e.g. `val x: FileCollection by lazy { … }`), the
     * round-trip through serialization succeeds and no error is reported.
     *
     * The error is routed through [onError] so that it appears in the failure
     * cause chain (for `assertHasCause`) and in the CC problems report.
     */
    private
    suspend fun WriteContext.checkKotlinDelegateForUnsupportedValue(field: Field, fieldName: String, fieldValue: Any?) {
        if (!KotlinDelegateInspector.isKotlinDelegate(fieldValue)) return
        val delegateValue = KotlinDelegateInspector.extractValue(fieldValue!!) ?: return
        val unsupportedType = unsupportedFieldDeclaredTypes.firstOrNull { it.java.isInstance(delegateValue) } ?: return
        if (isKotlinPropertyTypeUnsupported(field)) {
            reportUnsupportedKotlinDelegateType(field, fieldName, fieldValue, unsupportedType)
        }
    }

    /**
     * Reports a configuration cache error for a Kotlin delegate whose result type
     * is unsupported.  The error is routed through [onError] so that it appears
     * in both the failure cause chain and the CC problems report.
     */
    private
    suspend fun WriteContext.reportUnsupportedKotlinDelegateType(
        field: Field,
        fieldName: String,
        delegate: Any,
        unsupportedType: kotlin.reflect.KClass<*>
    ) {
        val delegateKind = KotlinDelegateInspector.delegateKindName(delegate)
        val propertyName = field.name.removeSuffix("\$delegate")
        val taskDescription = trace.sequence
            .filterIsInstance<PropertyTrace.Task>()
            .firstOrNull()
            ?.let { "task ${it.path} of type ${it.type.simpleName}" }
            ?: trace.toString()

        val exception = UnsupportedTypeException(
            "Cannot serialize $delegateKind delegate returning ${unsupportedType.simpleName} in $taskDescription." +
                "  The result type of this delegate (${unsupportedType.qualifiedName}) is not supported with the configuration cache.",
            listOf("Declare the property type explicitly as a supported type (e.g., val $propertyName: FileCollection by $delegateKind { ... }).")
        )
        withPropertyTrace(PropertyKind.Field, fieldName) {
            onError(exception) {
                text("failed to serialize value of ")
                reference(trace.toString())
            }
        }
    }

    /**
     * Checks whether the Kotlin property backed by [delegateField] has a return type
     * that is an unsupported configuration cache type.
     *
     * Kotlin compiles `val x by delegate` into a field named `x$delegate` and a
     * getter `getX()`.  This method finds the getter and checks its return type.
     *
     * @throws DelegateInspectionException if the getter cannot be found for a `$delegate` field
     */
    internal
    fun isKotlinPropertyTypeUnsupported(delegateField: Field): Boolean {
        val propertyName = delegateField.name.removeSuffix("\$delegate")
        if (propertyName == delegateField.name) {
            throw DelegateInspectionException(
                "Field '${delegateField.name}' on ${delegateField.declaringClass.name} " +
                    "does not follow the Kotlin delegate naming convention (<name>\$delegate)."
            )
        }

        val getterName = "get${propertyName.replaceFirstChar { it.uppercase() }}"
        return try {
            val returnType = delegateField.declaringClass.getMethod(getterName).returnType
            unsupportedFieldDeclaredTypes.any { it.java.isAssignableFrom(returnType) }
        } catch (e: NoSuchMethodException) {
            throw DelegateInspectionException(
                "Could not find getter '$getterName()' on ${delegateField.declaringClass.name} " +
                    "for delegate field '${delegateField.name}'. " +
                    "Available methods: ${delegateField.declaringClass.methods.map { it.name }.sorted().distinct().joinToString(", ")}",
                e
            )
        }
    }

    private
    fun conventionValueOf(bean: Any, field: Field, isExplicitValue: Field) =
        field.get(bean).let { fieldValue ->
            if (isExplicitValue.get(bean).uncheckedCast()) {
                fieldValue
            } else {
                getConventionValue(bean, field, fieldValue)
                    ?.takeIf { conventionValue ->
                        // Prevent convention value to be assigned to a field of incompatible type
                        // A common cause is a regular field type being promoted to a Property/Provider type.
                        conventionValue.isAssignableTo(field.type)
                    } ?: fieldValue
            }
        }

    private
    fun getConventionValue(bean: Any, field: Field, fieldValue: Any?): Any? =
        bean.uncheckedCast<IConventionAware>()
            .conventionMapping
            .getConventionValue(fieldValue, field.name, false)

    private
    fun Field.debugFrameName() =
        "${declaringClass.typeName}.$name"

    private
    fun Any?.isAssignableTo(type: Class<*>) =
        (if (type.isPrimitive) wrap(type) else type)
            .isInstance(this)
}
