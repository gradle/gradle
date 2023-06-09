/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.kotlin.dsl.accessors

import kotlinx.metadata.KmType
import org.gradle.kotlin.dsl.support.bytecode.newClassTypeOf
import org.gradle.kotlin.dsl.support.bytecode.newTypeParameterTypeOf


internal
object KotlinType {

    val string: KmType = newClassTypeOf("kotlin/String")

    val unit: KmType = newClassTypeOf("kotlin/Unit")

    val any: KmType = newClassTypeOf("kotlin/Any")

    val typeParameter: KmType = newTypeParameterTypeOf(0)
}
