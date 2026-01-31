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

package org.gradle.internal.declarativedsl.schemaBuilder

import org.gradle.declarative.dsl.model.annotations.HiddenInDefinition
import org.gradle.declarative.dsl.model.annotations.VisibleInDefinition
import org.gradle.declarative.dsl.model.annotations.internal.DeclarativeWithHiddenMembers
import org.gradle.internal.declarativedsl.schemaBuilder.MaybeDeclarativeClassInHierarchy.SuperclassWithMapping
import org.gradle.internal.declarativedsl.schemaBuilder.MaybeDeclarativeClassInHierarchy.VisibleSuperclassInHierarchy
import org.gradle.internal.declarativedsl.schemaBuilder.SupportedTypeProjection.SupportedType
import java.lang.reflect.Modifier
import kotlin.reflect.KCallable
import kotlin.reflect.KClass
import kotlin.reflect.KClassifier
import kotlin.reflect.KFunction
import kotlin.reflect.KMutableProperty
import kotlin.reflect.KProperty
import kotlin.reflect.KType
import kotlin.reflect.KTypeParameter
import kotlin.reflect.KTypeProjection
import kotlin.reflect.KVariance
import kotlin.reflect.KVisibility
import kotlin.reflect.full.createType
import kotlin.reflect.full.declaredMembers
import kotlin.reflect.full.instanceParameter
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.jvm.javaField

data class ClassMembersForSchema(
    val membersBySupertype: Map<KClass<*>, List<ExtractionResult<SupportedCallable, KCallable<*>>>>,
) {
    val declarativeMembers: Iterable<SupportedCallable> by lazy {
        membersBySupertype.values.flatten()
            .filterIsInstance<ExtractionResult.Extracted<SupportedCallable, KCallable<*>>>().map { it.result }
    }
}

typealias SupportedCallableResult = ExtractionResult<SupportedCallable, KCallable<*>>

internal fun collectMembersForSchema(host: SchemaBuildingHost, kClass: KClass<*>): ClassMembersForSchema {
    // Members in enum types are not supported
    if (kClass.isSubclassOf(Enum::class)) {
        return ClassMembersForSchema(emptyMap())
    }

    val supertypesWithMapping = host.declarativeSupertypesHierarchy(kClass)

    val results: MutableMap<KClass<*>, List<SupportedCallableResult>> = mutableMapOf()

    supertypesWithMapping.forEach { supertype ->
        if (!isValidMemberHolderType(supertype.superClass)) {
            return@forEach
        }

        host.inContextOfModelClass(supertype.superClass) {
            when (supertype) {
                is SuperclassWithMapping -> {
                    results[supertype.superClass] = supertype.superClass.declaredMembers.mapNotNull { member ->
                        maybeSupportedCallable(host, member, supertype)
                    }
                }

                is MaybeDeclarativeClassInHierarchy.NonDeclarativeSuperclassInHierarchy -> Unit
            }
        }
    }

    return ClassMembersForSchema(mergeMembersBySignature(results))
}

private fun isValidMemberHolderType(kClass: KClass<*>): Boolean = when {
    kClass.qualifiedName?.startsWith("kotlin.") == true -> false
    kClass.java.name.startsWith("java.") -> false
    kClass.java.name.startsWith("org.gradle") && kClass.annotations.any { it is DeclarativeWithHiddenMembers } -> false
    else -> true
}

private typealias TypeVariableAssignments = Map<KTypeParameter, SupportedType>

fun TypeVariableAssignments.applyTo(supportedType: SupportedTypeProjection): SupportedTypeProjection =
    when (supportedType) {
        SupportedTypeProjection.StarProjection -> SupportedTypeProjection.StarProjection
        is SupportedTypeProjection.ProjectedType -> SupportedTypeProjection.ProjectedType(supportedType.variance, applyToType(supportedType.type))
        is SupportedType -> applyToType(supportedType)
    }

fun TypeVariableAssignments.applyToType(supportedType: SupportedType): SupportedType =
    when (supportedType.classifier) {
        is KTypeParameter -> this[supportedType.classifier] ?: supportedType
        else -> SupportedType(supportedType.classifier, supportedType.isMarkedNullable, supportedType.arguments.map { applyTo(it) })
    }

