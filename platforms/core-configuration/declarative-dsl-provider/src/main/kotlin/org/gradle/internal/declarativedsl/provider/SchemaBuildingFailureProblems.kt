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

package org.gradle.internal.declarativedsl.provider

import org.gradle.api.problems.Problem
import org.gradle.api.problems.ProblemGroup
import org.gradle.api.problems.ProblemId
import org.gradle.api.problems.ProblemId.create
import org.gradle.api.problems.ProblemSpec
import org.gradle.api.problems.Severity
import org.gradle.api.problems.internal.GradleCoreProblemGroup.scripts
import org.gradle.api.problems.internal.InternalProblems
import org.gradle.declarative.dsl.evaluation.SchemaBuildingFailure
import org.gradle.declarative.dsl.evaluation.SchemaIssue
import org.gradle.declarative.dsl.model.annotations.HiddenInDefinition
import org.gradle.internal.declarativedsl.evaluator.runner.EvaluationResult.NotEvaluated
import org.gradle.internal.declarativedsl.schemaBuilder.SchemaFailureMessageFormatter

internal fun schemaBuildingFailuresAsProblems(
    stageFailure: NotEvaluated.StageFailure.SchemaBuildingFailures,
    problems: InternalProblems
): List<Problem> = stageFailure.failures.map { failure ->
    problems.reporter.create(schemaBuildingFailureProblemId(failure)) { problem ->
        problem.severity(Severity.ERROR)
        problem.details(SchemaFailureMessageFormatter.failureMessage(failure))
        problem.solutionFor(failure)
    }
}

private object ProblemIds {
    val group = ProblemGroup.create("dcl-schema", "DCL Schema issues", scripts())

    val SCHEMA_BUILDING_FAILURE = create("dcl-schema-building-failure", "Schema building failure", group)
    val SCHEMA_DECLARATION_BOTH_VISIBLE_AND_HIDDEN = create("declaration-visible-and-hidden", "Declaration is both visible and hidden", group)
    val SCHEMA_HIDDEN_DECLARATION_USED_IN_DEFINITION = create("declaration-hidden-type-used-in-definition", "A hidden declaration is used in definition", group)
    val SCHEMA_ILLEGAL_USAGE_OF_TYPE_PARAMETER_BOUND_BY_CLASS =
        create("illegal-usage-of-type-parameter-bound-by-class", "Illegal usage of type parameter bound by class", group)
    val SCHEMA_ILLEGAL_VARIANCE_IN_PARAMETERIZED_TYPE_USAGE =
        create("illegal-variance-in-parameterized-type-usage", "Illegal variance in parameterized type usage", group)
    val SCHEMA_NON_CLASSIFIABLE_TYPE = create("non-classifiable-type", "Non-classifiable type", group)
    val SCHEMA_UNIT_ADDING_FUNCTION_WITH_LAMBDA = create("unit-adding-function-with-lambda", "Unit-adding function with lambda", group)
    val SCHEMA_UNRECOGNIZED_MEMBER = create("unrecognized-member", "Unrecognized member", group)
    val SCHEMA_UNSUPPORTED_GENERIC_CONTAINER_TYPE = create("unsupported-generic-container-type", "Unsupported generic container type", group)
    val SCHEMA_UNSUPPORTED_MAP_FACTORY = create("unsupported-map-factory", "Unsupported map factory", group)
    val SCHEMA_UNSUPPORTED_NULLABLE_READ_ONLY_PROPERTY = create("unsupported-nullable-read-only-property", "Unsupported nullable read-only property", group)
    val SCHEMA_UNSUPPORTED_NULLABLE_TYPE = create("unsupported-nullable-type", "Unsupported nullable type", group)
    val SCHEMA_UNSUPPORTED_PAIR_FACTORY = create("unsupported-pair-factory", "Unsupported pair factory", group)
    val SCHEMA_UNSUPPORTED_TYPE_PARAMETER_AS_CONTAINER_TYPE =
        create("unsupported-type-parameter-as-container-type", "Unsupported type parameter as container type", group)
    val SCHEMA_UNSUPPORTED_VARARG_TYPE = create("unsupported-vararg-type", "Unsupported vararg type", group)
}

