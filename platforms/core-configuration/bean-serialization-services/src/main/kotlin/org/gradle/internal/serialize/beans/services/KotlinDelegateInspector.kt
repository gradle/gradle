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

import kotlin.properties.ReadOnlyProperty
import kotlin.properties.ReadWriteProperty


/**
 * Extracts the current value from Kotlin property delegates so that it can be
 * inspected for unsupported types before configuration cache serialization.
 *
 * Kotlin `by`-delegates create a backing field whose *declared* type is the
 * delegate class (e.g. `Lazy`), hiding the actual value type from the
 * field-level check in [unsupportedFieldTypeFor].  This inspector looks
 * *inside* recognised delegate wrappers to retrieve the wrapped value.
 *
 * Currently supports:
 * - [Lazy] (`by lazy { … }`) — the most common delegate in Gradle tasks
 * - [ReadWriteProperty] subclasses (`Delegates.observable`, `Delegates.vetoable`,
 *   `Delegates.notNull`) — via reflective access to the conventional `value` field
 * - [ReadOnlyProperty] subclasses — same reflective fallback
 */
internal object KotlinDelegateInspector {
    /**
     * Extracts the current value held by a Kotlin property delegate.
     *
     * Callers must guard with [isKotlinDelegate] before calling this method.
     *
     * @return the wrapped value, or `null` when the delegate has not yet been
     *   initialised (e.g. un-evaluated `lazy`) or holds a null value.
     *
     * @throws DelegateInspectionException if [delegate] is not a recognised delegate type,
     *   or if a recognised delegate type cannot be reflectively inspected
     */
    fun extractValue(delegate: Any): Any? = when (delegate) {
        is Lazy<*> -> extractFromLazy(delegate)
        is ReadWriteProperty<*, *> -> extractViaValueField(delegate)
        is ReadOnlyProperty<*, *> -> extractViaValueField(delegate)
        else -> throw DelegateInspectionException(
            "Not a recognised Kotlin property delegate: ${delegate::class.java.name}. " +
                "Callers must guard with isKotlinDelegate() before calling extractValue()."
        )
    }

    /**
     * Returns `true` when [value] is a recognised Kotlin property delegate type.
     */
    fun isKotlinDelegate(value: Any?): Boolean = when (value) {
        is Lazy<*> -> true
        is ReadWriteProperty<*, *> -> true
        is ReadOnlyProperty<*, *> -> true
        else -> false
    }

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

    private fun extractFromLazy(delegate: Lazy<*>): Any? =
        if (delegate.isInitialized()) delegate.value else null

    /**
     * Reflective fallback for delegates that store their value in a field named `value`.
     * This covers [kotlin.properties.ObservableProperty] (`Delegates.observable` / `vetoable`)
     * and the internal `NotNullVar` (`Delegates.notNull`).
     *
     * Walks the class hierarchy because the `value` field is typically declared in a
     * superclass (e.g. `ObservableProperty`) while the runtime instance may be an
     * anonymous subclass created by factory methods like [kotlin.properties.Delegates.observable].
     *
     * @throws DelegateInspectionException if the delegate's value cannot be reflectively accessed
     */
    private fun extractViaValueField(delegate: Any): Any? {
        val delegateClass = delegate.javaClass
        val field = findValueFieldInHierarchy(delegateClass)
            ?: throw DelegateInspectionException(
                "Could not find 'value' field in delegate of type ${delegateClass.name}. " +
                    "Available fields: ${describeFieldsOf(delegateClass)}"
            )
        try {
            field.isAccessible = true
            return field.get(delegate)
        } catch (e: Exception) {
            throw DelegateInspectionException(
                "Could not read 'value' field (declared in ${field.declaringClass.name}) " +
                    "from delegate of type ${delegateClass.name}.",
                e
            )
        }
    }

    private fun findValueFieldInHierarchy(clazz: Class<*>): java.lang.reflect.Field? {
        var current: Class<*>? = clazz
        while (current != null && current != Any::class.java) {
            try {
                return current.getDeclaredField("value")
            } catch (_: NoSuchFieldException) {
                current = current.superclass
            }
        }
        return null
    }

    private fun describeFieldsOf(clazz: Class<*>): String =
        generateSequence(clazz) { it.superclass }
            .takeWhile { it != Any::class.java }
            .flatMap { c -> c.declaredFields.map { "${c.simpleName}.${it.name}: ${it.type.simpleName}" } }
            .joinToString(", ")
            .ifEmpty { "(none)" }
}


/**
 * Thrown when a Kotlin property delegate cannot be inspected, either because
 * it is not a recognised delegate type or because reflective access failed.
 */
internal class DelegateInspectionException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)
