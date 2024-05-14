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

    sealed interface ConfigureSemantics : FunctionSemantics {
        val configuredType: DataTypeRef
        val configureBlockRequirement: ConfigureBlockRequirement

        sealed interface ConfigureBlockRequirement {
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

    sealed interface NewObjectFunctionSemantics : FunctionSemantics

    interface Builder : FunctionSemantics

    interface AccessAndConfigure : ConfigureSemantics {
        val accessor: ConfigureAccessor
        val returnType: ReturnType

        sealed interface ReturnType {
            interface Unit : ReturnType
            interface ConfiguredObject : ReturnType
        }

        override val configuredType: DataTypeRef
            get() = if (returnType is ReturnType.ConfiguredObject) returnValueType else accessor.objectType

        override val configureBlockRequirement: ConfigureSemantics.ConfigureBlockRequirement.Required
    }

    interface AddAndConfigure : NewObjectFunctionSemantics, ConfigureSemantics {
        override val configureBlockRequirement: ConfigureSemantics.ConfigureBlockRequirement

        override val configuredType: DataTypeRef
            get() = returnValueType
    }

    interface Pure : NewObjectFunctionSemantics {
        override val returnValueType: DataTypeRef
    }
}
