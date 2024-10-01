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

package org.gradle.internal.declarativedsl.dom

import org.gradle.declarative.dsl.schema.DataClass
import org.gradle.declarative.dsl.schema.DataProperty
import org.gradle.declarative.dsl.schema.DataType
import org.gradle.declarative.dsl.schema.SchemaFunction
import org.gradle.declarative.dsl.schema.SchemaMemberFunction


sealed interface DocumentResolution {
    sealed interface SuccessfulResolution : DocumentResolution
    sealed interface UnsuccessfulResolution : DocumentResolution {
        val reasons: Iterable<ResolutionFailureReason>
    }

    sealed interface DocumentNodeResolution : DocumentResolution

    sealed interface PropertyResolution : DocumentNodeResolution {
        data class PropertyAssignmentResolved(val receiverType: DataType, val property: DataProperty) : PropertyResolution, SuccessfulResolution
        data class PropertyNotAssigned(override val reasons: List<PropertyNotAssignedReason>) : PropertyResolution, UnsuccessfulResolution
    }

    sealed interface ElementResolution : DocumentNodeResolution {
        sealed interface SuccessfulElementResolution : ElementResolution, SuccessfulResolution {
            val elementType: DataType
            val elementFactoryFunction: SchemaMemberFunction

            data class ConfiguringElementResolved(
                override val elementType: DataClass,
                override val elementFactoryFunction: SchemaMemberFunction,
            ) : SuccessfulElementResolution

            data class ContainerElementResolved(
                override val elementType: DataType,
                override val elementFactoryFunction: SchemaMemberFunction,
            ) : SuccessfulElementResolution
        }

        data class ElementNotResolved(override val reasons: List<ElementNotResolvedReason>) : ElementResolution, UnsuccessfulResolution
    }

    data object ErrorResolution : DocumentNodeResolution, UnsuccessfulResolution {
        override val reasons: Iterable<ResolutionFailureReason>
            get() = listOf(IsError)
    }

    sealed interface ValueNodeResolution : DocumentResolution {
        data class LiteralValueResolved(val value: Any) : ValueNodeResolution, SuccessfulResolution

        sealed interface NamedReferenceResolution : ValueNodeResolution {
            data class NamedReferenceResolved(val referenceName: String) : NamedReferenceResolution, SuccessfulResolution
            data class NamedReferenceNotResolved(override val reasons: List<NamedReferenceNotResolvedReason>) : NamedReferenceResolution, UnsuccessfulResolution
        }

        sealed interface ValueFactoryResolution : ValueNodeResolution {
            data class ValueFactoryResolved(val function: SchemaFunction) : ValueFactoryResolution, SuccessfulResolution
            data class ValueFactoryNotResolved(override val reasons: List<ValueFactoryNotResolvedReason>) : ValueFactoryResolution, UnsuccessfulResolution
        }
    }
}
