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

package org.gradle.declarative.dsl.schema

import org.gradle.tooling.ToolingModelContract
import java.io.Serializable

@ToolingModelContract(subTypes = [
    CustomAccessorIdentifier.ExtensionAccessorIdentifier::class,
    CustomAccessorIdentifier.ContainerAccessorIdentifier::class,
    CustomAccessorIdentifier.ProjectFeatureIdentifier::class
])
sealed interface CustomAccessorIdentifier : Serializable {
    val type: CustomAccessorType
    val name: String

    interface ExtensionAccessorIdentifier : CustomAccessorIdentifier, Serializable

    interface ContainerAccessorIdentifier : CustomAccessorIdentifier, Serializable {
        val elementTypeClassName: String
    }

    interface ProjectFeatureIdentifier : CustomAccessorIdentifier, Serializable {
        val targetTypeClassName: String
    }

    @ToolingModelContract(subTypes = [
        CustomAccessorType.Extension::class,
        CustomAccessorType.Container::class,
        CustomAccessorType.ProjectFeature::class
    ])
    sealed interface CustomAccessorType : Serializable {
        interface Extension : CustomAccessorType
        interface Container : CustomAccessorType
        interface ProjectFeature : CustomAccessorType
    }
}
