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

package org.gradle.internal.declarativedsl.common

import org.gradle.internal.declarativedsl.evaluationSchema.AnalysisSchemaComponent
import org.gradle.internal.declarativedsl.evaluationSchema.gradleConfigureLambdas
import org.gradle.internal.declarativedsl.schemaBuilder.ConfigureLambdaHandler
import org.gradle.internal.declarativedsl.schemaBuilder.MemberKind
import org.gradle.internal.declarativedsl.schemaBuilder.TypeDiscovery
import org.gradle.internal.declarativedsl.schemaBuilder.toKType
import kotlin.reflect.KClass


/**
 * Provides type discovery via functions that return an object or configure an object accepting a lambda as the last parameter
 * (via [org.gradle.api.Action] or a Kotlin function type, see [gradleConfigureLambdas]).
 */
internal
class TypeDiscoveryFromRestrictedFunctions : AnalysisSchemaComponent {
    override fun typeDiscovery(): List<TypeDiscovery> = listOf(
        FunctionLambdaTypeDiscovery(gradleConfigureLambdas),
        FunctionReturnTypeDiscovery()
    )
}


private
class FunctionLambdaTypeDiscovery(
    private val configureLambdas: ConfigureLambdaHandler,
) : TypeDiscovery {
    /**
     * Collect everything that potentially looks like types configured by the lambdas.
     * TODO: this may be excessive
     */
    override fun getClassesToVisitFrom(typeDiscoveryServices: TypeDiscovery.TypeDiscoveryServices, kClass: KClass<*>): Iterable<TypeDiscovery.DiscoveredClass> =
        typeDiscoveryServices.host.classMembers(kClass).potentiallyDeclarativeMembers
            .filter { it.kind == MemberKind.FUNCTION }
            .mapNotNullTo(mutableSetOf()) { fn ->
                (fn.parameters.lastOrNull()?.type?.toKType())
                    ?.let(configureLambdas::getTypeConfiguredByLambda)
                    ?.classifier
                    ?.let { it as? KClass<*> }
                    ?.let { TypeDiscovery.DiscoveredClass(it, isHidden = false)
                }
            }
}


private
class FunctionReturnTypeDiscovery : TypeDiscovery {
    /**
     * Collects everything that restricted functions mention as return values.
     */
    override fun getClassesToVisitFrom(typeDiscoveryServices: TypeDiscovery.TypeDiscoveryServices, kClass: KClass<*>): Iterable<TypeDiscovery.DiscoveredClass> =
        typeDiscoveryServices.host.classMembers(kClass).potentiallyDeclarativeMembers
            .mapNotNullTo(mutableSetOf()) { fn ->
                (fn.returnType.classifier as? KClass<*>)
                    ?.let { TypeDiscovery.DiscoveredClass(it, isHidden = false) }
            }
}
