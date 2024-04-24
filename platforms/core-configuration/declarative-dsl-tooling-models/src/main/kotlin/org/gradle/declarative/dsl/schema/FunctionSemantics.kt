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

package org.gradle.declarative.dsl.schema

import java.io.Serializable


sealed interface FunctionSemantics : Serializable {

    val returnValueType: DataTypeRef

    fun isNewObjectFunctionSemantics() = false

    fun isConfigureSemantics() = false

    fun getConfiguredType(): DataTypeRef = error("Not configure semantics")

    fun getConfigureBlockRequirement(): ConfigureSemantics.ConfigureBlockRequirement =
        error("Not configure semantics")

    sealed interface ConfigureSemantics : FunctionSemantics {
        override fun isConfigureSemantics(): Boolean = true

        sealed interface ConfigureBlockRequirement : Serializable {
            interface NotAllowed : ConfigureBlockRequirement
            interface Optional : ConfigureBlockRequirement
            interface Required : ConfigureBlockRequirement

            val allows: Boolean
                get() = this !is NotAllowed

            val requires: Boolean
                get() = this is Required

            fun isValidIfLambdaIsPresent(isPresent: Boolean): Boolean = when {
                isPresent -> allows
                else -> !requires
            }
        }
    }

    sealed interface NewObjectFunctionSemantics : FunctionSemantics {
        override fun isNewObjectFunctionSemantics(): Boolean = true
    }

    interface Builder : FunctionSemantics

    interface AccessAndConfigure : ConfigureSemantics {
        val accessor: ConfigureAccessor
        val returnType: ReturnType

        sealed interface ReturnType : Serializable {
            interface Unit : ReturnType
            interface ConfiguredObject : ReturnType
        }
    }

    interface AddAndConfigure : NewObjectFunctionSemantics, ConfigureSemantics

    interface Pure : NewObjectFunctionSemantics {
        override val returnValueType: DataTypeRef
    }
}
