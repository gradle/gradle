/*
 * Copyright 2025 the original author or authors.
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

import java.lang.reflect.Field
import kotlin.properties.ReadOnlyProperty
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.jvm.jvmErasure


/**
 * Reflective utilities for inspecting Kotlin property delegates ahead of
 * configuration cache serialization.
 *
 * Kotlin `by`-delegates create a backing field named `<property>$delegate`
 * whose *declared* type is the delegate class (e.g. `Lazy`), hiding the
 * actual value type from the WideningCodec-driven check that operates on
 * the field's runtime value. This inspector exposes two views of that
 * compile-time machinery:
 *
 * - **Value-side** ([extractValue], [isKotlinDelegate], [delegateKindName]):
 *   query the delegate for the value it currently holds. All property
 *   delegates recognised here implement [Lazy] or [ReadOnlyProperty] (which
 *   [ReadWriteProperty] extends), so we can call the delegate's own contract
 *   rather than reflecting into private fields.
 * - **Field-side** ([kotlinPropertyGetterReturnType]): given a `$delegate`
 *   backing field, use [kotlin-reflect][kotlin.reflect] to look up the
 *   corresponding Kotlin property and return its declared type (the
 *   user-visible property type).
 *
 * Recognised delegate kinds:
 * - [Lazy] (`by lazy { … }`) — the most common delegate in Gradle tasks
 * - [ReadOnlyProperty] / [ReadWriteProperty] — covers `Delegates.observable`,
 *   `Delegates.vetoable`, `Delegates.notNull`, and any user-defined delegate
 *   that participates in Kotlin's standard delegate protocol
 */
internal object KotlinDelegateInspector {

    /**
     * A dummy [KProperty] passed to [ReadOnlyProperty.getValue] when we don't
     * have the real property at hand. The built-in delegates
     * ([kotlin.properties.ObservableProperty], `NotNullVar`) only read
     * [KProperty.name] — the reference below is a real KProperty so `.name`
     * is safe to read even if the delegate uses it (e.g., in an error
     * message).
     */
    private val DUMMY_PROPERTY_BACKER: Nothing? = null

    private val DUMMY_PROPERTY: KProperty<*> = KotlinDelegateInspector::DUMMY_PROPERTY_BACKER

    /**
     * Extracts the current value held by a Kotlin property delegate by asking
     * the delegate itself, via its [Lazy] or [ReadOnlyProperty] contract.
     *
     * Callers must guard with [isKotlinDelegate] before calling this method.
     *
     * @return the wrapped value, or `null` when the delegate has no value
     *   available yet — either an un-evaluated [Lazy] or a
     *   `Delegates.notNull` that has not been assigned (its `getValue`
     *   throws [IllegalStateException] before first assignment).
     *
     * @throws DelegateInspectionException if [delegate] is not a recognised delegate type
     */
    fun extractValue(delegate: Any): Any? = when (delegate) {
        is Lazy<*> -> if (delegate.isInitialized()) delegate.value else null
        is ReadOnlyProperty<*, *> -> extractFromPropertyDelegate(delegate)
        else -> throw DelegateInspectionException(
            "Not a recognised Kotlin property delegate: ${delegate::class.java.name}. " +
                "Callers must guard with isKotlinDelegate() before calling extractValue()."
        )
    }

    /**
     * Returns `true` when [value] is a recognised Kotlin property delegate type.
     */
    fun isKotlinDelegate(value: Any?): Boolean =
        value is Lazy<*> || value is ReadOnlyProperty<*, *>

    /**
     * Returns a human-readable label for the delegate kind, used in diagnostic messages.
     *
     * @throws DelegateInspectionException if [delegate] is not a recognised delegate type;
     *   callers must guard with [isKotlinDelegate] first.
     */
    fun delegateKindName(delegate: Any): String = when (delegate) {
        is Lazy<*> -> "lazy"
        is ReadWriteProperty<*, *> -> "observable/vetoable"
        is ReadOnlyProperty<*, *> -> "delegate"
        else -> throw DelegateInspectionException(
            "Not a recognised Kotlin property delegate: ${delegate::class.java.name}. " +
                "Callers must guard with isKotlinDelegate() before calling delegateKindName()."
        )
    }

    /**
     * Returns the declared return type of the Kotlin property backed by the given
     * `$delegate` field.
     *
     * Uses [kotlin-reflect][kotlin.reflect] to interrogate the declaring class,
     * which correctly handles Kotlin's JVM naming conventions — notably boolean
     * `val isReady by lazy {}` compiles to a getter named `isReady()` (not
     * `getIsReady()`) and would defeat any name-based getter lookup.
     *
     * @throws DelegateInspectionException if [delegateField] does not follow the
     *   `<name>$delegate` naming convention, or if the Kotlin property backing
     *   the delegate cannot be found.
     */
    fun kotlinPropertyGetterReturnType(delegateField: Field): Class<*> {
        val propertyName = delegateField.name.removeSuffix("\$delegate")
        if (propertyName == delegateField.name) {
            throw DelegateInspectionException(
                "Field '${delegateField.name}' on ${delegateField.declaringClass.name} " +
                    "does not follow the Kotlin delegate naming convention (<name>\$delegate)."
            )
        }
        val property = delegateField.declaringClass.kotlin.declaredMemberProperties
            .find { it.name == propertyName }
            ?: throw DelegateInspectionException(
                "Could not find Kotlin property '$propertyName' on ${delegateField.declaringClass.name} " +
                    "for delegate field '${delegateField.name}'."
            )
        return property.returnType.jvmErasure.java
    }

    /**
     * Asks the delegate for its current value via [ReadOnlyProperty.getValue].
     * The built-in delegates ignore the receiver — we pass `null` — and only
     * touch the property argument to read its name in error messages, which
     * is why [DUMMY_PROPERTY] is a real [KProperty].
     *
     * Returns `null` when the delegate signals "no value yet" by throwing
     * [IllegalStateException] (this is the contract used by
     * `Delegates.notNull` before first assignment).
     */
    private fun extractFromPropertyDelegate(delegate: ReadOnlyProperty<*, *>): Any? =
        try {
            @Suppress("UNCHECKED_CAST")
            (delegate as ReadOnlyProperty<Any?, Any?>).getValue(null, DUMMY_PROPERTY)
        } catch (_: IllegalStateException) {
            null
        }
}


/**
 * Thrown when a Kotlin property delegate cannot be inspected, either because
 * it is not a recognised delegate type or because reflective access failed.
 */
internal class DelegateInspectionException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)
