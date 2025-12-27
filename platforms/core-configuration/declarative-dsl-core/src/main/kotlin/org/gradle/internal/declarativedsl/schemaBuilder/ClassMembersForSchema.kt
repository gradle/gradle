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
import org.gradle.internal.declarativedsl.schemaBuilder.MaybeDeclarativeClassInHierarchy.SuperclassWithMapping
import org.gradle.internal.declarativedsl.schemaBuilder.MaybeDeclarativeClassInHierarchy.VisibleSuperclassInHierarchy
import org.gradle.internal.declarativedsl.schemaBuilder.SupportedTypeProjection.SupportedType
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
import kotlin.reflect.jvm.javaField

data class ClassMembersForSchema(
    val potentiallyDeclarativeMembersBySupertype: Map<KClass<*>, List<SupportedCallable>>,
    val unsupportedMembersBySupertype: Map<KClass<*>, List<NonDeclarativeMember>>
) {
    data class NonDeclarativeMember(val member: KCallable<*>, val reason: NonDeclarativeReason, val context: List<SchemaBuildingContextElement>)

    val potentiallyDeclarativeMembers: Iterable<SupportedCallable> by lazy { potentiallyDeclarativeMembersBySupertype.values.flatten() }
}

internal fun collectMembersForSchema(host: SchemaBuildingHost, kClass: KClass<*>): ClassMembersForSchema {
    return host.inContextOfModelClass(kClass) {
        val supertypesWithMapping = host.declarativeSupertypesHierarchy(kClass)

        val supported: MutableMap<KClass<*>, MutableList<SupportedCallable>> = mutableMapOf()
        val unsupported: MutableMap<KClass<*>, MutableList<ClassMembersForSchema.NonDeclarativeMember>> = mutableMapOf()

        supertypesWithMapping.forEach { supertype ->
            host.inContextOfModelClass(supertype.superClass) {
                when (supertype) {
                    is SuperclassWithMapping -> {
                        val supportedMembers by lazy { supported.getOrPut(supertype.superClass) { mutableListOf() } }
                        val unsupported by lazy { unsupported.getOrPut(supertype.superClass) { mutableListOf() } }

                        supertype.superClass.declaredMembers.forEach { member ->
                            handleMember(host, member, supertype, supportedMembers::add, unsupported::add)
                        }
                    }

                    is MaybeDeclarativeClassInHierarchy.NonDeclarativeSuperclassInHierarchy -> Unit
                }
            }
        }

        ClassMembersForSchema(mergeMembersBySignature(supported), unsupported)
    }
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
        else -> SupportedType(supportedType.classifier, supportedType.arguments.map { applyTo(it) })
    }

private sealed class TypeVariableAssignmentsIfSupported {
    class Supported(val typeMapping: TypeVariableAssignments) : TypeVariableAssignmentsIfSupported()
    class Unsupported(val reason: NonDeclarativeReason) : TypeVariableAssignmentsIfSupported()
}

