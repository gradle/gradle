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

package com.h0tk3y.kotlin.staticObjectNotation.schemaBuilder

import com.h0tk3y.kotlin.staticObjectNotation.analysis.DataType
import com.h0tk3y.kotlin.staticObjectNotation.analysis.DataTypeRef
import com.h0tk3y.kotlin.staticObjectNotation.analysis.FqName
import com.h0tk3y.kotlin.staticObjectNotation.analysis.ref
import kotlin.reflect.KClass
import kotlin.reflect.KClassifier

internal fun KClassifier.toDataTypeRef(): DataTypeRef =
    when (this) {
        Unit::class -> DataType.UnitType.ref
        Int::class -> DataType.IntDataType.ref
        String::class -> DataType.StringDataType.ref
        Boolean::class -> DataType.BooleanDataType.ref
        Long::class -> DataType.LongDataType.ref
        is KClass<*> -> DataTypeRef.Name(FqName.parse(java.name))
        else -> error("unexpected type")
    }
