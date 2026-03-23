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

package org.gradle.internal.declarativedsl.ndoc

import org.gradle.api.NamedDomainObjectContainer
import org.gradle.declarative.dsl.model.annotations.ElementFactoryName
import org.gradle.internal.declarativedsl.schemaBuilder.MaybeDeclarativeClassInHierarchy
import org.gradle.internal.declarativedsl.schemaBuilder.SchemaBuildingHost
import org.gradle.internal.declarativedsl.schemaBuilder.SchemaBuildingIssue
import org.gradle.internal.declarativedsl.schemaBuilder.SchemaResult
import org.gradle.internal.declarativedsl.schemaBuilder.SupportedTypeProjection
import org.gradle.internal.declarativedsl.schemaBuilder.SupportedTypeProjection.SupportedType
import org.gradle.internal.declarativedsl.schemaBuilder.flatMap
import org.gradle.internal.declarativedsl.schemaBuilder.schemaBuildingError
import org.gradle.internal.declarativedsl.schemaBuilder.schemaBuildingFailure
import org.gradle.internal.declarativedsl.schemaBuilder.schemaResult
import org.gradle.internal.declarativedsl.schemaBuilder.toKType
import java.util.Locale
import kotlin.reflect.KClass
import kotlin.reflect.KTypeParameter
import kotlin.reflect.KVariance

object DclContainerMemberExtractionUtils {

    /**
     * Tries to extract the DCL element type from a potential representation of a [NamedDomainObjectContainer] type [containerType].
     * If the type is [NamedDomainObjectContainer], extracts the explicit type argument. If it is a subtype of NDOC, extracts the type argument
     * from the supertype hierarchy.
     *
     * @return
     * * [SchemaResult.Result]:
     *   * with a `null` result if the [containerType] is not a [NamedDomainObjectContainer] or its subtype
     *   * with a non-null [SupportedType] result if the [containerType] is a subtype of NDOC and the type argument is a concrete type
     * * [SchemaResult.Failure]:
     *   * if the element type cannot be represented for DCL (e.g., it is a class-bound type parameter usage)
     *   * if the element type is a `*` or `in`-projection
     */
    fun elementTypeFromNdocContainerType(host: SchemaBuildingHost, containerType: SupportedType): SchemaResult<SupportedType?> {
        val kClass = containerType.classifier as? KClass<*> ?: return schemaResult(null)

        return host.declarativeSupertypesHierarchy(kClass)
            .find { it.superClass == NamedDomainObjectContainer::class }
            ?.let { (it as? MaybeDeclarativeClassInHierarchy.SuperclassWithMapping)?.typeVariableAssignments }
            ?.values?.singleOrNull()
            ?.let { typeArgumentForNdocElement ->
                if (typeArgumentForNdocElement.classifier is KTypeParameter) {
                    val index = kClass.typeParameters.indexOf(typeArgumentForNdocElement.classifier)
                    val argWithSubstitution = containerType.arguments.getOrNull(index)?.let { arg ->
                        when (arg) {
                            // We support out-projected or concrete element types here, but not in-projected ones
                            is SupportedTypeProjection.ProjectedType -> when (arg.variance) {
                                KVariance.IN -> host.schemaBuildingFailure(SchemaBuildingIssue.IllegalVarianceInParameterizedTypeUsage(arg.variance))
                                else -> schemaResult(arg.type)
                            }
                            SupportedTypeProjection.StarProjection -> host.schemaBuildingFailure(SchemaBuildingIssue.IllegalVarianceInParameterizedTypeUsage("*"))
                            is SupportedType -> schemaResult(arg)
                        }
                    } ?: host.schemaBuildingError("Could not find the type argument for container element in ${containerType.toKType()}")
                    argWithSubstitution.flatMap {
                        if (it?.classifier is KTypeParameter)
                            host.schemaBuildingFailure(SchemaBuildingIssue.IllegalUsageOfTypeParameterBoundByClass(it.toKType()))
                        else schemaResult(it)
                    }
                } else schemaResult(typeArgumentForNdocElement)
            } ?: schemaResult(null)
    }

    fun elementFactoryFunctionNameFromElementType(supportedType: SupportedType): String =
        when (val annotation = (supportedType.classifier as? KClass<*>)?.annotations.orEmpty().filterIsInstance<ElementFactoryName>().singleOrNull()) {
            null -> null
            else -> annotation.value.takeIf { it != "" } ?: error("@${ElementFactoryName::class.java.simpleName} must provide a value")
        } ?: (supportedType.classifier as? KClass<*>)?.simpleName?.replaceFirstChar { it.lowercase(Locale.ROOT) }
        ?: (supportedType.classifier as? KTypeParameter)?.name?.replaceFirstChar { it.lowercase(Locale.ROOT) }
        ?: error("cannot determine element factory name for unexpected container element type ${supportedType.toKType()}")
}
