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

import org.gradle.declarative.dsl.schema.ConfigureAccessor.ConfiguringLambdaArgument
import org.gradle.declarative.dsl.schema.ConfigureAccessor.Custom
import org.gradle.declarative.dsl.schema.ConfigureAccessor.Property
import org.gradle.tooling.ToolingModelContract
import java.io.Serializable


@ToolingModelContract(subTypes = [
    Property::class,
    Custom::class,
    ConfiguringLambdaArgument::class
])
sealed interface ConfigureAccessor : Serializable {
    val objectType: DataTypeRef

    interface Property : ConfigureAccessor {
        val dataProperty: DataProperty

        override val objectType: DataTypeRef
            get() = dataProperty.valueType
    }

    interface Custom : ConfigureAccessor {
        val customAccessorIdentifier: String
    }

    interface ConfiguringLambdaArgument : ConfigureAccessor

    // TODO: configure all elements by addition key?
    // TODO: Do we want to support configuring external objects?
}
