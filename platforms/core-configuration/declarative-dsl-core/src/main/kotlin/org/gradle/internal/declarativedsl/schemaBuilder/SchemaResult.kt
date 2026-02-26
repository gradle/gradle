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

import org.gradle.declarative.dsl.evaluation.SchemaBuildingFailure
import org.gradle.declarative.dsl.evaluation.SchemaIssue
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
    data class Result<out T>(val result: T) : SchemaResult<T>()
    data class Failure(val issue: SchemaIssue, val contextElements: List<SchemaBuildingContextElement>) : SchemaResult<Nothing>()
}

inline fun <T, R> SchemaResult<T>.flatMap(transform: (T) -> SchemaResult<R>): SchemaResult<R> = when (this) {
    is SchemaResult.Failure -> this
    is SchemaResult.Result -> transform(result)
}

inline fun <T, R> SchemaResult<T>.map(transform: (T) -> R): SchemaResult<R> = flatMap { schemaResult(transform(it)) }

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
    is SchemaResult.Failure -> throw IllegalStateException("Unexpected failure: $issue", DeclarativeDslSchemaBuildingException(SchemaFailureMessageFormatter.failureMessage(asReportableFailure())))
}

fun <T> schemaResult(result: T) = SchemaResult.Result(result)


sealed interface SchemaBuildingIssue {
    data object DeclarationBothHiddenAndVisible : SchemaBuildingIssue, SchemaIssue.DeclarationBothHiddenAndVisible {
        private fun readResolve(): Any = DeclarationBothHiddenAndVisible
    }

    data class IllegalUsageOfTypeParameterBoundByClass(override val typeParameterName: String) : SchemaBuildingIssue, SchemaIssue.IllegalUsageOfTypeParameterBoundByClass {
        constructor(typeParameterUsage: KType?) : this(typeParameterUsage?.toString() ?: "unknown")
    }

    data class IllegalVarianceInParameterizedTypeUsage(override val varianceKind: String) : SchemaBuildingIssue, SchemaIssue.IllegalVarianceInParameterizedTypeUsage {
        constructor(variance: KVariance?) : this(variance?.name ?: "unknown")
    }

    data class HiddenTypeUsedInDeclaration(
        override val hiddenClassName: String,
        override val hiddenBecause: Iterable<String>,
        override val illegalUsages: Iterable<String>
    ) : SchemaBuildingIssue, SchemaIssue.HiddenTypeUsedInDeclaration {
        constructor(
            hiddenClass: KClass<*>,
            hiddenBecauseTags: Iterable<DiscoveryTag>,
            illegalUsageTags: Iterable<DiscoveryTag>
        ) : this(
            hiddenClass.let { it.qualifiedName ?: it.simpleName } ?: "unknown",
            hiddenBecauseTags.map { discoveryTagDescription(it, hiddenClass) },
            illegalUsageTags.map { discoveryTagDescription(it, hiddenClass) }
        )
    }

    data class UnsupportedPairFactory(override val returnTypeName: String) : SchemaBuildingIssue, SchemaIssue.UnsupportedPairFactory {
        constructor(returnType: SupportedTypeProjection.SupportedType?) : this(returnType?.toKType()?.toString() ?: "unknown")
    }

    data class UnsupportedMapFactory(override val returnTypeName: String) : SchemaBuildingIssue, SchemaIssue.UnsupportedMapFactory {
        constructor(returnType: SupportedTypeProjection.SupportedType?) : this(returnType?.toKType()?.toString() ?: "unknown")
    }

    data class UnsupportedNullableType(override val typeName: String) : SchemaBuildingIssue, SchemaIssue.UnsupportedNullableType {
        constructor(type: KType?) : this(type?.toString() ?: "unknown")
    }

    data class UnsupportedVarargType(override val typeName: String) : SchemaBuildingIssue, SchemaIssue.UnsupportedVarargType {
        constructor(type: KType?) : this(type?.toString() ?: "unknown")
    }

