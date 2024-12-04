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
import org.gradle.internal.declarativedsl.schemaBuilder.MemberFilter
import org.gradle.internal.declarativedsl.schemaBuilder.TypeDiscovery
import org.gradle.internal.declarativedsl.schemaBuilder.isPublicAndRestricted
import kotlin.reflect.KClass
import kotlin.reflect.full.memberFunctions


/**
 * Provides type discovery via functions that return an object or configure an object accepting a lambda as the last parameter
 * (via [org.gradle.api.Action] or a Kotlin function type, see [gradleConfigureLambdas]).
 *
 * All configured or returned types that appear in [isPublicAndRestricted]-matching will be discovered (for now, regardless of actual function semantics).
 */
internal
class TypeDiscoveryFromRestrictedFunctions : AnalysisSchemaComponent {
    override fun typeDiscovery(): List<TypeDiscovery> = listOf(
        FunctionLambdaTypeDiscovery(isPublicAndRestricted, gradleConfigureLambdas),
        FunctionReturnTypeDiscovery(isPublicAndRestricted)
    )
}


private
class FunctionLambdaTypeDiscovery(
    private val memberFilter: MemberFilter,
    private val configureLambdas: ConfigureLambdaHandler,
) : TypeDiscovery {
    /**
     * Collect everything that potentially looks like types configured by the lambdas.
     * TODO: this may be excessive
     */
    override fun getClassesToVisitFrom(typeDiscoveryServices: TypeDiscovery.TypeDiscoveryServices, kClass: KClass<*>): Iterable<KClass<*>> =
        kClass.memberFunctions
            .filter { memberFilter.shouldIncludeMember(it) }
            .mapNotNullTo(mutableSetOf()) { fn ->
                fn.parameters.lastOrNull()?.let {
                    configureLambdas.getTypeConfiguredByLambda(it.type)?.classifier as? KClass<*>
                }
            }
}


private
class FunctionReturnTypeDiscovery(
    private val memberFilter: MemberFilter
) : TypeDiscovery {
    /**
     * Collects everything that restricted functions mention as return values.
     */
    override fun getClassesToVisitFrom(typeDiscoveryServices: TypeDiscovery.TypeDiscoveryServices, kClass: KClass<*>): Iterable<KClass<*>> =
        kClass.memberFunctions
            .filter { memberFilter.shouldIncludeMember(it) }
            .mapNotNullTo(mutableSetOf()) { fn ->
                fn.returnType.classifier as? KClass<*>
            }
}
