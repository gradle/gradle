/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.internal.serialize.graph.codecs

import org.gradle.internal.configuration.problems.DocumentationSection
import org.gradle.internal.configuration.problems.PropertyKind
import org.gradle.internal.configuration.problems.PropertyProblem
import org.gradle.internal.configuration.problems.PropertyTrace
import org.gradle.internal.configuration.problems.StructuredMessage
import org.gradle.internal.deprecation.DeprecationLogger
import org.gradle.internal.reflect.UnsupportedTypeException
import org.gradle.internal.serialize.graph.WriteContext
import org.gradle.internal.serialize.graph.withPropertyTrace
import java.lang.reflect.Field


/** See [isInsideSuppressedFailureTask]. */
private const val SUPPRESSED_FAILURE_TASK_NAME = "com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar"


/**
 * Returns the [WideningCodec] registered for [runtimeType] when its decoded
 * type cannot be assigned to [declaredType] — meaning a value of [runtimeType]
 * flowing into a slot typed [declaredType] cannot survive the configuration
 * cache roundtrip. Returns null in three cases:
 *
 * - no codec is registered for [runtimeType],
 * - the registered codec is not a [WideningCodec] (it roundtrips to its
 *   declared type, no widening occurs), or
 * - the slot's declared type can accept the codec's `decodedType` (the
 *   load-side value fits the slot).
 *
 * Centralises the codec-lookup core shared by every store-time roundtrip-type
 * check (bean fields, record components, lambda parameters, `Property<T>`
 * value types, Kotlin delegates). Callers layer their own carve-outs and
 * reporting on top of the returned codec.
 * <p>
 * Pass [runtimeType] explicitly when the value's runtime class is more specific
 * than the slot's declared type (the common case for bean fields). Omit it when
 * the slot's declared type IS the only type signal available (the lambda and
 * `Property<T>` cases) — it then defaults to [declaredType].
 */
fun WriteContext.findCodecThatWidensIncompatibly(
    declaredType: Class<*>,
    runtimeType: Class<*> = declaredType
): WideningCodec<*>? {
    val widening = codecForRuntimeType(runtimeType) as? WideningCodec<*> ?: return null
    if (declaredType.isAssignableFrom(widening.decodedType)) return null
    return widening
}


/**
 * Reports a serialization failure as a non-fatal configuration cache problem
 * at the [current trace][WriteContext.trace].
 * <p>
 * This does not interrupt encoding. The caller is expected to write a
 * self-consistent placeholder in place of the offending value. The
 * [exception] is attached to the resulting
 * [org.gradle.internal.configuration.problems.PropertyProblem], so it
 * surfaces as a cause of `ConfigurationCacheProblemsException` when the
 * build later fails due to deferred problems, and its
 * [resolutions][org.gradle.internal.exceptions.ResolutionProvider]
 * remain visible to `failure.assertHasResolution(...)`.
 * <p>
 * Callers who need a new trace frame (e.g., bean-field encoders that have
 * not yet entered the field's property trace) should wrap with
 * [withPropertyTrace] themselves; this keeps the trace surface explicit at
 * the call site instead of conflating it with the problem-reporting helper.
 */
fun WriteContext.reportSerializationProblem(exception: UnsupportedTypeException) {
    val message = StructuredMessage.build {
        text("failed to serialize value of ")
        trace.sequence.forEach { it.describe(this) }
        text(exception.detailsForProblem)
    }
    onProblem(
        PropertyProblem(
            trace = trace,
            message = message,
            exception = exception,
            documentationSection = DocumentationSection.RequirementsDisallowedTypes
        )
    )
}


/**
 * Reports a deferred problem when a non-null bean field value would be encoded
 * by a [WideningCodec] whose `decodedType` cannot be assigned back to the
 * field's declared type. Wraps the report in a [withPropertyTrace] frame so
 * the problem is attributed to the field rather than the enclosing bean.
 *
 * @return `true` when the field's value must be dropped from the cache; the
 *         caller is expected to write `null` in its place.
 */
fun WriteContext.reportIfIncompatibleRoundtrip(field: Field, fieldName: String, fieldValue: Any?): Boolean {
    if (fieldValue == null) return false
    val widening = findCodecThatWidensIncompatibly(field.type, fieldValue.javaClass) ?: return false
    // The helper has already rejected the case where field.type is a supertype of
    // decodedType (load-side value fits the field). This branch handles the OPPOSITE
    // subtype relation: field.type is a subtype of decodedType — the codec may produce
    // a concrete instance of that subtype at runtime (codecs declare a broad interface
    // but generally construct via a factory that yields the expected concrete class).
    // Only flag when the types share no subtyping relation at all — then reassignment
    // is definitely impossible.
    if (widening.decodedType.isAssignableFrom(field.type)) return false
    val exception = UnsupportedTypeException(
        "Cannot serialize value of type ${fieldValue.javaClass.name} into field ${field.name} of ${field.declaringClass.name} in ${trace.taskDescription()}.",
        "Values of this type are restored from the configuration cache as ${widening.publicDecodedType.name}, which cannot be assigned to a field of type ${field.type.name}.",
        listOf(widening.wideningFix)
    )
    withPropertyTrace(PropertyKind.Field, fieldName) {
        reportSerializationProblem(exception)
    }
    return true
}


