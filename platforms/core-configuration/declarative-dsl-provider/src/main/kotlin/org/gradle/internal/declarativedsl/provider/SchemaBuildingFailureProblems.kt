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
import org.gradle.api.problems.ProblemSpec
import org.gradle.api.problems.internal.ProblemsInternal
import org.gradle.declarative.dsl.evaluation.SchemaBuildingFailure
import org.gradle.declarative.dsl.evaluation.SchemaIssue
import org.gradle.declarative.dsl.model.annotations.HiddenInDefinition
import org.gradle.declarative.dsl.schema.UnsafeBecauseHasHiddenMembers
import org.gradle.declarative.dsl.schema.UnsafeBecauseHasNonPublicMembers
import org.gradle.declarative.dsl.schema.UnsafeNonPureFunction
import org.gradle.declarative.dsl.schema.UnsafeInjectProperty
import org.gradle.declarative.dsl.schema.UnsafeNonAbstractMember
import org.gradle.declarative.dsl.schema.UnsafeNonInterfaceType
import org.gradle.declarative.dsl.schema.UnsafeJavaBeanProperty
import org.gradle.internal.declarativedsl.evaluator.runner.EvaluationResult.NotEvaluated
import org.gradle.internal.declarativedsl.schemaBuilder.SchemaFailureMessageFormatter

internal fun schemaBuildingFailuresAsProblems(
    stageFailure: NotEvaluated.StageFailure.SchemaBuildingFailures,
    problems: ProblemsInternal
): List<Problem> {
    val group = problems.groups.gradle.buildLogic
    return stageFailure.failures.map { failure ->
        problems.reporter.create(group.problemId(schemaBuildingFailureProblemName(failure))) { problem ->
            problem.details(SchemaFailureMessageFormatter.failureMessage(failure))
            problem.solutionFor(failure)
        }
    }
}

/**
 * The names of the schema building problems, reported into the `Gradle > Build Logic` group.
 */
private object ProblemNames {
    const val SCHEMA_BUILDING_FAILURE = "Schema building failure"
    const val SCHEMA_DECLARATION_BOTH_VISIBLE_AND_HIDDEN = "Declaration is both visible and hidden"
    const val SCHEMA_HIDDEN_DECLARATION_USED_IN_DEFINITION = "A hidden declaration is used in definition"
    const val SCHEMA_ILLEGAL_USAGE_OF_TYPE_PARAMETER_BOUND_BY_CLASS = "Illegal usage of type parameter bound by class"
    const val SCHEMA_ILLEGAL_VARIANCE_IN_PARAMETERIZED_TYPE_USAGE = "Illegal variance in parameterized type usage"
    const val SCHEMA_NON_CLASSIFIABLE_TYPE = "Non-classifiable type"
    const val SCHEMA_UNIT_ADDING_FUNCTION_WITH_LAMBDA = "Unit-adding function with lambda"
    const val SCHEMA_UNRECOGNIZED_MEMBER = "Unrecognized member"
    const val SCHEMA_UNSUPPORTED_GENERIC_CONTAINER_TYPE = "Unsupported generic container type"
    const val SCHEMA_UNSUPPORTED_MAP_FACTORY = "Unsupported map factory"
    const val SCHEMA_UNSUPPORTED_NULLABLE_READ_ONLY_PROPERTY = "Unsupported nullable read-only property"
    const val SCHEMA_UNSUPPORTED_NULLABLE_TYPE = "Unsupported nullable type"
    const val SCHEMA_UNSUPPORTED_PAIR_FACTORY = "Unsupported pair factory"
    const val SCHEMA_UNSUPPORTED_TYPE_PARAMETER_AS_CONTAINER_TYPE = "Unsupported type parameter as container type"
    const val SCHEMA_UNSUPPORTED_VARARG_TYPE = "Unsupported vararg type"
    const val SCHEMA_UNSAFE_NON_INTERFACE_TYPE = "Unsafe non-interface type in safe feature API"
    const val SCHEMA_UNSAFE_NON_ABSTRACT_MEMBER = "Unsafe non-abstract member in safe feature API"
    const val SCHEMA_UNSAFE_INJECT_PROPERTY = "Unsafe injected service property in safe feature API"
    const val SCHEMA_UNSAFE_JAVA_BEAN_PROPERTY = "Unsafe Java bean property in safe feature API"
    const val SCHEMA_UNSAFE_NON_PURE_FUNCTION = "Unsafe non-pure function in safe feature API"
    const val SCHEMA_UNSAFE_BECAUSE_HAS_HIDDEN_MEMBERS = "Unsafe hidden members in safe feature API"
    const val SCHEMA_UNSAFE_BECAUSE_HAS_NON_PUBLIC_MEMBERS = "Non-public members in safe feature API"
}