private fun handleMember(
    host: SchemaBuildingHost,
    member: KCallable<*>,
    supertype: SuperclassWithMapping,
    addAsSupported: (SupportedCallable) -> Unit,
    addAsUnsupported: (ClassMembersForSchema.NonDeclarativeMember) -> Unit
) {
    if (member.annotationsWithGetters.run { any { it is VisibleInDefinition } && any { it is HiddenInDefinition} }) {
        host.schemaBuildingFailure("Conflicting annotations on $member: both @${VisibleInDefinition::class.simpleName} and @${HiddenInDefinition::class.simpleName} are present")
    }

    if (member.visibility != KVisibility.PUBLIC) {
        return
    }

    // Ignore statics:
    if (member is KProperty<*> && java.lang.reflect.Modifier.isStatic(member.javaField?.modifiers ?: 0)) {
        return
    }

    val isInHiddenType = supertype is MaybeDeclarativeClassInHierarchy.HiddenSuperclassInHierarchy
    val isExposedByAnnotation = member.annotations.any { it is VisibleInDefinition } || (member is KProperty<*> && member.getter.annotations.any { it is VisibleInDefinition })
    val isHiddenByAnnotation = member.annotations.any { it is HiddenInDefinition } || (member is KProperty<*> && member.getter.annotations.any { it is HiddenInDefinition })
    if (isHiddenByAnnotation || isInHiddenType && !isExposedByAnnotation) {
        return
    }

    host.inContextOfModelMember(member) {
        fun supportedTypeWithSubstitution(type: KType): SupportedType? =
            type.asSupported()?.let(supertype.typeVariableAssignments::applyToType)

        val parameters = member.parameters.filter { it != member.instanceParameter }.map { param ->
            host.withTag(SchemaBuildingTags.parameter(param)) {
                val supportedType = supportedTypeWithSubstitution(param.type)
                    ?: run {
                        addAsUnsupported(
                            ClassMembersForSchema.NonDeclarativeMember(
                                member,
                                NonDeclarativeReason.UnsupportedTypeUsage(param.type), // FIXME use the more precise reasons
                                host.context.toList()
                            )
                        )
                        return
                    }

                SupportedKParameter(param.name, supportedType, param.isVararg, param.isOptional)
            }
        }

        val returnType = host.withTag(SchemaBuildingTags.returnValueType(member.returnType)) {
            supportedTypeWithSubstitution(member.returnType)
                ?: run {
                    addAsUnsupported(
                        ClassMembersForSchema.NonDeclarativeMember( // FIXME use the more precise reasons
                            member,
                            NonDeclarativeReason.UnsupportedTypeUsage(member.returnType), // FIXME use the more precise reasons
                            host.context.toList()
                        )
                    )
                    return
                }
        }

        val memberKind = when (member) {
            is KFunction<*> -> MemberKind.FUNCTION
            is KMutableProperty<*> -> MemberKind.MUTABLE_PROPERTY
            is KProperty<*> -> MemberKind.READ_ONLY_PROPERTY
            else -> error("Unexpected member kind: $member")
        }

        addAsSupported(
            SupportedCallable(
                member,
                kind = memberKind,
                name = member.name,
                parameters = parameters,
                returnType = returnType
            )
        )
    }
}


/**
 * Among multiple members in the ones collect from supertypes, keeps just the first one for each unique signature.
 *
 * Provided the [members] map sorted topologically from subtypes to supertypes, if there are overrides, the implementation preserves the
 * overriding member from the subtype and drops the member(s) from the supertype(s).
 */
private fun mergeMembersBySignature(members: Map<KClass<*>, List<SupportedCallable>>): Map<KClass<*>, List<SupportedCallable>> {
    fun SupportedCallable.signature() = listOf(name, this.kind) + this.parameters

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
    data class NonDeclarativeSuperclassInHierarchy(override val superClass: KClass<*>, val reason: NonDeclarativeReason) : MaybeDeclarativeClassInHierarchy
}

