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

package org.gradle.internal.serialize.graph

import org.gradle.api.internal.GeneratedSubclasses.unpackType
import org.gradle.internal.configuration.problems.DocumentationSection
import org.gradle.internal.configuration.problems.DocumentationSection.RequirementsDisallowedTypes
import org.gradle.internal.configuration.problems.PropertyKind

import org.gradle.internal.configuration.problems.PropertyProblem
import org.gradle.internal.configuration.problems.StructuredMessage.Companion.build
import org.gradle.internal.configuration.problems.StructuredMessageBuilder
import org.gradle.internal.configuration.problems.propertyDescriptionFor

import kotlin.reflect.KClass


/**
 * The problem name for a value of a type the configuration cache cannot serialize.
 */
@PublishedApi
internal
const val TYPE_UNSUPPORTED_FOR_SERIALIZATION = "Type unsupported for serialization"


fun MutableIsolateContext.reportUnsupportedFieldType(
    unsupportedType: KClass<*>,
    action: String,
    fieldName: String,
    fieldValue: Any? = null
) {
    withPropertyTrace(PropertyKind.Field, fieldName) {
        if (fieldValue == null) logUnsupported(action, unsupportedType)
        else logUnsupportedBaseType(action, unsupportedType, unpackType(fieldValue))
    }
}


fun IsolateContext.logUnsupportedBaseType(
    action: String,
    baseType: KClass<*>,
    actualType: Class<*>,
    documentationSection: DocumentationSection = DocumentationSection.RequirementsDisallowedTypes,
    appendix: StructuredMessageBuilder = {},
    name: String = TYPE_UNSUPPORTED_FOR_SERIALIZATION
) {
    logUnsupported(action, name, documentationSection, appendix) {
        text(" object of type ")
        reference(actualType)
        text(", a subtype of ")
        reference(baseType)
        text(",")
    }
}


fun IsolateContext.logPropertyProblem(
    action: String,
    name: String,
    documentationSection: DocumentationSection? = null,
    message: StructuredMessageBuilder
) {
    logPropertyProblem(action, PropertyProblem(trace, name, build(message), documentationSection = documentationSection))
}


fun IsolateContext.logUnsupported(
    action: String,
    baseType: KClass<*>,
    documentationSection: DocumentationSection = RequirementsDisallowedTypes,
    appendix: StructuredMessageBuilder = {},
    name: String = TYPE_UNSUPPORTED_FOR_SERIALIZATION
) {
    logUnsupported(action, name, documentationSection, appendix) {
        text(" object of type ")
        reference(baseType)
    }
}


fun IsolateContext.logUnsupported(
    action: String,
    name: String,
    documentationSection: DocumentationSection = RequirementsDisallowedTypes,
    appendix: StructuredMessageBuilder = {},
    unsupportedThings: StructuredMessageBuilder
) {
    logPropertyProblem(
        action,
        name,
        documentationSection = documentationSection,
    ) {
        text("cannot ")
        text(action)
        unsupportedThings()
        text(" as these are not supported with the configuration cache.")
        appendix()
    }
}


fun IsolateContext.logNotImplemented(baseType: Class<*>) {
    logPropertyProblem("serialize", "Type not yet supported") {
        text("objects of type ")
        reference(baseType)
        text(" are not yet supported with the configuration cache.")
    }
}


fun IsolateContext.logPropertyProblem(action: String, problem: PropertyProblem) {
    logger.debug("configuration-cache > failed to {} {} because {}", action, propertyDescriptionFor(problem.trace), problem.message)
    onProblem(problem)
}


inline fun <T : WriteContext, R> T.withDebugFrame(name: () -> String, instance: Any? = null, writeAction: T.() -> R): R {
    val tracer = this.tracer
    return if (tracer == null) {
        writeAction()
    } else {
        val frameName = name()
        try {
            tracer.open(frameName, instance)
            writeAction()
        } finally {
            tracer.close(frameName, instance)
        }
    }
}
