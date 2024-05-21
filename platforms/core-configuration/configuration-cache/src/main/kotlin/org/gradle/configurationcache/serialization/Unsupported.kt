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

package org.gradle.configurationcache.serialization

import org.gradle.configurationcache.problems.DocumentationSection
import org.gradle.configurationcache.problems.StructuredMessageBuilder


internal
inline fun <reified T : Any> unsupported(
    documentationSection: DocumentationSection = DocumentationSection.RequirementsDisallowedTypes
): Codec<T> = codec(
    encode = { value ->
        logUnsupportedBaseType("serialize", T::class, value.javaClass, documentationSection)
    },
    decode = {
        logUnsupported("deserialize", T::class, documentationSection)
        null
    }
)


internal
inline fun <reified T : Any> unsupported(
    description: String,
    documentationSection: DocumentationSection = DocumentationSection.RequirementsDisallowedTypes
) = unsupported<T>(documentationSection) {
    text(description)
}


internal
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