internal fun collectDeclarativeSuperclassHierarchy(kClass: KClass<*>): Iterable<MaybeDeclarativeClassInHierarchy> {
    /**
     * Using [VisibleSuperclassInHierarchy] as keys for all classes at first.
     * In post-processing, some might become [MaybeDeclarativeClassInHierarchy.HiddenSuperclassInHierarchy].
     */
    val reachedFrom = mutableMapOf<VisibleSuperclassInHierarchy, MutableSet<VisibleSuperclassInHierarchy>>()
    val nonDeclarative = mutableSetOf<MaybeDeclarativeClassInHierarchy.NonDeclarativeSuperclassInHierarchy>()

    /** Using a separate list to ensure that the result is topologically sorted (by adding a node only after all its connections are added, then reversing the result). */
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
                when (val typeMappingIfSupported = typeMappingForSupertype(current.typeVariableAssignments, supertype)) {
                    is TypeVariableAssignmentsIfSupported.Supported -> {
                        val superClass = supertype.classifier as KClass<*>
                        visit(VisibleSuperclassInHierarchy(superClass, typeMappingIfSupported.typeMapping), current)
                    }

                    is TypeVariableAssignmentsIfSupported.Unsupported -> {
                        nonDeclarative.add(MaybeDeclarativeClassInHierarchy.NonDeclarativeSuperclassInHierarchy(superclass, typeMappingIfSupported.reason))
                    }
                }
            }

            visitedDeclarativeSupertypes += current
        }
    }

    val root = VisibleSuperclassInHierarchy(kClass, kClass.typeParameters.associateWith { SupportedType(it, emptyList()) })
    visit(root, null)

    val visible = checkDefinitionVisibilityInHierarchy(root, reachedFrom)

    return visitedDeclarativeSupertypes.asReversed().map {
        if (it in visible) it else MaybeDeclarativeClassInHierarchy.HiddenSuperclassInHierarchy(it.superClass, it.typeVariableAssignments)
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
private fun typeMappingForSupertype(typeMapping: TypeVariableAssignments, supertype: KType): TypeVariableAssignmentsIfSupported {
    val superClass = supertype.classifier as KClass<*>
    return TypeVariableAssignmentsIfSupported.Supported(superClass.typeParameters.zip(supertype.arguments).associate { (param, arg) ->
        val argType = arg.type ?: error("Unexpected type projection in supertype: $arg; expected to have a projection with a type, got none.")

        val supportedArgType = argType.asSupported() ?: return TypeVariableAssignmentsIfSupported.Unsupported(NonDeclarativeReason.UnsupportedTypeUsage(argType))

        param to when (val argClassifier = arg.type?.classifier) {
            is KTypeParameter -> typeMapping.getValue(argClassifier)
            else -> typeMapping.applyToType(supportedArgType)
        }
    })
}

private fun checkDefinitionVisibilityInHierarchy(
    root: VisibleSuperclassInHierarchy,
    reachedFrom: MutableMap<VisibleSuperclassInHierarchy, MutableSet<VisibleSuperclassInHierarchy>>,
): HashSet<VisibleSuperclassInHierarchy> {
    val visible = hashSetOf<VisibleSuperclassInHierarchy>()
    val invisible = hashSetOf<VisibleSuperclassInHierarchy>()

    fun checkVisibility(from: VisibleSuperclassInHierarchy): Boolean {
        if (from in visible) return true
        if (from in invisible) return false

        val annotatedVisible = from.superClass.annotations.any { it is VisibleInDefinition }
        val annotatedHidden = from.superClass.annotations.any { it is HiddenInDefinition }

        return when {
            annotatedHidden && annotatedVisible -> {
                // TODO: properly collect this error and report in a batch
                error("Conflicting annotations on ${from.superClass.qualifiedName}: both @${HiddenInDefinition::class.simpleName} and @${VisibleInDefinition::class.simpleName} are present")
            }

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
    return visible
}

private fun isVisibleDeclarativeDefinitionClass(kClass: KClass<*>): Boolean = when {
    kClass == Any::class -> false
    kClass == Iterable::class -> false
    kClass.annotations.any { it is HiddenInDefinition } -> false
    kClass.java.name.startsWith("java.") || kClass.java.name.startsWith("kotlin.") -> false
    else -> true
}

sealed interface NonDeclarativeReason {
    class UnsupportedTypeUsage(val type: KType) : NonDeclarativeReason
    class UnsupportedNullableType(val type: KType) : NonDeclarativeReason // FIXME use the more precise reasons
    class UnsupportedTypeProjection(val type: KTypeProjection) : NonDeclarativeReason
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

fun KTypeProjection.asSupported(): SupportedTypeProjection? {
    return when {
        this == KTypeProjection.STAR -> SupportedTypeProjection.StarProjection
        variance != KVariance.INVARIANT -> SupportedTypeProjection.ProjectedType(variance!!, type?.asSupported() ?: return null)
        else -> type?.asSupported()
    }
}

fun KType.asSupported(): SupportedType? = when {
    isMarkedNullable -> null
    classifier == null -> null
    else -> {
        val args = arguments.mapNotNull { it.asSupported() }
        if (args.size != arguments.size) { // i.e., there is an unsupported argument
            null
        } else {
            SupportedType(classifier!!, args)
        }
    }
}

sealed class SupportedTypeProjection {
    /**
     * Represents a DCL-supported concrete type, or, when used as a type projection, an invariant type.
     */
    data class ProjectedType(val variance: KVariance, val type: SupportedType) : SupportedTypeProjection()
    data class SupportedType(val classifier: KClassifier, val arguments: List<SupportedTypeProjection>) : SupportedTypeProjection()
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
        annotations = emptyList(), nullable = false
    )

data class SupportedKParameter(
    val name: String?,
    val type: SupportedType,
    val isVararg: Boolean,
    val isOptional: Boolean
)
