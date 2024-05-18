/*
 * Copyright 2020 the original author or authors.
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

import org.gradle.api.internal.GeneratedSubclasses
import org.gradle.configurationcache.problems.DocumentationSection
import org.gradle.configurationcache.problems.StructuredMessageBuilder
import kotlin.reflect.KClass


internal
fun IsolateContext.logUnsupportedBaseType(
    action: String,
    baseType: KClass<*>,
    actualType: Class<*>,
    documentationSection: DocumentationSection = DocumentationSection.RequirementsDisallowedTypes,
    appendix: StructuredMessageBuilder = {}
) {
    logUnsupported(action, documentationSection, appendix) {
        text(" object of type ")
        reference(GeneratedSubclasses.unpack(actualType))
        text(", a subtype of ")
        reference(baseType)
        text(",")
    }
}
