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

package org.gradle.internal.declarativedsl.utils

import org.gradle.api.NamedDomainObjectContainer
import org.gradle.declarative.dsl.model.annotations.ElementFactoryName
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.TypeVariable
import java.util.Locale
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.KTypeParameter
import kotlin.reflect.KVariance
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.full.starProjectedType

object DclContainerMemberExtractionUtils {
    fun elementTypeFromNdocContainerType(
        type: Type
    ) = JavaTypeUtils.asCandidateType(type)?.let { elementTypeFromNdocContainerType(it, JavaTypeUtils) }
        .takeIf { it is Class<*> || it is ParameterizedType } // can't use type variables and wildcards as element types

    fun elementTypeFromNdocContainerType(containerType: KType): KType? =
        KotlinTypeUtils.asCandidateType(containerType)?.let { elementTypeFromNdocContainerType(it, KotlinTypeUtils) }
            .takeIf { it?.classifier is KClass<*> }

    private fun <TRaw, TArg> elementTypeFromNdocContainerType(
        containerType: CandidateContainerType<TRaw, TArg>,
        typeUtils: TypeUtils<TRaw, TArg>
    ): TArg? = when (containerType.typeArgs) {
        emptyList<TArg>() -> {
            // can't be NDOC<T>, as that would be a ParameterizedType, so go and find the supertype
            if (typeUtils.isNdocSubclass(containerType.rawClass)) {
                findNamedDomainObjectContainerTypeArgument(containerType, typeUtils)
            } else null
        }

        else -> run {
            if (typeUtils.isExactlyNdocType(containerType.rawClass)) {
                containerType.typeArgs?.single()
            } else if (typeUtils.isNdocSubclass(containerType.rawClass)) {
                findNamedDomainObjectContainerTypeArgument(containerType, typeUtils)
            } else null
        }
    }

    private fun <TRaw, TArg> findNamedDomainObjectContainerTypeArgument(
        containerSubtype: CandidateContainerType<TRaw, TArg>,
        typeUtils: TypeUtils<TRaw, TArg>
    ): TArg? {
        val visited: HashSet<TRaw> = hashSetOf()

        fun findIn(type: CandidateContainerType<TRaw, TArg>): TArg? {
            if (typeUtils.isExactlyNdocType(type.rawClass)) {
                return type.typeArgs?.single()
            }

            if (!visited.add(type.rawClass)) {
                return null
            }

            return type.candidateSupertypes().firstNotNullOfOrNull(::findIn)
        }

        return findIn(containerSubtype)
    }

    fun elementFactoryFunctionNameFromElementType(elementType: KClass<*>): String =
        elementFactoryFunctionNameFromElementType(elementType.starProjectedType)

    fun elementFactoryFunctionNameFromElementType(elementType: KType): String =
        when (val annotation = (elementType.classifier as? KClass<*>)?.annotations.orEmpty().filterIsInstance<ElementFactoryName>().singleOrNull()) {
            null -> null
            else -> annotation.value.takeIf { it != "" } ?: error("@${ElementFactoryName::class.java.simpleName} must provide a value")
        } ?: (elementType.classifier as? KClass<*>)?.simpleName?.replaceFirstChar { it.lowercase(Locale.ROOT) }
        ?: (elementType.classifier as? KTypeParameter)?.name?.replaceFirstChar { it.lowercase(Locale.ROOT) }
        ?: error("cannot determine element factory name for unexpected container element type $elementType")

}

private interface CandidateContainerType<TRaw, TArg> {
    /**
     * The "erased", or "raw" type. It should not depend on any type arguments that a generic type instantiation may have.
     */
    val rawClass: TRaw

    /**
     * The real known type arguments for the type.
     *
     * Example: consider `class Foo<T>` and `class Bar<R> : Foo<R>`.
     * If there is a declared type `Bar<String>`, the [typeArgs] for `Foo` must contain `String`.
     */
    val typeArgs: List<TArg>?
    fun candidateSupertypes(): Sequence<CandidateContainerType<TRaw, TArg>>
}

