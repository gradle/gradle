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

package org.gradle.internal.declarativedsl.schemaBuilder

import org.gradle.declarative.dsl.model.annotations.Adding
import org.gradle.declarative.dsl.model.annotations.HiddenInDefinition
import org.gradle.declarative.dsl.model.annotations.VisibleInDefinition
import org.gradle.internal.declarativedsl.schemaBuilder.TypeDiscovery.DiscoveredClass.DiscoveryTag
import org.gradle.internal.declarativedsl.schemaBuilder.TypeDiscovery.DiscoveredClass.DiscoveryTag.ContainerElement
import org.gradle.internal.declarativedsl.schemaBuilder.TypeDiscovery.DiscoveredClass.DiscoveryTag.PropertyType
import org.gradle.internal.declarativedsl.schemaBuilder.TypeDiscovery.DiscoveredClass.DiscoveryTag.Special
import org.gradle.internal.declarativedsl.schemaBuilder.TypeDiscovery.DiscoveredClass.DiscoveryTag.Supertype
import org.gradle.internal.declarativedsl.schemaBuilder.TypeDiscovery.DiscoveredClass.DiscoveryTag.UsedInMember
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.KVariance

/**
 * A container for returning a [Result] in schema building or reporting a [Failure] while carrying the context of the failure.
 *
 * A [Failure] result means that the schema building operation cannot be continued because of a failed requirement.
 * If it happens inside a complex operation, it should be propagated to the caller, normally by using [orFailWith].
 * (conveniently as: `.orFailWith { return it }`). The top-level usages can handle a failure by recording the
 * failed result of the invoked operation, to be reported at the end of the schema building process.
 *
 * In rare cases, when the [Failure] means an unsatisfied expectation of the schema builder implementation (rather than a user's mistake in the schema),
 * it can be handled by throwing an exception with [orError].
 *
 * @see ExtractionResult for the cases when both the positive and negative results need to carry some additional metadata
 */
sealed class SchemaResult<out T> {
    data class Result<out T : Any>(val result: T) : SchemaResult<T>()
    data class Failure(val issue: SchemaBuildingIssue, val context: List<SchemaBuildingContextElement>) : SchemaResult<Nothing>()
}

fun <T, R> SchemaResult<T>.flatMap(transform: (T) -> SchemaResult<R>): SchemaResult<R> = when (this) {
    is SchemaResult.Failure -> this
    is SchemaResult.Result -> transform(result)
}

fun <T, R : Any> SchemaResult<T>.map(transform: (T) -> R): SchemaResult<R> = flatMap { schemaResult(transform(it)) }

inline fun <T> SchemaResult<T>.orFailWith(onFailure: (SchemaResult.Failure) -> Nothing): T = when (this) {
    is SchemaResult.Result -> result
    is SchemaResult.Failure -> onFailure(this)
}

inline fun <T> Iterable<SchemaResult<T>>.getAllOrFailWith(onFailure: (List<SchemaResult.Failure>) -> Nothing): List<T> {
    val failures = filterIsInstance<SchemaResult.Failure>()
    if (failures.isEmpty()) {
        return map { (it as SchemaResult.Result).result }
    } else {
        onFailure(failures)
    }
}

@RequiresOptIn("This schema building operation breaks the process early or loses detailed failure information. Opt-in if you know what you're doing.")
annotation class LossySchemaBuildingOperation

/**
 * If the result is a [SchemaResult.Failure], throws an [IllegalStateException] reporting the [SchemaResult.Failure] and its context.
 *
 * Use only if the failure is a violation of an invariant. Otherwise, prefer propagating the failure to the caller, so the caller
 * can collect multiple failures.
 */
@LossySchemaBuildingOperation
fun <T> SchemaResult<T>.orError(): T = when (this) {
    is SchemaResult.Result -> result
    is SchemaResult.Failure -> throw IllegalStateException("Unexpected failure: $issue", DeclarativeDslSchemaBuildingException(SchemaFailureMessageFormatter.failureMessage(this)))
}

fun <T : Any> schemaResult(result: T) = SchemaResult.Result(result)


sealed interface SchemaBuildingIssue {
    data object DeclarationBothHiddenAndVisible : SchemaBuildingIssue
    data class IllegalUsageOfTypeParameterBoundByClass(val typeParameterUsage: KType) : SchemaBuildingIssue
    data class IllegalVarianceInParameterizedTypeUsage(val parameterizedClass: KClass<*>, val variance: KVariance) : SchemaBuildingIssue

