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

import org.gradle.internal.declarativedsl.schemaBuilder.TypeDiscovery.DiscoveredClass
import kotlin.reflect.KClass

class FunctionLambdaTypeDiscovery(
    private val configureLambdas: ConfigureLambdaHandler,
) : TypeDiscovery {
    /**
     * Collect everything that potentially looks like types configured by the lambdas.
     * TODO: this may be excessive
     */
    override fun getClassesToVisitFrom(typeDiscoveryServices: TypeDiscovery.TypeDiscoveryServices, kClass: KClass<*>): Iterable<SchemaResult<DiscoveredClass>> =
        typeDiscoveryServices.host.classMembers(kClass).declarativeMembers
            .filter { it.kind == MemberKind.FUNCTION }
            .flatMapTo(mutableSetOf()) { fn ->
                (fn.parameters.lastOrNull()?.type?.toKType())
                    ?.let(configureLambdas::getTypeConfiguredByLambda)
                    ?.asSupported(typeDiscoveryServices.host)
                    ?.let {
                        if (it is SchemaResult.Result)
                            DiscoveredClass.classesOf(it.result, DiscoveredClass.DiscoveryTag.UsedInMember(fn.kCallable)).map(::schemaResult)
                        else emptyList()
                    }
                    ?: emptyList()
                }
}

class FunctionReturnTypeDiscovery : TypeDiscovery {
    /**
     * Collects everything that restricted functions mention as return values.
     */
    override fun getClassesToVisitFrom(typeDiscoveryServices: TypeDiscovery.TypeDiscoveryServices, kClass: KClass<*>): Iterable<SchemaResult<DiscoveredClass>> =
        typeDiscoveryServices.host.classMembers(kClass).declarativeMembers
            .flatMapTo(mutableSetOf()) { fn ->
                DiscoveredClass.classesOf(fn.returnType, DiscoveredClass.DiscoveryTag.UsedInMember(fn.kCallable)).map(::schemaResult)
            }
}
