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
import org.gradle.api.logging.Logging
import org.gradle.internal.configuration.problems.PropertyKind
import org.gradle.internal.extensions.stdlib.uncheckedCast
import org.gradle.internal.reflect.UnsupportedTypeException
import org.gradle.internal.serialize.graph.BeanStateWriter
import org.gradle.internal.serialize.graph.WriteContext
import org.gradle.internal.serialize.graph.findCodecThatWidensIncompatibly
import org.gradle.internal.serialize.graph.reportIfIncompatibleRoundtrip
import org.gradle.internal.serialize.graph.reportSerializationProblem
import org.gradle.internal.serialize.graph.withPropertyTrace
import org.gradle.internal.serialize.graph.withDebugFrame
import org.gradle.internal.serialize.graph.writePropertyValue
import java.lang.reflect.Field


class BeanPropertyWriter(
    beanType: Class<*>
) : BeanStateWriter {

    private
    companion object {
        private val logger = Logging.getLogger(BeanPropertyWriter::class.java)
    }

    private
    val relevantFields = relevantStateOf(beanType)

    /**
     * Serializes a bean by serializing the value of each of its fields.
     *
     * For each field, runs two store-time roundtrip checks. When either
     * reports an incompatibility, the field's value is dropped — `null` is
     * written in its place so the cache remains self-consistent and the
     * read-side observes a null/empty value on load.
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
            val dropped =
                reportIfIncompatibleRoundtrip(field, fieldName, fieldValue) ||
                    reportIfUnsupportedKotlinDelegate(field, fieldName, fieldValue)
            val effectiveValue = if (dropped) null else fieldValue
            withDebugFrame({ field.debugFrameName() }) {
                writePropertyValue(PropertyKind.Field, fieldName, effectiveValue)
            }
        }
    }

    /**
     * Inspects a Kotlin property delegate field's value for an unsupported
     * roundtrip into the property's declared getter return type.
     *
     * Uninitialized `by lazy` delegates are deliberately skipped: there is no
     * value yet to type-check against the property's getter return type, and
     * forcing the lazy here would change observable build behaviour. The skip
     * is logged at info level *and* emits a deferred problem so the bypass is
     * visible in the configuration cache problems report — diagnosing a later
     * load-side failure shouldn't require enabling debug logging. Other delegate
     * kinds (`Delegates.observable`, `Delegates.vetoable`) carry a value from
     * construction; `Delegates.notNull` throws on read until first assignment.
     * All three are checked normally because their value (if any) is available
     * without forcing.
     *
     * @return `true` when the field's value must be dropped from the cache.
     */
    private
    suspend fun WriteContext.reportIfUnsupportedKotlinDelegate(field: Field, fieldName: String, fieldValue: Any?): Boolean {
        // A Kotlin `by`-delegate is identified by BOTH the field name (compiler emits `<name>$delegate`)
        // AND the value type. A regular field declared as `Lazy<T>` is not a delegate even though its
        // value satisfies isKotlinDelegate(); skip it so normal codec-driven serialization handles it.
        if (!field.name.endsWith("\$delegate") || !KotlinDelegateInspector.isKotlinDelegate(fieldValue)) return false
        if (fieldValue is Lazy<*> && !fieldValue.isInitialized()) {
            // Deliberate skip — see KDoc above. Logged and reported as a deferred
            // problem so a later load-side failure has a breadcrumb back here.
            val propertyName = field.name.removeSuffix("\$delegate")
            logger.info(
                "Skipping widening-roundtrip check for uninitialized `by lazy` delegate '{}' on {}",
                field.name, field.declaringClass.name
            )
            val exception = UnsupportedTypeException(
                "Cannot type-check `by lazy` delegate for property '$propertyName' in ${trace.taskDescription()}: " +
                    "the delegate was not initialized at configuration cache store time, " +
                    "so the load-side value type cannot be verified.",
                listOf("Initialize the lazy at configuration time if you require store-time type validation.")
            )
            withPropertyTrace(PropertyKind.Field, fieldName) {
                reportSerializationProblem(exception)
            }
            return false
        }
        val delegateValue = KotlinDelegateInspector.extractValue(fieldValue!!) ?: return false
        val kotlinGetterReturnType = KotlinDelegateInspector.kotlinPropertyGetterReturnType(field)
        val widening = findCodecThatWidensIncompatibly(kotlinGetterReturnType, delegateValue.javaClass) ?: return false
        val delegateKind = KotlinDelegateInspector.delegateKindName(fieldValue)
        val propertyName = field.name.removeSuffix("\$delegate")
        val exception = UnsupportedTypeException(
            "Cannot serialize $delegateKind delegate for property '$propertyName: ${kotlinGetterReturnType.simpleName}' in ${trace.taskDescription()}. " +
                "The codec for the delegate's value produces ${widening.publicDecodedType.name} on load, " +
                "which cannot be assigned to a property of type ${kotlinGetterReturnType.name}.",
            listOf(widening.wideningFix)
        )
        withPropertyTrace(PropertyKind.Field, fieldName) {
            reportSerializationProblem(exception)
        }
        return true
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