@Suppress("ReturnCount")
private fun maybeSupportedCallable(
    host: SchemaBuildingHost,
    member: KCallable<*>,
    supertype: SuperclassWithMapping,
): SupportedCallableResult? {
    host.inContextOfModelMember(member) {
        if (member.visibility != KVisibility.PUBLIC) {
            return null
        }

        // Ignore statics:
        if (member is KProperty<*> && Modifier.isStatic(member.javaField?.modifiers ?: 0)) {
            return null
        }

        if (member.annotationsWithGetters.run { any { it is VisibleInDefinition } && any { it is HiddenInDefinition } }) {
            return ExtractionResult.Failure(
                host.schemaBuildingFailure(SchemaBuildingIssue.DeclarationBothHiddenAndVisible),
                member
            )
        }

        val isInHiddenType = supertype is MaybeDeclarativeClassInHierarchy.HiddenSuperclassInHierarchy
        val isExposedByAnnotation = member.annotations.any { it is VisibleInDefinition } || (member is KProperty<*> && member.getter.annotations.any { it is VisibleInDefinition })
        val isHiddenByAnnotation = member.annotations.any { it is HiddenInDefinition } || (member is KProperty<*> && member.getter.annotations.any { it is HiddenInDefinition })
        if (isHiddenByAnnotation || isInHiddenType && !isExposedByAnnotation) {
            return null
        }

        val parameters = memberParameters(member, host, supertype)
            .getAllOrFailWith { return ExtractionResult.Failure(it.first(), member) }

        val returnType = host.withTag(SchemaBuildingTags.returnValueType(member.returnType)) {
            supportedTypeWithSubstitution(host, supertype, member.returnType)
                .orFailWith { return ExtractionResult.Failure(it, member) }
        }

        val memberKind = when (member) {
            is KFunction<*> -> MemberKind.FUNCTION
            is KMutableProperty<*> -> MemberKind.MUTABLE_PROPERTY
            is KProperty<*> -> MemberKind.READ_ONLY_PROPERTY
            else -> error("Unexpected member kind: $member")
        }

        return ExtractionResult.Extracted(
            SupportedCallable(
                member,
                kind = memberKind,
                name = member.name,
                parameters = parameters,
                returnType = returnType
            ),
            member
        )
    }
}

private fun memberParameters(
    member: KCallable<*>,
    host: SchemaBuildingHost,
    supertype: SuperclassWithMapping
): List<SchemaResult<SupportedKParameter>> = member.parameters.filter { it != member.instanceParameter }.map { param ->
    host.withTag(SchemaBuildingTags.parameter(param)) {
        supportedTypeWithSubstitution(host, supertype, param.type).map { paramType ->
            SupportedKParameter(param.name, paramType, param.isVararg, param.isOptional)
        }
    }
}

private fun supportedTypeWithSubstitution(host: SchemaBuildingHost, supertype: SuperclassWithMapping, type: KType): SchemaResult<SupportedType> =
    type.asSupported(host).map(supertype.typeVariableAssignments::applyToType)


/**
 * Among multiple members in the ones collect from supertypes, keeps just the first one for each unique signature.
 *
 * Provided the [members] map sorted topologically from subtypes to supertypes, if there are overrides, the implementation preserves the
 * overriding member from the subtype and drops the member(s) from the supertype(s).
 */
private fun mergeMembersBySignature(members: Map<KClass<*>, List<SupportedCallableResult>>): Map<KClass<*>, List<SupportedCallableResult>> {
    fun SupportedCallableResult.signature() = when (this) {
        is ExtractionResult.Extracted -> listOf(result.name, result.kind) + result.parameters
        is ExtractionResult.Failure -> listOf(this)
    }

    val entries = members.entries
        .flatMap { (kClass, classMembers) -> classMembers.map { kClass to it } }
        .distinctBy { (_, member) -> member.signature() }

    return entries.groupBy({ (kClass, _) -> kClass }, valueTransform = { it.second })
}


sealed interface MaybeDeclarativeClassInHierarchy {
    val superClass: KClass<*>

    /**
     * A parameter-to-argument type mapping for a given [superClass] found in the type hierarchy of the [superClass].
     * For each of the type parameters of the superclass, contains the DCL-supported type that is used as the type argument for that parameter in [superClass].
     */
    sealed interface SuperclassWithMapping : MaybeDeclarativeClassInHierarchy {
        val typeVariableAssignments: TypeVariableAssignments
    }

    data class VisibleSuperclassInHierarchy(override val superClass: KClass<*>, override val typeVariableAssignments: TypeVariableAssignments) : SuperclassWithMapping
    data class HiddenSuperclassInHierarchy(override val superClass: KClass<*>, override val typeVariableAssignments: TypeVariableAssignments) : SuperclassWithMapping
    data class NonDeclarativeSuperclassInHierarchy(override val superClass: KClass<*>, val reason: SchemaResult.Failure) : MaybeDeclarativeClassInHierarchy
}

