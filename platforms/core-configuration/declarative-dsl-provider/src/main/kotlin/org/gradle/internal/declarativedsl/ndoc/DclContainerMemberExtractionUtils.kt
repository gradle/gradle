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
import org.gradle.internal.declarativedsl.schemaBuilder.SupportedTypeProjection
import org.gradle.internal.declarativedsl.schemaBuilder.SupportedTypeProjection.SupportedType
import org.gradle.internal.declarativedsl.schemaBuilder.toKType
import java.util.Locale
import kotlin.reflect.KClass
import kotlin.reflect.KTypeParameter
import kotlin.reflect.KVariance

object DclContainerMemberExtractionUtils {

    fun elementTypeFromNdocContainerType(host: SchemaBuildingHost, containerType: SupportedType): SupportedType? {
        val kClass = containerType.classifier as? KClass<*> ?: return null

        return host.declarativeSupertypesHierarchy(kClass)
            .find { it.superClass == NamedDomainObjectContainer::class }
            ?.let { (it as? MaybeDeclarativeClassInHierarchy.SuperclassWithMapping)?.typeVariableAssignments }
            ?.values?.singleOrNull()
            ?.let { typeArgumentForNdocElement ->
                if (typeArgumentForNdocElement.classifier is KTypeParameter) {
                    val index = kClass.typeParameters.indexOf(typeArgumentForNdocElement.classifier)
                    containerType.arguments.getOrNull(index)?.let { arg ->
                        when (arg) {
                            is SupportedTypeProjection.ProjectedType -> arg.type.takeIf { arg.variance != KVariance.IN }
                            is SupportedType -> arg
                            SupportedTypeProjection.StarProjection -> null
                        }.takeIf { it?.classifier !is KTypeParameter } // accept only concrete types but not type parameters
                    }
                } else typeArgumentForNdocElement
            }
    }

    fun elementFactoryFunctionNameFromElementType(supportedType: SupportedType): String =
        when (val annotation = (supportedType.classifier as? KClass<*>)?.annotations.orEmpty().filterIsInstance<ElementFactoryName>().singleOrNull()) {
            null -> null
            else -> annotation.value.takeIf { it != "" } ?: error("@${ElementFactoryName::class.java.simpleName} must provide a value")
        } ?: (supportedType.classifier as? KClass<*>)?.simpleName?.replaceFirstChar { it.lowercase(Locale.ROOT) }
        ?: (supportedType.classifier as? KTypeParameter)?.name?.replaceFirstChar { it.lowercase(Locale.ROOT) }
        ?: error("cannot determine element factory name for unexpected container element type ${supportedType.toKType()}")
}
