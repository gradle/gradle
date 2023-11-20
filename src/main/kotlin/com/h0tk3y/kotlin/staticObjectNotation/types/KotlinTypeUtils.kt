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

package com.h0tk3y.kotlin.staticObjectNotation.types

import kotlin.reflect.KParameter
import kotlin.reflect.KType
import kotlin.reflect.KTypeProjection
import kotlin.reflect.KVariance
import kotlin.reflect.full.createType
import kotlin.reflect.full.isSubtypeOf

internal fun isConfigureLambda(kParam: KParameter): Boolean {
    val paramType = kParam.type
    return paramType.isSubtypeOf(configureLambdaTypeFor(Nothing::class.createType()))
}

internal fun isConfigureLambda(kParam: KParameter, returnTypeClassifier: KType): Boolean {
    val paramType = kParam.type
    return paramType.isSubtypeOf(configureLambdaTypeFor(returnTypeClassifier))
}

private fun configureLambdaTypeFor(configuredType: KType) =
    Function1::class.createType(
        listOf(
            KTypeProjection(KVariance.INVARIANT, configuredType),
            KTypeProjection(KVariance.INVARIANT, Unit::class.createType())
        )
    )