internal fun collectDeclarativeSuperclassHierarchy(host: SchemaBuildingHost, kClass: KClass<*>): Iterable<MaybeDeclarativeClassInHierarchy> {
    /**
     * Using [VisibleSuperclassInHierarchy] as keys for all classes at first.
     * In post-processing, some might become [MaybeDeclarativeClassInHierarchy.HiddenSuperclassInHierarchy] or [MaybeDeclarativeClassInHierarchy.NonDeclarativeSuperclassInHierarchy].
     */
    val reachedFrom = mutableMapOf<VisibleSuperclassInHierarchy, MutableSet<VisibleSuperclassInHierarchy>>()
    val nonDeclarative = mutableSetOf<MaybeDeclarativeClassInHierarchy.NonDeclarativeSuperclassInHierarchy>()

    /**
     * Using a separate list to ensure that the result is topologically sorted (by adding a node only after all its connections are added, then reversing the result).
     * This is needed because the members are collected and deduplicated using the order of elements in this result.
     */
    val visitedDeclarativeSupertypes = mutableListOf<VisibleSuperclassInHierarchy>()

    fun visit(current: VisibleSuperclassInHierarchy, parent: VisibleSuperclassInHierarchy?) {
        val isSeen = current in reachedFrom

        reachedFrom.getOrPut(current) { mutableSetOf() }.run {
            if (parent != null) {
                add(parent)
            }
        }

        if (!isSeen) {
            current.superClass.supertypes.forEach { supertype ->
                val superclass = supertype.classifier as? KClass<*> ?: return@forEach
                when (val typeMappingIfSupported = typeMappingForSupertype(host, current.typeVariableAssignments, supertype)) {
                    is SchemaResult.Result -> {
                        val superClass = supertype.classifier as KClass<*>
                        visit(VisibleSuperclassInHierarchy(superClass, typeMappingIfSupported.result), current)
                    }

                    is SchemaResult.Failure -> {
                        nonDeclarative.add(MaybeDeclarativeClassInHierarchy.NonDeclarativeSuperclassInHierarchy(superclass, typeMappingIfSupported))
                    }
                }
            }

            visitedDeclarativeSupertypes += current
        }
    }

    val root = VisibleSuperclassInHierarchy(kClass, kClass.typeParameters.associateWith { SupportedType(it, isMarkedNullable = false, arguments = emptyList()) })
    visit(root, null)

    val visibilityCheckResult: Map<KClass<*>, MaybeDeclarativeClassInHierarchy> = checkDefinitionVisibilityInHierarchy(host, root, reachedFrom)

    return visitedDeclarativeSupertypes.asReversed().map {
        visibilityCheckResult[it.superClass] ?: MaybeDeclarativeClassInHierarchy.HiddenSuperclassInHierarchy(it.superClass, it.typeVariableAssignments)
    } + nonDeclarative
}

/**
 * Given a [typeMapping] and a [supertype] (which has some concrete [KType.arguments]), produces the type mapping that tells for the given supertype which substitutions
 * its type arguments got in the subtype (and eventually in the class being processed).
 *
 * For example:
 *
 * * `class Sub<T, S>`
 * * `class Sup<G> : Sub<G, List<G>>`
 * * `class Supp : Sup<Int>`
 *
 * Here we start with the mapping for `Sup`: `{G: Int}`. From that, we go to the mapping for `Sub`: `{T: Int, S: List<Int>}`
 */
private fun typeMappingForSupertype(host: SchemaBuildingHost, typeMapping: TypeVariableAssignments, supertype: KType): SchemaResult<TypeVariableAssignments> {
    val superClass = supertype.classifier as KClass<*>
    return SchemaResult.Result(superClass.typeParameters.zip(supertype.arguments).associate { (param, arg) ->
        val argType = arg.type ?: error("Unexpected type projection in supertype: $arg; expected to have a projection with a type, got none.")

        val supportedArgType = argType.asSupported(host)
            .orFailWith { return it }

        param to when (val argClassifier = arg.type?.classifier) {
            is KTypeParameter -> typeMapping.getValue(argClassifier)
            else -> typeMapping.applyToType(supportedArgType)
        }
    })
}