private interface TypeUtils<TRaw, TArg> {
    fun isNdocSubclass(type: TRaw): Boolean
    fun isExactlyNdocType(type: TRaw): Boolean
    fun asCandidateType(type: TArg): CandidateContainerType<TRaw, TArg>?
}

private class JavaCandidateContainerType(
    override val rawClass: Class<*>,
    override val typeArgs: List<Type>?
) : CandidateContainerType<Class<*>, Type> {
    override fun candidateSupertypes(): Sequence<JavaCandidateContainerType> =
        (rawClass.genericInterfaces.asSequence() + rawClass.genericSuperclass).mapNotNull { type ->
            when (type) {
                is Class<*> ->
                    JavaTypeUtils.asCandidateType(type)

                is ParameterizedType -> {
                    fun typeParamIndex(typeVariable: TypeVariable<*>): Int =
                        rawClass.typeParameters.indexOf(typeVariable)

                    val actualSupertypeArgs = type.actualTypeArguments.map { arg ->
                        if (arg is TypeVariable<*>) {
                            val indexInOwner = typeParamIndex(arg)
                            if (indexInOwner == -1) {
                                return@mapNotNull null // can't find the type argument for the supertype
                            }
                            typeArgs?.getOrNull(indexInOwner) ?: return@mapNotNull null
                        } else arg
                    }
                    val rawSupertype = type.rawType as? Class<*> ?: return@mapNotNull null
                    JavaCandidateContainerType(rawSupertype, actualSupertypeArgs)
                }

                else -> null
            }
        }
}

private object JavaTypeUtils : TypeUtils<Class<*>, Type> {

    override fun isNdocSubclass(type: Class<*>): Boolean =
        NamedDomainObjectContainer::class.java.isAssignableFrom(type)

    override fun isExactlyNdocType(type: Class<*>): Boolean =
        type == NamedDomainObjectContainer::class.java

    override fun asCandidateType(type: Type): JavaCandidateContainerType? = when (type) {
        is Class<*> -> JavaCandidateContainerType(type, emptyList())
        is ParameterizedType -> type.run {
            val rawClass = this.rawType as? Class<*>
                ?: return@run null
            JavaCandidateContainerType(rawClass, this.actualTypeArguments.asList())
        }

        else -> null
    }
}

private class KotlinCandidateContainerType(
    override val rawClass: KClass<*>,
    override val typeArgs: List<KType?>?
) : CandidateContainerType<KClass<*>, KType?> {
    override fun candidateSupertypes(): Sequence<KotlinCandidateContainerType> =
        rawClass.supertypes.asSequence().mapNotNull supertypeCandidate@{ supertype ->
            val rawType = supertype.classifier as? KClass<*>
                ?: return@supertypeCandidate null
            val typeArgs = supertype.arguments.map { typeArg ->
                val type = typeArg.type
                    // projected types cannot be element types; keep them as nulls if they are unrelated
                    // but discard them (automatically because they are nulls) if they end up as the resulting element type
                    .takeIf { typeArg.variance == KVariance.INVARIANT }
                val classifier = type?.classifier
                if (classifier is KTypeParameter) {
                    val index = rawClass.typeParameters.indexOf(classifier).takeIf { it != -1 }
                        ?: return@supertypeCandidate null
                    typeArgs?.getOrNull(index)
                } else type
            }
            KotlinCandidateContainerType(rawType, typeArgs)
        }
}

private object KotlinTypeUtils : TypeUtils<KClass<*>, KType?> {

    override fun isNdocSubclass(type: KClass<*>): Boolean =
        type.isSubclassOf(NamedDomainObjectContainer::class)

    override fun isExactlyNdocType(type: KClass<*>): Boolean =
        type == NamedDomainObjectContainer::class

    override fun asCandidateType(type: KType?): CandidateContainerType<KClass<*>, KType?>? =
        when (val classifier = type?.classifier) {
            null -> null
            is KClass<*> -> KotlinCandidateContainerType(classifier, type.arguments.map { if (it.variance == KVariance.INVARIANT) it.type else null })
            else -> null
        }
}
