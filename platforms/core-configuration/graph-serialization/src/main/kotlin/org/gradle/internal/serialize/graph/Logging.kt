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

import org.gradle.internal.configuration.problems.DocumentationSection
import org.gradle.internal.configuration.problems.DocumentationSection.NotYetImplemented
import org.gradle.internal.configuration.problems.DocumentationSection.RequirementsDisallowedTypes

import org.gradle.internal.configuration.problems.PropertyProblem
import org.gradle.internal.configuration.problems.StructuredMessage.Companion.build
import org.gradle.internal.configuration.problems.StructuredMessageBuilder
import org.gradle.internal.configuration.problems.propertyDescriptionFor

import kotlin.reflect.KClass


fun IsolateContext.logUnsupportedBaseType(
    action: String,
    baseType: KClass<*>,
    actualType: Class<*>,
    documentationSection: DocumentationSection = DocumentationSection.RequirementsDisallowedTypes,
    appendix: StructuredMessageBuilder = {}
) {
    logUnsupported(action, documentationSection, appendix) {
        text(" object of type ")
        reference(actualType)
        text(", a subtype of ")
        reference(baseType)
        text(",")
    }
}


fun IsolateContext.logPropertyProblem(
    action: String,
    documentationSection: DocumentationSection? = null,
    message: StructuredMessageBuilder
) {
    logPropertyProblem(action, PropertyProblem(trace, build(message), documentationSection = documentationSection))
}


fun IsolateContext.logUnsupported(
    action: String,
    baseType: KClass<*>,
    documentationSection: DocumentationSection = RequirementsDisallowedTypes,
    appendix: StructuredMessageBuilder = {}
) {
    logUnsupported(action, documentationSection, appendix) {
        text(" object of type ")
        reference(baseType)
    }
}


fun IsolateContext.logUnsupported(
    action: String,
    documentationSection: DocumentationSection = RequirementsDisallowedTypes,
    appendix: StructuredMessageBuilder = {},
    unsupportedThings: StructuredMessageBuilder
) {
    logPropertyProblem(
        action,
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
    logPropertyProblem {
        text("objects of type ")
        reference(baseType)
        text(" are not yet supported with the configuration cache.")
    }
}


fun IsolateContext.logNotImplemented(feature: String, documentationSection: DocumentationSection = NotYetImplemented) {
    onProblem(
        PropertyProblem(
            trace,
            build {
                text("support for $feature is not yet implemented with the configuration cache.")
            },
            documentationSection = documentationSection
        )
    )
}


fun IsolateContext.logPropertyProblem(documentationSection: DocumentationSection? = null, message: StructuredMessageBuilder) {
    val problem = PropertyProblem(trace, build(message), documentationSection = documentationSection)
    logPropertyProblem("serialize", problem)
}


fun IsolateContext.logPropertyProblem(action: String, problem: PropertyProblem) {
    logger.debug("configuration-cache > failed to {} {} because {}", action, propertyDescriptionFor(problem.trace), problem.message)
    onProblem(problem)
}


inline fun <T : WriteContext, R> T.withDebugFrame(name: () -> String, writeAction: T.() -> R): R {
    val tracer = this.tracer
    return if (tracer == null) {
        writeAction()
    } else {
        val frameName = name()
        try {
            tracer.open(frameName)
            writeAction()
        } finally {
            tracer.close(frameName)
        }
    }
}