private fun checkDefinitionVisibilityInHierarchy(
    host: SchemaBuildingHost,
    root: VisibleSuperclassInHierarchy,
    reachedFrom: MutableMap<VisibleSuperclassInHierarchy, MutableSet<VisibleSuperclassInHierarchy>>,
): Map<KClass<*>, MaybeDeclarativeClassInHierarchy> {
    val visible = hashSetOf<VisibleSuperclassInHierarchy>()
    val invisible = hashSetOf<VisibleSuperclassInHierarchy>()
    val invalid = hashSetOf<MaybeDeclarativeClassInHierarchy.NonDeclarativeSuperclassInHierarchy>()

    fun checkVisibility(from: VisibleSuperclassInHierarchy): Boolean {
        if (from in visible) return true
        if (from in invisible) return false

        val annotatedVisible = from.superClass.annotations.any { it is VisibleInDefinition }
        val annotatedHidden = from.superClass.annotations.any { it is HiddenInDefinition }

        return when {
            annotatedHidden && annotatedVisible -> invalid.add(
                MaybeDeclarativeClassInHierarchy.NonDeclarativeSuperclassInHierarchy(
                    from.superClass,
                    host.inContextOfModelClass(from.superClass) {
                        host.schemaBuildingFailure(SchemaBuildingIssue.DeclarationBothHiddenAndVisible)
                    }
                )
            )

            annotatedVisible -> {
                visible.add(from)
                true
            }

            (from == root && !annotatedHidden || reachedFrom.getValue(from).any { checkVisibility(it) }) && isVisibleDeclarativeDefinitionClass(from.superClass) -> {
                visible.add(from)
                true
            }

            else -> {
                invisible.add(from)
                false
            }

        }
    }

    reachedFrom.keys.forEach { checkVisibility(it) }
    return visible.associateBy { it.superClass } + invalid.associateBy { it.superClass }
}

private fun isVisibleDeclarativeDefinitionClass(kClass: KClass<*>): Boolean = when {
    kClass.annotations.any { it is HiddenInDefinition } -> false
    else -> true
}

data class SupportedCallable(
    val kCallable: KCallable<*>,
    val kind: MemberKind,
    val name: String,
    val parameters: List<SupportedKParameter>,
    val returnType: SupportedType
)

enum class MemberKind {
    FUNCTION, READ_ONLY_PROPERTY, MUTABLE_PROPERTY;

    val isProperty get() = this == READ_ONLY_PROPERTY || this == MUTABLE_PROPERTY
}

fun KTypeProjection.asSupported(host: SchemaBuildingHost): SchemaResult<SupportedTypeProjection> {
    return when {
        this == KTypeProjection.STAR -> schemaResult(SupportedTypeProjection.StarProjection)
        else -> {
            val type = type ?: host.schemaBuildingError("Type projection is not a star projection but the type is null: $this")
            type.asSupported(host).map {
                if (variance != KVariance.INVARIANT)
                    SupportedTypeProjection.ProjectedType(variance!!, it)
                else it
            }
        }
    }
}

fun KType.asSupported(host: SchemaBuildingHost): SchemaResult<SupportedType> = when {
    classifier == null -> host.schemaBuildingFailure(SchemaBuildingIssue.NonClassifiableType(this))
    else -> {
        val args = arguments.map { arg ->
            when (val argSupported = arg.asSupported(host)) {
                is SchemaResult.Failure -> return argSupported
                is SchemaResult.Result -> argSupported.result
            }
        }
        SchemaResult.Result(SupportedType(classifier!!, isMarkedNullable, args))
    }
}

sealed class SupportedTypeProjection {
    /**
     * Represents a DCL-supported concrete type, or, when used as a type projection, an invariant type.
     */
    data class ProjectedType(val variance: KVariance, val type: SupportedType) : SupportedTypeProjection()
    data class SupportedType(val classifier: KClassifier, val isMarkedNullable: Boolean, val arguments: List<SupportedTypeProjection>) : SupportedTypeProjection()
    object StarProjection : SupportedTypeProjection()
}

fun SupportedType.toKType(): KType =
    classifier.createType(
        arguments = arguments.map { argType ->
            when (argType) {
                SupportedTypeProjection.StarProjection -> KTypeProjection.STAR
                is SupportedTypeProjection.ProjectedType -> KTypeProjection(argType.variance, argType.type.toKType())
                is SupportedType -> KTypeProjection(variance = KVariance.INVARIANT, type = argType.toKType())
            }
        },
        annotations = emptyList(), nullable = isMarkedNullable
    )

data class SupportedKParameter(
    val name: String?,
    val type: SupportedType,
    val isVararg: Boolean,
    val isOptional: Boolean
)
