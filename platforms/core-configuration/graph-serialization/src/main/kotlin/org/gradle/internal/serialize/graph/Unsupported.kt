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

package org.gradle.internal.serialize.graph

import org.gradle.api.internal.GeneratedSubclasses.unpackType
import org.gradle.internal.configuration.problems.DocumentationSection
import org.gradle.internal.configuration.problems.StructuredMessageBuilder


inline fun <reified T : Any> unsupported(
    documentationSection: DocumentationSection = DocumentationSection.RequirementsDisallowedTypes
): Codec<T> = codec(
    encode = { value ->
        logUnsupportedBaseType("serialize", T::class, unpackType(value), documentationSection)
    },
    decode = {
        logUnsupported("deserialize", T::class, documentationSection)
        null
    }
)


inline fun <reified T : Any> unsupported(
    description: String,
    documentationSection: DocumentationSection = DocumentationSection.RequirementsDisallowedTypes
) = unsupported<T>(documentationSection) {
    text(description)
}


inline fun <reified T : Any> unsupported(
    documentationSection: DocumentationSection = DocumentationSection.RequirementsDisallowedTypes,
    noinline unsupportedMessage: StructuredMessageBuilder
): Codec<T> = codec(
    encode = {
        logUnsupported("serialize", documentationSection, unsupportedThings = unsupportedMessage)
    },
    decode = {
        logUnsupported("deserialize", documentationSection, unsupportedThings = unsupportedMessage)
        null
    }
)