    data class UnsupportedGenericContainerType(override val typeName: String) : SchemaBuildingIssue, SchemaIssue.UnsupportedGenericContainerType {
        constructor(type: KType?) : this(type?.toString() ?: "unknown")
    }

    data class UnsupportedTypeParameterAsContainerType(override val typeName: String) : SchemaBuildingIssue, SchemaIssue.UnsupportedTypeParameterAsContainerType {
        constructor(type: KType?) : this(type?.toString() ?: "unknown")
    }

    class UnsupportedNullableReadOnlyProperty : SchemaBuildingIssue, SchemaIssue.UnsupportedNullableReadOnlyProperty
    data class NonClassifiableType(override val typeName: String) : SchemaBuildingIssue, SchemaIssue.NonClassifiableType {
        constructor(kType: KType?) : this(kType?.toString() ?: "unknown")
    }

    class UnitAddingFunctionWithLambda : SchemaBuildingIssue, SchemaIssue.UnitAddingFunctionWithLambda

    class UnrecognizedMember : SchemaBuildingIssue, SchemaIssue.UnrecognizedMember
}

object SchemaFailureMessageFormatter {
    fun failureMessage(failure: SchemaBuildingFailure): String =
        messageForIssue(failure.issue) + (
            failure.context.takeIf { it.isNotEmpty() }
                ?.let { "\n${contextRepresentation(it)}" }
                .orEmpty()
            )

    fun contextRepresentation(context: List<SchemaBuildingFailure.FailureContext>): String =
        context.asReversed()
            .joinToString("\n") { "  in ${it.userRepresentation}" }

    fun messageForIssue(schemaIssue: SchemaIssue): String =
        when (schemaIssue) {
            is SchemaIssue.HiddenTypeUsedInDeclaration ->
                "Type '${schemaIssue.hiddenClassName}' is a hidden type and cannot be directly used." +
                    "\n  Appears as hidden:\n" +
                    schemaIssue.hiddenBecause.joinToString("\n") { "    - $it" } +
                    "\n  Illegal usages:\n" +
                    schemaIssue.illegalUsages.joinToString("\n") { "    - $it" }

            is SchemaIssue.IllegalUsageOfTypeParameterBoundByClass -> "Type parameter '${schemaIssue.typeParameterName}' bound by a class cannot be used as a type"
            is SchemaIssue.IllegalVarianceInParameterizedTypeUsage -> "Illegal '${schemaIssue.varianceKind}' variance"
            is SchemaIssue.DeclarationBothHiddenAndVisible ->
                "Conflicting annotations: @${VisibleInDefinition::class.simpleName} and @${HiddenInDefinition::class.simpleName} are present"

            is SchemaIssue.NonClassifiableType -> "Illegal type '${schemaIssue.typeName}': has no classifier"
            is SchemaIssue.UnitAddingFunctionWithLambda -> "An @${Adding::class.simpleName} function with a Unit return type may not accept configuring lambdas"
            is SchemaIssue.UnrecognizedMember -> "Member not recognized as part of schema"
            is SchemaIssue.UnsupportedTypeParameterAsContainerType -> "Using a type parameter as a configured type is not supported"
            is SchemaIssue.UnsupportedGenericContainerType -> "Using a parameterized type as a configured type is not supported"
            is SchemaIssue.UnsupportedPairFactory -> "Illegal type '${schemaIssue.returnTypeName}': functions returning Pair types are not supported"
            is SchemaIssue.UnsupportedMapFactory -> "Illegal type '${schemaIssue.returnTypeName}': functions returning Map types are not supported"
            is SchemaIssue.UnsupportedNullableReadOnlyProperty -> "Unsupported property declaration: nullable read-only property"
            is SchemaIssue.UnsupportedNullableType -> "Unsupported usage of a nullable type"
            is SchemaIssue.UnsupportedVarargType -> "Unsupported vararg type ${schemaIssue.typeName}"
            else -> "Schema issue: $schemaIssue"
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
