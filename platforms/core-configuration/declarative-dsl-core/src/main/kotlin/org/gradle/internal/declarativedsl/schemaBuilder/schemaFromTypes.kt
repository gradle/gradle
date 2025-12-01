/*
 * Copyright 2023 the original author or authors.
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

import org.gradle.declarative.dsl.model.annotations.HiddenInDeclarativeDsl
import org.gradle.declarative.dsl.schema.AnalysisSchema
import org.gradle.declarative.dsl.schema.FqName
import java.util.function.Consumer
import java.util.function.Predicate
import kotlin.reflect.KCallable
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KVisibility
import kotlin.reflect.full.instanceParameter


@Suppress("LongParameterList")
fun schemaFromTypes(
    topLevelReceiver: KClass<*>,
    types: Iterable<KClass<*>>,
    externalFunctionDiscovery: TopLevelFunctionDiscovery = CompositeTopLevelFunctionDiscovery(listOf()),
    externalObjects: Map<FqName, KClass<*>> = emptyMap(),
    defaultImports: List<FqName> = emptyList(),
    configureLambdas: ConfigureLambdaHandler = kotlinFunctionAsConfigureLambda,
    propertyExtractor: PropertyExtractor = DefaultPropertyExtractor(isPublicAndNotHidden),
    functionExtractor: FunctionExtractor =
        CompositeFunctionExtractor(
            listOf(
                GetterBasedConfiguringFunctionExtractor(isPublicAndNotHidden),
                DefaultFunctionExtractor(configureLambdas, isPublicAndNotHidden)
            )
        ),
    augmentationsProvider: AugmentationsProvider = CompositeAugmentationsProvider(emptyList()),
    typeDiscovery: TypeDiscovery = TypeDiscovery.none
): AnalysisSchema =
    DataSchemaBuilder(typeDiscovery, propertyExtractor, functionExtractor, augmentationsProvider).schemaFromTypes(
        topLevelReceiver, types, externalFunctionDiscovery.discoverTopLevelFunctions(), externalObjects, defaultImports,
    )


val isPublic: MemberFilter = MemberFilter { member: KCallable<*> ->
    member.visibility == KVisibility.PUBLIC
}


val isPublicAndNotHidden: MemberFilter = MemberFilter { member: KCallable<*> ->
    when (member) {
        is KFunction<*> if member.name == "equals" && member.parameters.size == 2 && member.instanceParameter == member.parameters[0] && member.parameters[1].type.classifier == Any::class  -> false
        is KFunction<*> if member.name == "hashCode" && member.parameters.singleOrNull() == member.instanceParameter -> false
        is KFunction<*> if member.name == "toString" && member.parameters.singleOrNull() == member.instanceParameter -> false
        // FIXME: workaround filtering
        is KFunction<*> if member.name == "forEach" && member.parameters.size == 2 && member.instanceParameter != null && member.parameters.last().type.classifier == Consumer::class -> false
        is KFunction<*> if member.name == "removeIf" && member.parameters.size == 2 && member.instanceParameter != null && member.parameters.last().type.classifier == Predicate::class -> false
        is KFunction<*> if member.name == "sort" && member.parameters.size == 2 && member.instanceParameter != null && member.parameters.last().type.classifier == Comparator::class -> false
        else -> member.visibility == KVisibility.PUBLIC &&
            member.annotationsWithGetters.none { it is HiddenInDeclarativeDsl }
    }
}