/**
 * Reports a deferred problem when the codec registered for [valueType] is a
 * [WideningCodec] that produces a decoded type incompatible with [valueType].
 * <p>
 * Used by `Property<T>` / `ListProperty<T>` / `SetProperty<T>` / `MapProperty<K, V>`
 * codecs to verify the type argument(s) can survive the configuration cache
 * roundtrip. [propertyKind] only contributes display text (`propertyKind.simpleName`);
 * the helper does not inspect its identity, so callers stay decoupled from the
 * specific property interfaces.
 * <p>
 * [resolutionFor] supplies the user-facing fix line attached to the reported
 * exception. The default returns the codec's own [WideningCodec.wideningFix];
 * `MapProperty` callers override to emit a key/value-aware fix line that names
 * the offending [valueType]. The lambda receives both the matched [WideningCodec]
 * and the original [valueType] so callers can compose either piece into the fix.
 *
 * @return `true` when the property's value must be dropped from the cache (the
 *         caller should write a missing-value placeholder so the property
 *         survives the roundtrip as if it were never set).
 */
fun WriteContext.reportIfUnsupportedPropertyValueType(
    propertyKind: Class<*>,
    valueType: Class<*>,
    resolutionFor: (widening: WideningCodec<*>, valueType: Class<*>) -> String = { widening, _ -> widening.wideningFix }
): Boolean {
    val widening = findCodecThatWidensIncompatibly(valueType) ?: return false
    val resolutions = listOf(resolutionFor(widening, valueType))
    if (isInsideSuppressedFailureTask()) {
        nagAboutSuppressedWidening(widening, resolutions, propertyKind, valueType)
        return false
    }
    failOnIncompatibleWidening(widening, resolutions, propertyKind, valueType)
    return true
}


/**
 * `true` when the current property trace is inside a task of the
 * [SUPPRESSED_FAILURE_TASK_NAME] type (the ShadowJar task from
 * [GradleUp/shadow](https://github.com/GradleUp/shadow)).
 *
 * ShadowJar declares `SetProperty<Configuration> configurations` but only calls
 * `.resolve(...)` on the resolved set, so widening to a `FileCollection` on
 * roundtrip is functionally equivalent for the plugin's own use. For this one
 * task type the widening report is downgraded to a deprecation nag so affected
 * builds stay green while ShadowJar's authors migrate the field type; the nag
 * will become a hard error in Gradle 10.
 *
 * This is a pragmatic escape hatch, not a design pattern. Additional entries
 * should be strongly resisted — this is a single-purpose exception, not the
 * seed of a general allow-list.
 */
private fun WriteContext.isInsideSuppressedFailureTask(): Boolean =
    trace.sequence
        .filterIsInstance<PropertyTrace.Task>()
        .any { it.type.name == SUPPRESSED_FAILURE_TASK_NAME }


private fun WriteContext.failOnIncompatibleWidening(widening: WideningCodec<*>, resolutions: List<String>, propertyKind: Class<*>, valueType: Class<*>) {
    val exception = UnsupportedTypeException(
        "Cannot serialize ${propertyKind.simpleName}<${valueType.simpleName}> in ${trace.taskDescription()}.",
        "The value type of this property (${valueType.name}) is not supported with the configuration cache: values of this type are restored from the configuration cache as ${widening.publicDecodedType.name}.",
        resolutions
    )
    reportSerializationProblem(exception)
}


private fun WriteContext.nagAboutSuppressedWidening(widening: WideningCodec<*>, resolutions: List<String>, propertyKind: Class<*>, valueType: Class<*>) {
    var deprecation = DeprecationLogger
        .deprecateAction("Serializing ${propertyKind.simpleName}<${valueType.simpleName}> in ${trace.taskDescription()}")
        .withContext("The value type of this property (${valueType.name}) is not supported with the configuration cache: values of this type are restored from the configuration cache as ${widening.publicDecodedType.name}.")
    resolutions.forEach { deprecation = deprecation.withAdvice(it) }
    deprecation
        .willBecomeAnErrorInGradle10()
        .withUserManual("configuration_cache_requirements", "config_cache:requirements:disallowed_types")
        .nagUser()
}