    data class HiddenTypeUsedInDeclaration(
        val hiddenClass: KClass<*>,
        val hiddenBecause: Iterable<DiscoveryTag>,
        val illegalUsages: Iterable<DiscoveryTag>
    ) : SchemaBuildingIssue

    data class UnsupportedPairFactory(val returnType: SupportedTypeProjection.SupportedType) : SchemaBuildingIssue

    data class UnsupportedMapFactory(val returnType: SupportedTypeProjection.SupportedType) : SchemaBuildingIssue

    data class UnsupportedNullableType(val type: KType) : SchemaBuildingIssue
    data class UnsupportedVarargType(val type: KType) : SchemaBuildingIssue
    data class UnsupportedGenericContainerType(val type: KType) : SchemaBuildingIssue
    data class UnsupportedTypeParameterAsContainerType(val type: KType) : SchemaBuildingIssue

    data object UnsupportedNullableReadOnlyProperty : SchemaBuildingIssue
    data class NonClassifiableType(val kType: KType) : SchemaBuildingIssue

    data object UnitAddingFunctionWithLambda : SchemaBuildingIssue

    data object UnrecognizedMember : SchemaBuildingIssue
}

object SchemaFailureMessageFormatter {
    fun failureMessage(failure: SchemaResult.Failure): String =
        messageForIssue(failure.issue) + (
            failure.context.takeIf { it.isNotEmpty() }
                ?.let { "\n${contextRepresentation(it)}" }
                .orEmpty()
            )

    fun contextRepresentation(context: List<SchemaBuildingContextElement>): String =
        context.asReversed()
            .joinToString("\n") { "  in ${it.userRepresentation}" }

    fun messageForIssue(schemaBuildingIssue: SchemaBuildingIssue): String =
        with(schemaBuildingIssue) {
            when (this) {
                is SchemaBuildingIssue.HiddenTypeUsedInDeclaration ->
                    "Type '${hiddenClass.qualifiedName}' is a hidden type and cannot be directly used." +
                        "\n  Appears as hidden:\n" +
                        hiddenBecause.joinToString("\n") { "    - ${discoveryTagDescription(it, hiddenClass)}" } +
                        "\n  Illegal usages:\n" +
                        illegalUsages.joinToString("\n") { "    - ${discoveryTagDescription(it, hiddenClass)}" }

                is SchemaBuildingIssue.IllegalUsageOfTypeParameterBoundByClass -> "Type parameter '$typeParameterUsage' bound by a class cannot be used as a type"
                is SchemaBuildingIssue.IllegalVarianceInParameterizedTypeUsage -> "Illegal '${variance}' variance"
                SchemaBuildingIssue.DeclarationBothHiddenAndVisible ->
                    "Conflicting annotations: @${VisibleInDefinition::class.simpleName} and @${HiddenInDefinition::class.simpleName} are present"

                is SchemaBuildingIssue.NonClassifiableType -> "Illegal type '$kType': has no classifier"
                SchemaBuildingIssue.UnitAddingFunctionWithLambda -> "An @${Adding::class.simpleName} function with a Unit return type may not accept configuring lambdas"
                SchemaBuildingIssue.UnrecognizedMember -> "Member not recognized as part of schema"
                is SchemaBuildingIssue.UnsupportedTypeParameterAsContainerType -> "Using a type parameter as a configured type is not supported"
                is SchemaBuildingIssue.UnsupportedGenericContainerType -> "Using a parameterized type as a configured type is not supported"
                is SchemaBuildingIssue.UnsupportedPairFactory -> "Illegal type '${returnType.toKType()}': functions returning Pair types are not supported"
                is SchemaBuildingIssue.UnsupportedMapFactory -> "Illegal type '${returnType.toKType()}': functions returning Map types are not supported"
                SchemaBuildingIssue.UnsupportedNullableReadOnlyProperty -> "Unsupported property declaration: nullable read-only property"
                is SchemaBuildingIssue.UnsupportedNullableType -> "Unsupported usage of a nullable type"
                is SchemaBuildingIssue.UnsupportedVarargType -> "Unsupported vararg type $type"
            }
        }

    private fun discoveryTagDescription(tag: DiscoveryTag, violatingClass: KClass<*>): String = when (tag) {
        is ContainerElement -> "as the element of a container '${tag.containerMember}'"
        is PropertyType -> "as the property type of '${tag.kClass.qualifiedName}.${tag.propertyName}'"
        is Supertype -> if (tag.ofType == violatingClass && tag.isHidden) "type '${violatingClass.qualifiedName}' is annotated as hidden" else
            "in the supertypes of '${tag.ofType.qualifiedName}'"

        is UsedInMember -> "referenced from member '${tag.member}'"
        is Special -> tag.description
    }
}