@Suppress("CyclomaticComplexMethod")
internal fun schemaBuildingFailureProblemName(failure: SchemaBuildingFailure): String = when (failure.issue) {
    is SchemaIssue.DeclarationBothHiddenAndVisible -> ProblemNames.SCHEMA_DECLARATION_BOTH_VISIBLE_AND_HIDDEN
    is SchemaIssue.HiddenTypeUsedInDeclaration -> ProblemNames.SCHEMA_HIDDEN_DECLARATION_USED_IN_DEFINITION
    is SchemaIssue.IllegalUsageOfTypeParameterBoundByClass -> ProblemNames.SCHEMA_ILLEGAL_USAGE_OF_TYPE_PARAMETER_BOUND_BY_CLASS
    is SchemaIssue.IllegalVarianceInParameterizedTypeUsage -> ProblemNames.SCHEMA_ILLEGAL_VARIANCE_IN_PARAMETERIZED_TYPE_USAGE
    is SchemaIssue.NonClassifiableType -> ProblemNames.SCHEMA_NON_CLASSIFIABLE_TYPE
    is SchemaIssue.UnitAddingFunctionWithLambda -> ProblemNames.SCHEMA_UNIT_ADDING_FUNCTION_WITH_LAMBDA
    is SchemaIssue.UnrecognizedMember -> ProblemNames.SCHEMA_UNRECOGNIZED_MEMBER
    is SchemaIssue.UnsupportedGenericContainerType -> ProblemNames.SCHEMA_UNSUPPORTED_GENERIC_CONTAINER_TYPE
    is SchemaIssue.UnsupportedMapFactory -> ProblemNames.SCHEMA_UNSUPPORTED_MAP_FACTORY
    is SchemaIssue.UnsupportedNullableReadOnlyProperty -> ProblemNames.SCHEMA_UNSUPPORTED_NULLABLE_READ_ONLY_PROPERTY
    is SchemaIssue.UnsupportedNullableType -> ProblemNames.SCHEMA_UNSUPPORTED_NULLABLE_TYPE
    is SchemaIssue.UnsupportedPairFactory -> ProblemNames.SCHEMA_UNSUPPORTED_PAIR_FACTORY
    is SchemaIssue.UnsupportedTypeParameterAsContainerType -> ProblemNames.SCHEMA_UNSUPPORTED_TYPE_PARAMETER_AS_CONTAINER_TYPE
    is SchemaIssue.UnsupportedVarargType -> ProblemNames.SCHEMA_UNSUPPORTED_VARARG_TYPE
    is SchemaIssue.UnsafeDeclarationInSafeFeatureApi -> when ((failure.issue as SchemaIssue.UnsafeDeclarationInSafeFeatureApi).unsafeApiCause) {
        is UnsafeNonInterfaceType -> ProblemNames.SCHEMA_UNSAFE_NON_INTERFACE_TYPE
        is UnsafeNonAbstractMember -> ProblemNames.SCHEMA_UNSAFE_NON_ABSTRACT_MEMBER
        is UnsafeInjectProperty -> ProblemNames.SCHEMA_UNSAFE_INJECT_PROPERTY
        is UnsafeJavaBeanProperty -> ProblemNames.SCHEMA_UNSAFE_JAVA_BEAN_PROPERTY
        is UnsafeNonPureFunction -> ProblemNames.SCHEMA_UNSAFE_NON_PURE_FUNCTION
        is UnsafeBecauseHasHiddenMembers -> ProblemNames.SCHEMA_UNSAFE_BECAUSE_HAS_HIDDEN_MEMBERS
        is UnsafeBecauseHasNonPublicMembers -> ProblemNames.SCHEMA_UNSAFE_BECAUSE_HAS_NON_PUBLIC_MEMBERS
        else -> ProblemNames.SCHEMA_BUILDING_FAILURE
    }
    else -> ProblemNames.SCHEMA_BUILDING_FAILURE
}

internal fun ProblemSpec.solutionFor(failure: SchemaBuildingFailure) {
    when (val issue = failure.issue) {
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

        is SchemaIssue.UnsafeDeclarationInSafeFeatureApi -> {
            when (issue.unsafeApiCause) {
                is UnsafeBecauseHasHiddenMembers -> solution("Remove the hidden members.")
                is UnsafeBecauseHasNonPublicMembers -> {
                    solution("Remove the non-public members from the safe definition.")
                    solution("Make the members public.")
                }
                is UnsafeNonPureFunction -> {
                    solution("Use read-only properties to expose nested models.")
                    solution("Use NamedDomainObjectContainer or collection properties to model multi-element containers.")
                }
                is UnsafeNonAbstractMember -> solution("Make the member safe by removing the implementation (making it abstract).")
                is UnsafeInjectProperty -> solution("Remove the @Inject annotation.")
                is UnsafeNonInterfaceType -> solution("Make the type safe by making it an interface.")
                is UnsafeJavaBeanProperty -> solution("Make the property safe by using Gradle Property API.")
                else -> Unit
            }
            solution("Declare the corresponding features as having unsafe definitions.")
        }

        is SchemaIssue.NonClassifiableType,
        is SchemaIssue.HiddenTypeUsedInDeclaration,
        is SchemaIssue.IllegalUsageOfTypeParameterBoundByClass -> Unit // no specific solutions

    }

    solution("Remove the violating declaration or make it non-public in an unsafe definition.")
    solution("In an unsafe definition, annotate the violating declaration as @${HiddenInDefinition::class.simpleName} to exclude it from the Declarative schema.")
}