internal fun schemaBuildingFailureProblemId(failure: SchemaBuildingFailure): ProblemId = when (failure.issue) {
    is SchemaIssue.DeclarationBothHiddenAndVisible -> ProblemIds.SCHEMA_DECLARATION_BOTH_VISIBLE_AND_HIDDEN
    is SchemaIssue.HiddenTypeUsedInDeclaration -> ProblemIds.SCHEMA_HIDDEN_DECLARATION_USED_IN_DEFINITION
    is SchemaIssue.IllegalUsageOfTypeParameterBoundByClass -> ProblemIds.SCHEMA_ILLEGAL_USAGE_OF_TYPE_PARAMETER_BOUND_BY_CLASS
    is SchemaIssue.IllegalVarianceInParameterizedTypeUsage -> ProblemIds.SCHEMA_ILLEGAL_VARIANCE_IN_PARAMETERIZED_TYPE_USAGE
    is SchemaIssue.NonClassifiableType -> ProblemIds.SCHEMA_NON_CLASSIFIABLE_TYPE
    is SchemaIssue.UnitAddingFunctionWithLambda -> ProblemIds.SCHEMA_UNIT_ADDING_FUNCTION_WITH_LAMBDA
    is SchemaIssue.UnrecognizedMember -> ProblemIds.SCHEMA_UNRECOGNIZED_MEMBER
    is SchemaIssue.UnsupportedGenericContainerType -> ProblemIds.SCHEMA_UNSUPPORTED_GENERIC_CONTAINER_TYPE
    is SchemaIssue.UnsupportedMapFactory -> ProblemIds.SCHEMA_UNSUPPORTED_MAP_FACTORY
    is SchemaIssue.UnsupportedNullableReadOnlyProperty -> ProblemIds.SCHEMA_UNSUPPORTED_NULLABLE_READ_ONLY_PROPERTY
    is SchemaIssue.UnsupportedNullableType -> ProblemIds.SCHEMA_UNSUPPORTED_NULLABLE_TYPE
    is SchemaIssue.UnsupportedPairFactory -> ProblemIds.SCHEMA_UNSUPPORTED_PAIR_FACTORY
    is SchemaIssue.UnsupportedTypeParameterAsContainerType -> ProblemIds.SCHEMA_UNSUPPORTED_TYPE_PARAMETER_AS_CONTAINER_TYPE
    is SchemaIssue.UnsupportedVarargType -> ProblemIds.SCHEMA_UNSUPPORTED_VARARG_TYPE
    else -> ProblemIds.SCHEMA_BUILDING_FAILURE
}

internal fun ProblemSpec.solutionFor(failure: SchemaBuildingFailure) {
    when (failure.issue) {
        is SchemaIssue.DeclarationBothHiddenAndVisible -> {
            solution("Make the declaration either visible or hidden.")
        }

        is SchemaIssue.IllegalVarianceInParameterizedTypeUsage -> {
            solution("Use invariant type arguments (with no wildcards or in/out-projections)")
        }

        is SchemaIssue.UnitAddingFunctionWithLambda -> {
            solution("Make the function return the configured value instead of Unit/void.")
            solution("Remove the functional (lambda) parameter.")
        }

        is SchemaIssue.UnrecognizedMember -> {
            solution("Adjust the member signature to follow the Declarative definition conventions.")
        }

        is SchemaIssue.UnsupportedGenericContainerType -> {
            solution("Create a non-generic subtype of the generic type, providing concrete type arguments for the supertype.")
        }

        is SchemaIssue.UnsupportedMapFactory -> {
            solution("If regular Map values (mapOf) don't work for this use case, use a custom type instead of Map.")
        }

        is SchemaIssue.UnsupportedNullableReadOnlyProperty -> {
            solution("Make the property non-nullable.")
            solution("Make the property writable (var), or add a setter in Java.")
        }

        is SchemaIssue.UnsupportedNullableType -> {
            solution("Use a non-nullable type instead of nullable type.")
        }

        is SchemaIssue.UnsupportedPairFactory -> {
            solution("Use a custom type with a custom value factory instead of Pair.")
        }

        is SchemaIssue.UnsupportedTypeParameterAsContainerType -> {
            solution(
                "Use a concrete type as the container (receiver) type. " +
                    "If needed, define a subtype of the owner of this member with concrete types used as type arguments for the supertype's parameters."
            )
        }

        is SchemaIssue.UnsupportedVarargType -> {
            solution("Use a supported vararg type.")
            solution("Use a list instead of vararg.")
        }

        is SchemaIssue.NonClassifiableType,
        is SchemaIssue.HiddenTypeUsedInDeclaration,
        is SchemaIssue.IllegalUsageOfTypeParameterBoundByClass -> Unit // no specific solutions
    }

    solution("Remove the violating declaration or make it non-public.")
    solution("If the definition is unsafe, annotate the violating declaration as @${HiddenInDefinition::class.simpleName} to exclude it from the Declarative schema.")
}
